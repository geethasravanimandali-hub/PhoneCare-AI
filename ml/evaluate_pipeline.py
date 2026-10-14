#!/usr/bin/env python3
"""
PhoneCare-AI Diagnostic ML Evaluation Pipeline
Prepares data splits, checks for leakage, and benchmarks baseline vs diagnostic ML classifiers.
"""

import argparse
import json
import math
import os
import random
import re
import sys
from collections import Counter, defaultdict
from typing import Dict, List, Any, Tuple, Optional

# Attempt scikit-learn imports; graceful fallback to built-in implementations
try:
    import numpy as np
    from sklearn.dummy import DummyClassifier
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
    from sklearn.model_selection import StratifiedShuffleSplit
    from sklearn.pipeline import Pipeline
    HAS_SKLEARN = True
except ImportError:
    HAS_SKLEARN = False

RANDOM_SEED = 42


def tokenize(text: str) -> List[str]:
    """Simple lowercase tokenization for n-gram and similarity checks."""
    if not text:
        return []
    return re.findall(r"\b[a-zA-Z0-9_\-]+\b", text.lower())


def jaccard_similarity(text_a: str, text_b: str) -> float:
    """Calculates Jaccard token overlap similarity between two text strings."""
    tokens_a = set(tokenize(text_a))
    tokens_b = set(tokenize(text_b))
    if not tokens_a or not tokens_b:
        return 0.0
    intersection = len(tokens_a.intersection(tokens_b))
    union = len(tokens_a.union(tokens_b))
    return intersection / union if union > 0 else 0.0


def check_data_leakage(train_records: List[Dict[str, Any]], test_records: List[Dict[str, Any]], threshold: float = 0.85) -> List[Tuple[str, str, float]]:
    """
    Checks for near-duplicate symptom descriptions between train and test splits
    to prevent synthetic or verbatim data leakage.
    """
    leaks = []
    for train_item in train_records:
        train_text = train_item.get("symptom_description", "")
        for test_item in test_records:
            test_text = test_item.get("symptom_description", "")
            sim = jaccard_similarity(train_text, test_text)
            if sim >= threshold:
                leaks.append((train_item.get("case_id", "train"), test_item.get("case_id", "test"), sim))
    return leaks


def prepare_feature_text(record: Dict[str, Any]) -> str:
    """Combines brand, model, android_version, usage_condition, and symptom description."""
    brand = record.get("brand", "")
    model = record.get("model", "")
    android = record.get("android_version", "")
    condition = record.get("usage_condition", "").replace("_", " ")
    symptom = record.get("symptom_description", "")
    return f"{brand} {model} Android {android} condition {condition}. {symptom}"


def stratified_split(records: List[Dict[str, Any]], test_ratio: float = 0.25, seed: int = RANDOM_SEED) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """
    Performs stratified split across target_fault_category without external library dependencies.
    """
    grouped: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for r in records:
        cat = r.get("target_fault_category", "Unknown")
        grouped[cat].append(r)

    rng = random.Random(seed)
    train_set: List[Dict[str, Any]] = []
    test_set: List[Dict[str, Any]] = []

    for cat, items in grouped.items():
        shuffled = list(items)
        rng.shuffle(shuffled)
        test_count = max(1, int(round(len(shuffled) * test_ratio)))
        test_set.extend(shuffled[:test_count])
        train_set.extend(shuffled[test_count:])

    return train_set, test_set


