#!/usr/bin/env python3
"""
PhoneCare-AI Diagnostic ML Prediction Interface
Loads trained diagnostic model artifacts (scikit-learn with pure-Python fallback),
evaluates device symptoms and context, calculates calibrated confidence,
and flags uncertain or out-of-distribution cases.
"""

import argparse
import json
import math
import os
import re
import sys
from typing import Dict, List, Any, Optional

# Ensure project root is on sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Attempt scikit-learn & joblib
try:
    import joblib
    HAS_SKLEARN = True
except ImportError:
    HAS_SKLEARN = False


def tokenize(text: str) -> List[str]:
    """Tokenize lowercase words."""
    if not text:
        return []
    return re.findall(r"\b[a-zA-Z0-9_\-]+\b", text.lower())


class DiagnosticPredictor:
    """
    Diagnostic classification predictor for Android troubleshooting.
    Supports scikit-learn model loading and pure-Python portable fallback.
    """

    DEFAULT_MODELS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")

    def __init__(self, models_dir: Optional[str] = None, force_portable: bool = False):
        self.models_dir = models_dir or self.DEFAULT_MODELS_DIR
        self.force_portable = force_portable
        self.sklearn_pipeline = None
        self.portable_model = None
        self.model_type = None
        self.classes = []
        self.vocabulary = set()
        self._load_model()

    def _load_model(self) -> None:
        """Loads scikit-learn joblib model if available, else portable JSON weights."""
        joblib_path = os.path.join(self.models_dir, "diagnostic_model.joblib")
        portable_path = os.path.join(self.models_dir, "portable_model.json")

        if not self.force_portable and HAS_SKLEARN and os.path.exists(joblib_path):
            try:
                self.sklearn_pipeline = joblib.load(joblib_path)
                self.model_type = "scikit-learn (TfidfVectorizer + LogisticRegression)"
                self.classes = list(self.sklearn_pipeline.classes_)
                # Extract vocabulary from tfidf vectorizer
                tfidf = self.sklearn_pipeline.named_steps.get("tfidf")
                if tfidf and hasattr(tfidf, "vocabulary_"):
                    self.vocabulary = set(tfidf.vocabulary_.keys())
                return
            except Exception as e:
                # Log and fallback to portable model
                pass

        if os.path.exists(portable_path):
            with open(portable_path, "r", encoding="utf-8") as f:
                self.portable_model = json.load(f)
            self.model_type = "Pure-Python (TF-IDF Naive Bayes portable fallback)"
            self.classes = self.portable_model.get("classes", [])
            self.vocabulary = set(self.portable_model.get("vocabulary", []))
            return

        raise FileNotFoundError(
            f"No trained model artifact found in {self.models_dir}. "
            "Please run 'python3 ml/train_model.py' to generate model artifacts."
        )

    def prepare_input_text(self, symptom: str, brand: str = "", model: str = "",
                            android_version: str = "", diagnostic_observations: str = "") -> str:
        """Combines device context with symptom text matching training format."""
        parts = []
        if brand or model:
            parts.append(f"{brand} {model}".strip())
        if android_version:
            parts.append(f"Android {android_version}")
        if diagnostic_observations:
            parts.append(f"observation: {diagnostic_observations}")
        parts.append(symptom.strip())
        return " ".join(p for p in parts if p)

    def _predict_with_sklearn(self, feature_text: str) -> List[Dict[str, Any]]:
        """Computes probabilities using scikit-learn pipeline."""
        probas = self.sklearn_pipeline.predict_proba([feature_text])[0]
        results = [
            {"category": cls, "score": round(float(p), 4)}
            for cls, p in zip(self.classes, probas)
        ]
        results.sort(key=lambda x: x["score"], reverse=True)
        return results

    def _predict_with_portable(self, feature_text: str) -> List[Dict[str, Any]]:
        """Computes posterior scores using pure-Python portable weights."""
        tokens = tokenize(feature_text)
        classes = self.portable_model["classes"]
        class_priors = self.portable_model["class_priors"]
        word_counts = self.portable_model["word_counts"]
        total_words = self.portable_model["total_words"]
        idf = self.portable_model["idf"]
        alpha = self.portable_model.get("alpha", 1.0)
        vocab_size = len(self.portable_model["vocabulary"])

        scores = {}
        default_prior = -math.log(len(classes)) if len(classes) > 0 else 0.0
        for c in classes:
            log_prob = class_priors.get(c, default_prior)
            for tok in tokens:
                idf_weight = idf.get(tok, 1.0)
                word_freq = word_counts.get(c, {}).get(tok, 0)
                prob_w = (word_freq + alpha) / (total_words.get(c, 0) + alpha * vocab_size)
                log_prob += math.log(prob_w) * idf_weight
            scores[c] = log_prob

        # Softmax normalization for interpretable probabilities
        max_score = max(scores.values())
        exp_scores = {c: math.exp(s - max_score) for c, s in scores.items()}
        sum_exp = sum(exp_scores.values())
        probas = [
            {"category": c, "score": round(exp_scores[c] / sum_exp, 4)}
            for c in classes
        ]
        probas.sort(key=lambda x: x["score"], reverse=True)
        return probas

    def predict(self, symptom: str, brand: str = "", model: str = "",
                android_version: str = "", diagnostic_observations: str = "") -> Dict[str, Any]:
        """
        Accepts symptom description and device context.
        Returns predicted category, calibrated confidence level, and uncertainty indicator.
        """
        # 1. Input Validation
        if symptom is None or not isinstance(symptom, str) or not symptom.strip():
            raise ValueError("Symptom description cannot be empty or whitespace.")

        cleaned_symptom = symptom.strip()
        tokens = tokenize(cleaned_symptom)
        if len(tokens) == 0:
            raise ValueError("Symptom description contains no recognizable words.")

        feature_text = self.prepare_input_text(
            cleaned_symptom, brand, model, android_version, diagnostic_observations
        )

        # 2. Vocabulary Overlap Audit (Detect out-of-distribution or unfamiliar input)
        recognized_tokens = [t for t in tokens if t in self.vocabulary]
        vocab_overlap_ratio = len(recognized_tokens) / len(tokens) if tokens else 0.0

        # 3. Model Inference
        if self.sklearn_pipeline is not None:
            candidates = self._predict_with_sklearn(feature_text)
        else:
            candidates = self._predict_with_portable(feature_text)

        top_candidate = candidates[0]
        second_candidate = candidates[1] if len(candidates) > 1 else {"category": None, "score": 0.0}

        top_score = top_candidate["score"]
        second_score = second_candidate["score"]
        margin = top_score - second_score

        # 4. Uncertainty & Calibration Assessment
        # Across 8 classes (where random chance is 0.125), calibrate confidence levels:
        is_uncertain = False
        uncertainty_reason = None
        confidence_level = "LOW"

        if len(recognized_tokens) < 1 or vocab_overlap_ratio < 0.15:
            is_uncertain = True
            confidence_level = "LOW"
            uncertainty_reason = "Input symptom contains mostly unfamiliar or out-of-vocabulary terms."
        elif top_score < 0.18:
            is_uncertain = True
            confidence_level = "LOW"
            uncertainty_reason = "Model predicted probability is below diagnostic confidence threshold (p < 0.18)."
        elif margin < 0.04:
            is_uncertain = True
            confidence_level = "LOW"
            uncertainty_reason = f"Narrow margin ({margin:.3f}) between '{top_candidate['category']}' and '{second_candidate['category']}'."
        elif top_score >= 0.30 and margin >= 0.08:
            confidence_level = "HIGH"
            is_uncertain = False
        else:
            confidence_level = "MEDIUM"
            is_uncertain = False

        advice = self._generate_diagnostic_advice(top_candidate["category"], is_uncertain)

        return {
            "status": "SUCCESS",
            "predicted_category": top_candidate["category"],
            "confidence_score": round(top_score, 4),
            "confidence_level": confidence_level,
            "is_uncertain": is_uncertain,
            "uncertainty_reason": uncertainty_reason,
            "model_type": self.model_type,
            "vocabulary_overlap_ratio": round(vocab_overlap_ratio, 3),
            "top_candidates": candidates[:3],
            "diagnostic_advice": advice,
        }

    def _generate_diagnostic_advice(self, category: str, is_uncertain: bool) -> str:
        """Generates contextual diagnostic advice or prompts for follow-up questions."""
        if is_uncertain:
            return (
                "Diagnostic confidence is low or symptom is ambiguous. "
                "Recommend asking targeted follow-up questions regarding specific operating conditions."
            )
        advice_map = {
            "Battery draining": "Inspect background app battery consumption and system wakelocks under Settings > Battery.",
            "Phone overheating": "Inspect active high-load background tasks, thermal throttling status, and avoid heavy use while charging.",
            "Wi-Fi problems": "Verify DHCP/DNS connectivity, reset network settings, or toggle airplane mode to re-initialize wireless interface.",
            "Mobile network problems": "Check SIM status, toggle airplane mode to refresh carrier tower registration, and verify APN settings.",
            "Charging problems": "Inspect USB-C port for lint/debris, verify cable resistance, and check charger output wattage.",
            "Camera problems": "Clear camera app storage cache, check third-party app permissions, and verify hardware sensor lock.",
            "Storage problems": "Review app cache accumulation, clean download cache, and inspect external SD or media files.",
            "Phone lagging": "Check available RAM, reboot device to clear memory leaks, and verify background process limits.",
        }
        return advice_map.get(category, "Review system settings and follow grounded diagnostic steps.")


