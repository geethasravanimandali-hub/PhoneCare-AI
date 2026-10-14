#!/usr/bin/env python3
"""
PhoneCare-AI Diagnostic Model Training Pipeline
Trains a scikit-learn text classification pipeline from random initialization,
isolates synthetic data from primary evaluation, benchmarks against baselines,
and serializes the trained model artifacts.
"""

import argparse
import json
import math
import os
import random
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from typing import Dict, List, Any, Tuple

# Try scikit-learn & joblib
try:
    import joblib
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
    from sklearn.pipeline import Pipeline
    HAS_SKLEARN = True
except ImportError:
    HAS_SKLEARN = False
# Ensure project root is on sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from ml.evaluate_pipeline import (
        PurePythonTFIDFNaiveBayes,
        check_data_leakage,
        prepare_feature_text,
        stratified_split,
    )
except ImportError:
    from evaluate_pipeline import (
        PurePythonTFIDFNaiveBayes,
        check_data_leakage,
        prepare_feature_text,
        stratified_split,
    )

RANDOM_SEED = 42


def train_and_evaluate(dataset_path: str = "data/dataset/troubleshooting_benchmark.json",
                       output_dir: str = "ml/models",
                       seed: int = RANDOM_SEED) -> Dict[str, Any]:
    """
    Trains diagnostic models from random initialization, evaluates strictly on
    empirical real-world test data, and saves model artifacts.
    """
    if not os.path.exists(dataset_path):
        raise FileNotFoundError(f"Dataset not found at {dataset_path}")

    with open(dataset_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    all_records = data.get("records", [])

    # 1. Strict Provenance Separation
    # Empirical records = controlled experiments + verified documentation
    empirical_records = [
        r for r in all_records
        if r.get("data_provenance") in ("CONTROLLED_EXPERIMENT", "VERIFIED_DOCUMENTATION")
    ]
    synthetic_records = [
        r for r in all_records
        if r.get("data_provenance") == "SYNTHETIC_VARIATION"
    ]

    print("=" * 70)
    print("  PhoneCare-AI Diagnostic Model Training & Evaluation")
    print("=" * 70)
    print(f"Total Dataset Records:          {len(all_records)}")
    print(f"Empirical Records (Physical/Doc): {len(empirical_records)} (Used for primary split)")
    print(f"Synthetic Records (Paraphrases):  {len(synthetic_records)} (Evaluated separately)")

    # 2. Stratified Train/Test Split on Empirical Records (Primary Evaluation Set)
    # 32 train (4 per category), 16 test (2 per category)
    emp_train, emp_test = stratified_split(empirical_records, test_ratio=0.333, seed=seed)

    # 3. Data Leakage Verification
    leaks = check_data_leakage(emp_train, emp_test, threshold=0.85)
    if leaks:
        print(f"WARNING: Data leakage detected between train and test: {len(leaks)} pairs")
    else:
        print("Data Leakage Audit:             CLEAN (0 leakage pairs between train & test)")

    X_train = [prepare_feature_text(r) for r in emp_train]
    y_train = [r["target_fault_category"] for r in emp_train]

    X_test = [prepare_feature_text(r) for r in emp_test]
    y_test = [r["target_fault_category"] for r in emp_test]

    categories = sorted(list(set(y_train + y_test)))

    results: Dict[str, Any] = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "random_seed": seed,
        "empirical_train_count": len(emp_train),
        "empirical_test_count": len(emp_test),
        "synthetic_test_count": len(synthetic_records),
        "categories": categories,
        "baselines": {},
        "trained_model": {},
    }

    # 4. Baseline 1: Majority Class Baseline
    majority_class = Counter(y_train).most_common(1)[0][0]
    maj_preds = [majority_class] * len(y_test)
    maj_acc = sum(1 for p, y in zip(maj_preds, y_test) if p == y) / len(y_test)
    results["baselines"]["Majority_Class_Baseline"] = {
        "accuracy": round(maj_acc, 4),
        "predicted_class": majority_class,
        "description": "Always predicts the most frequent category in training set"
    }

    # 5. Baseline 2: Pure-Python TF-IDF Naive Bayes
    py_nb = PurePythonTFIDFNaiveBayes(alpha=1.0)
    py_nb.fit(X_train, y_train)
    py_nb_preds = py_nb.predict(X_test)
    py_nb_acc = sum(1 for p, y in zip(py_nb_preds, y_test) if p == y) / len(y_test)
    results["baselines"]["PurePython_TFIDF_NaiveBayes"] = {
        "accuracy": round(py_nb_acc, 4),
        "description": "Deterministic TF-IDF Naive Bayes classifier without external dependencies"
    }

    # 6. Train Scikit-Learn Pipeline from Random Initialization
    sk_pipeline = None
    if HAS_SKLEARN:
        sk_pipeline = Pipeline([
            ("tfidf", TfidfVectorizer(
                ngram_range=(1, 2),
                sublinear_tf=True,
                max_df=0.95,
                min_df=1
            )),
            ("clf", LogisticRegression(
                C=1.2,
                max_iter=300,
                solver="lbfgs",
                random_state=seed
            ))
        ])

        # Fit strictly on empirical training data
        sk_pipeline.fit(X_train, y_train)

        # Evaluate on Empirical Test Set (Primary Evaluation)
        sk_preds = sk_pipeline.predict(X_test)
        sk_acc = float(accuracy_score(y_test, sk_preds))
        sk_macro_f1 = float(f1_score(y_test, sk_preds, average="macro", zero_division=0))
        conf_mat = confusion_matrix(y_test, sk_preds, labels=categories).tolist()
        class_report = classification_report(y_test, sk_preds, labels=categories, output_dict=True, zero_division=0)

        # Evaluate separately on Synthetic Variations (Secondary Evaluation)
        X_synth = [prepare_feature_text(r) for r in synthetic_records]
        y_synth = [r["target_fault_category"] for r in synthetic_records]
        synth_preds = sk_pipeline.predict(X_synth)
        synth_acc = float(accuracy_score(y_synth, synth_preds))

        results["trained_model"] = {
            "model_type": "TfidfVectorizer + LogisticRegression",
            "empirical_test_accuracy": round(sk_acc, 4),
            "empirical_macro_f1": round(sk_macro_f1, 4),
            "synthetic_test_accuracy": round(synth_acc, 4),
            "confusion_matrix": conf_mat,
            "classification_report": class_report,
            "hyperparameters": {
                "ngram_range": [1, 2],
                "sublinear_tf": True,
                "C": 1.2,
                "max_iter": 300,
                "solver": "lbfgs"
            }
        }

    # 7. Save Model Artifacts
    os.makedirs(output_dir, exist_ok=True)

    metadata_path = os.path.join(output_dir, "model_metadata.json")
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)

    # Train deployable pipeline on all 48 empirical records (excluding synthetic)
    X_emp_all = [prepare_feature_text(r) for r in empirical_records]
    y_emp_all = [r["target_fault_category"] for r in empirical_records]

    # Save Scikit-Learn Model Artifact via Joblib
    if HAS_SKLEARN:
        prod_pipeline = Pipeline([
            ("tfidf", TfidfVectorizer(
                ngram_range=(1, 2),
                sublinear_tf=True,
                max_df=0.95,
                min_df=1
            )),
            ("clf", LogisticRegression(
                C=1.2,
                max_iter=300,
                solver="lbfgs",
                random_state=seed
            ))
        ])
        prod_pipeline.fit(X_emp_all, y_emp_all)
        model_path = os.path.join(output_dir, "diagnostic_model.joblib")
        joblib.dump(prod_pipeline, model_path)
        print(f"\n[Artifact Saved] Scikit-Learn model (fit on all 48 empirical cases) saved to: {model_path}")

    # Fit and save Portable Model on all empirical records
    prod_py_nb = PurePythonTFIDFNaiveBayes(alpha=1.0)
    prod_py_nb.fit(X_emp_all, y_emp_all)
    portable_weights = {
        "classes": prod_py_nb.classes,
        "class_priors": prod_py_nb.class_priors,
        "word_counts": {c: dict(words) for c, words in prod_py_nb.word_counts.items()},
        "total_words": prod_py_nb.total_words_per_class,
        "vocabulary": sorted(list(prod_py_nb.vocabulary)),
        "idf": prod_py_nb.idf,
        "alpha": prod_py_nb.alpha
    }
    portable_path = os.path.join(output_dir, "portable_model.json")
    with open(portable_path, "w", encoding="utf-8") as f:
        json.dump(portable_weights, f, indent=2)
    print(f"[Artifact Saved] Portable Python model saved to: {portable_path}")
    print(f"[Artifact Saved] Metadata and evaluation report: {metadata_path}")

    # 8. Print Results Summary
    print("\n" + "-" * 70)
    print("  Empirical Test Evaluation Summary (100% Real-World & Lab Cases)")
    print("-" * 70)
    print(f"  • Majority Class Baseline : Accuracy = {maj_acc * 100:5.1f}%")
    print(f"  • Pure-Python Naive Bayes : Accuracy = {py_nb_acc * 100:5.1f}%")
    if HAS_SKLEARN and "trained_model" in results:
        tm = results["trained_model"]
        print(f"  • Trained Scikit-Learn ML : Accuracy = {tm['empirical_test_accuracy'] * 100:5.1f}% | Macro F1 = {tm['empirical_macro_f1']:.4f}")
        print(f"  • Synthetic Robustness    : Accuracy = {tm['synthetic_test_accuracy'] * 100:5.1f}% (Evaluated separately)")

        print("\n  Per-Class Metrics on Empirical Test Data:")
        print(f"  {'Category':<24} {'Precision':<10} {'Recall':<10} {'F1-Score':<10} {'Support'}")
        print("  " + "-" * 62)
        cr = tm["classification_report"]
        for cat in categories:
            if cat in cr:
                m = cr[cat]
                print(f"  {cat:<24} {m['precision']:<10.2f} {m['recall']:<10.2f} {m['f1-score']:<10.2f} {m['support']}")

    print("=" * 70)
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train PhoneCare-AI diagnostic ML model.")
    parser.add_argument("--dataset", default="data/dataset/troubleshooting_benchmark.json", help="Path to benchmark JSON")
    parser.add_argument("--output-dir", default="ml/models", help="Directory to save model artifacts")
    parser.add_argument("--seed", type=int, default=RANDOM_SEED, help="Random initialization seed")

    args = parser.parse_args()
    train_and_evaluate(dataset_path=args.dataset, output_dir=args.output_dir, seed=args.seed)