class PurePythonTFIDFNaiveBayes:
    """
    Lightweight, self-contained Multinomial Naive Bayes classifier with TF-IDF weights.
    Provides a reproducible baseline that runs in any environment without external binary dependencies.
    """

    def __init__(self, alpha: float = 1.0):
        self.alpha = alpha
        self.classes: List[str] = []
        self.class_priors: Dict[str, float] = {}
        self.word_counts: Dict[str, Dict[str, float]] = {}
        self.total_words_per_class: Dict[str, float] = {}
        self.vocabulary: set = set()
        self.idf: Dict[str, float] = {}

    def fit(self, X: List[str], y: List[str]) -> "PurePythonTFIDFNaiveBayes":
        self.classes = sorted(list(set(y)))
        n_docs = len(X)
        class_doc_counts = Counter(y)

        # 1. Compute IDF
        doc_freq = Counter()
        for doc in X:
            unique_tokens = set(tokenize(doc))
            for t in unique_tokens:
                doc_freq[t] += 1
                self.vocabulary.add(t)

        for token, df in doc_freq.items():
            self.idf[token] = math.log((n_docs + 1) / (df + 1)) + 1.0

        # 2. Compute Class Priors and TF-IDF weighted counts
        for c in self.classes:
            self.class_priors[c] = math.log((class_doc_counts[c] + 1) / (n_docs + len(self.classes)))
            self.word_counts[c] = defaultdict(float)
            self.total_words_per_class[c] = 0.0

        for doc, label in zip(X, y):
            tokens = tokenize(doc)
            term_freq = Counter(tokens)
            for token, count in term_freq.items():
                tfidf = (count / len(tokens)) * self.idf.get(token, 1.0)
                self.word_counts[label][token] += tfidf
                self.total_words_per_class[label] += tfidf

        return self

    def predict_log_proba(self, text: str) -> Dict[str, float]:
        tokens = tokenize(text)
        term_freq = Counter(tokens)
        vocab_size = len(self.vocabulary)
        scores = {}

        for c in self.classes:
            score = self.class_priors[c]
            denom = self.total_words_per_class[c] + self.alpha * vocab_size
            for token, count in term_freq.items():
                if token in self.vocabulary:
                    tfidf = (count / len(tokens)) * self.idf.get(token, 1.0)
                    prob = (self.word_counts[c][token] + self.alpha) / denom
                    score += tfidf * math.log(prob)
            scores[c] = score

        return scores

    def predict(self, X: List[str]) -> List[str]:
        preds = []
        for doc in X:
            scores = self.predict_log_proba(doc)
            best_class = max(scores.items(), key=lambda x: x[1])[0]
            preds.append(best_class)
        return preds