def main():
    parser = argparse.ArgumentParser(description="PhoneCare-AI ML Diagnostic Prediction Interface")
    parser.add_argument("--symptom", required=True, help="User-reported symptom description")
    parser.add_argument("--brand", default="", help="Device brand (e.g. Samsung, Google)")
    parser.add_argument("--model", default="", help="Device model (e.g. Galaxy S24, Pixel 8)")
    parser.add_argument("--android-version", default="", help="Android version (e.g. 14)")
    parser.add_argument("--observations", default="", help="Diagnostic observations")
    parser.add_argument("--models-dir", default=None, help="Directory containing model artifacts")
    parser.add_argument("--portable", action="store_true", help="Force pure-Python portable model")
    parser.add_argument("--json", action="store_true", help="Output pure JSON")

    args = parser.parse_args()

    try:
        predictor = DiagnosticPredictor(models_dir=args.models_dir, force_portable=args.portable)
        result = predictor.predict(
            symptom=args.symptom,
            brand=args.brand,
            model=args.model,
            android_version=args.android_version,
            diagnostic_observations=args.observations,
        )
    except Exception as e:
        if args.json:
            print(json.dumps({
                "status": "ERROR",
                "message": str(e),
                "is_uncertain": True,
                "confidence_level": "LOW",
                "predicted_category": None
            }, indent=2))
            sys.exit(1)
        else:
            print(f"Prediction Error: {e}", file=sys.stderr)
            sys.exit(1)

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print("\n" + "=" * 60)
        print("  PhoneCare-AI Diagnostic ML Prediction")
        print("=" * 60)
        print(f"Predicted Fault Category : {result['predicted_category']}")
        print(f"Confidence Level         : {result['confidence_level']} (Score: {result['confidence_score']:.4f})")
        print(f"Uncertainty Flag         : {'YES - Flagged as Uncertain' if result['is_uncertain'] else 'NO - Confident'}")
        if result["is_uncertain"]:
            print(f"Uncertainty Reason       : {result['uncertainty_reason']}")
        print(f"Model Type               : {result['model_type']}")
        print(f"Vocab Overlap Ratio      : {result['vocabulary_overlap_ratio'] * 100:.1f}%")
        print("\nTop Candidate Categories:")
        for idx, cand in enumerate(result["top_candidates"], 1):
            print(f"  {idx}. {cand['category']:<26} Score: {cand['score']:.4f}")
        print(f"\nDiagnostic Advice:\n  {result['diagnostic_advice']}")
        print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