class DiagnosticEvaluationPipeline:
    """Orchestrates dataset loading, train/test splitting, baseline benchmarking, and model evaluation."""

    def __init__(self, dataset_path: str):
        self.dataset_path = dataset_path
        self.records: List[Dict[str, Any]] = []

    def load_dataset(self, provenance_filter: Optional[str] = None) -> List[Dict[str, Any]]:
        with open(self.dataset_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        records = data.get("records", [])

        if provenance_filter:
            records = [r for r in records if r.get("data_provenance") == provenance_filter]

        self.records = records
        return self.records

    def run_evaluation(self, test_ratio: float = 0.25, seed: int = RANDOM_SEED) -> Dict[str, Any]:
        """Runs baseline comparison and outputs detailed metrics."""
        if not self.records:
            self.load_dataset()

        # 1. Stratified split
        train_records, test_records = stratified_split(self.records, test_ratio=test_ratio, seed=seed)

        # 2. Data leakage verification
        leakage = check_data_leakage(train_records, test_records, threshold=0.85)

        X_train = [prepare_feature_text(r) for r in train_records]
        y_train = [r["target_fault_category"] for r in train_records]
        X_test = [prepare_feature_text(r) for r in test_records]
        y_test = [r["target_fault_category"] for r in test_records]

        results: Dict[str, Any] = {
            "dataset_size": len(self.records),
            "train_size": len(train_records),
            "test_size": len(test_records),
            "data_leakage_detected": len(leakage) > 0,
            "leakage_pairs": leakage,
            "models": {}
        }

        # 3. Simple Baseline: Majority Class Classifier
        majority_class = Counter(y_train).most_common(1)[0][0]
        majority_preds = [majority_class] * len(y_test)
        majority_acc = sum(1 for p, y in zip(majority_preds, y_test) if p == y) / len(y_test)

        results["models"]["Majority_Class_Baseline"] = {
            "accuracy": round(majority_acc, 4),
            "predicted_class": majority_class,
            "description": "Always predicts the most frequent category in training set"
        }

        # 4. Standard Library Baseline: TF-IDF Naive Bayes
        py_nb = PurePythonTFIDFNaiveBayes()
        py_nb.fit(X_train, y_train)
        py_nb_preds = py_nb.predict(X_test)
        py_nb_acc = sum(1 for p, y in zip(py_nb_preds, y_test) if p == y) / len(y_test)

        results["models"]["PurePython_TFIDF_NaiveBayes"] = {
            "accuracy": round(py_nb_acc, 4),
            "description": "Standard-library Multinomial Naive Bayes with TF-IDF term weighting"
        }

        # 5. Scikit-learn Classifier (when installed)
        if HAS_SKLEARN:
            pipeline = Pipeline([
                ("tfidf", TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True)),
                ("clf", LogisticRegression(C=1.0, max_iter=200, random_state=seed))
            ])
            pipeline.fit(X_train, y_train)
            sk_preds = pipeline.predict(X_test)

            sk_acc = float(accuracy_score(y_test, sk_preds))
            sk_macro_f1 = float(f1_score(y_test, sk_preds, average="macro", zero_division=0))
            labels = sorted(list(set(y_train + y_test)))
            conf_mat = confusion_matrix(y_test, sk_preds, labels=labels).tolist()
            report = classification_report(y_test, sk_preds, labels=labels, output_dict=True, zero_division=0)

            results["models"]["ScikitLearn_LogisticRegression"] = {
                "accuracy": round(sk_acc, 4),
                "macro_f1": round(sk_macro_f1, 4),
                "confusion_matrix": conf_mat,
                "labels": labels,
                "classification_report": report,
                "description": "Scikit-Learn TF-IDF N-Gram Vectorizer + L2 Logistic Regression"
            }

        return results

    def print_evaluation_summary(self, results: Dict[str, Any]) -> None:
        print("=" * 70)
        print("  PhoneCare-AI Diagnostic ML Evaluation Pipeline Report")
        print("=" * 70)
        print(f"Dataset Size:   {results['dataset_size']} total records")
        print(f"Split Details:  {results['train_size']} Train / {results['test_size']} Test (Stratified)")
        print(f"Data Leakage:   {'WARNING - Detected!' if results['data_leakage_detected'] else 'CLEAN (0 leaks detected)'}")

        if results["data_leakage_detected"]:
            for item in results["leakage_pairs"]:
                print(f"  ⚠ Leak between {item[0]} and {item[1]} (similarity: {item[2]:.2f})")

        print("-" * 70)
        print("  Model Benchmark Comparison")
        print("-" * 70)

        for name, data in results["models"].items():
            acc = data.get("accuracy", 0.0)
            f1 = data.get("macro_f1", None)
            f1_str = f" | Macro F1: {f1:.4f}" if f1 is not None else ""
            print(f"  • {name:<32}: Accuracy = {acc * 100:5.1f}%{f1_str}")
            print(f"    └─ {data.get('description', '')}")

        if "ScikitLearn_LogisticRegression" in results["models"]:
            sk_data = results["models"]["ScikitLearn_LogisticRegression"]
            print("\n" + "-" * 70)
            print("  Scikit-Learn Per-Class Diagnostic Performance")
            print("-" * 70)
            print(f"  {'Fault Category':<25} {'Precision':<10} {'Recall':<10} {'F1-Score':<10} {'Support'}")
            print("  " + "-" * 62)
            report = sk_data["classification_report"]
            for label in sk_data["labels"]:
                if label in report:
                    metrics = report[label]
                    print(f"  {label:<25} {metrics['precision']:<10.2f} {metrics['recall']:<10.2f} {metrics['f1-score']:<10.2f} {metrics['support']}")

        print("\n" + "=" * 70)
        print("  DATASET LIMITATIONS & GOVERNANCE NOTICE:")
        print("  • Sample size (64 records) is an initial benchmark for pipeline validation.")
        print("  • Synthetic records are explicitly labeled and must not be used alone to")
        print("    advertise production-grade generalization.")
        print("  • Additional observed physical device outcomes should be collected through")
        print("    controlled testing prior to final model freeze.")
        print("=" * 70)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Evaluate PhoneCare-AI diagnostic classification models.")
    parser.add_argument("--dataset", default="data/dataset/troubleshooting_benchmark.json", help="Path to benchmark JSON")
    parser.add_argument("--test-ratio", type=float, default=0.25, help="Test split ratio (default: 0.25)")
    parser.add_argument("--filter-provenance", default=None, help="Filter by provenance (e.g. CONTROLLED_EXPERIMENT)")

    args = parser.parse_args()
    pipeline = DiagnosticEvaluationPipeline(args.dataset)
    pipeline.load_dataset(provenance_filter=args.filter_provenance)
    res = pipeline.run_evaluation(test_ratio=args.test_ratio)
    pipeline.print_evaluation_summary(res)
