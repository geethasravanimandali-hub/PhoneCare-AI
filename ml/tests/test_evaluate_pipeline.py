#!/usr/bin/env python3
"""
Unit tests for PhoneCare-AI Diagnostic ML Evaluation Pipeline
"""

import json
import os
import tempfile
import unittest

from ml.evaluate_pipeline import (
    DiagnosticEvaluationPipeline,
    PurePythonTFIDFNaiveBayes,
    check_data_leakage,
    jaccard_similarity,
    prepare_feature_text,
    stratified_split,
)


class TestEvaluationPipeline(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.benchmark_path = "data/dataset/troubleshooting_benchmark.json"

    def test_jaccard_similarity(self):
        """Verifies text similarity calculation."""
        text1 = "Battery drops rapidly overnight while on standby"
        text2 = "Battery drops quickly overnight while on standby"
        text3 = "Wi-Fi disconnected from home router"

        sim_high = jaccard_similarity(text1, text2)
        sim_low = jaccard_similarity(text1, text3)

        self.assertGreater(sim_high, 0.6)
        self.assertLess(sim_low, 0.2)
        self.assertEqual(jaccard_similarity("", ""), 0.0)

    def test_data_leakage_detection(self):
        """Verifies data leakage detector identifies overlapping symptom texts."""
        train_records = [
            {"case_id": "TR-1", "symptom_description": "Phone overheats heavily while gaming in silicone case"}
        ]
        test_records_leaked = [
            {"case_id": "TE-1", "symptom_description": "Phone overheats heavily while gaming in silicone case"}
        ]
        test_records_clean = [
            {"case_id": "TE-2", "symptom_description": "Camera app crashes with failed error code"}
        ]

        leaks = check_data_leakage(train_records, test_records_leaked, threshold=0.85)
        self.assertEqual(len(leaks), 1)
        self.assertEqual(leaks[0][0], "TR-1")
        self.assertEqual(leaks[0][1], "TE-1")

        no_leaks = check_data_leakage(train_records, test_records_clean, threshold=0.85)
        self.assertEqual(len(no_leaks), 0)

    def test_stratified_split(self):
        """Verifies stratified split preserves class representation."""
        pipeline = DiagnosticEvaluationPipeline(self.benchmark_path)
        records = pipeline.load_dataset()

        train_set, test_set = stratified_split(records, test_ratio=0.25, seed=42)

        self.assertEqual(len(train_set) + len(test_set), len(records))
        self.assertGreater(len(train_set), len(test_set))

        # Check all 8 categories exist in both train and test sets
        train_cats = {r["target_fault_category"] for r in train_set}
        test_cats = {r["target_fault_category"] for r in test_set}

        self.assertEqual(len(train_cats), 8)
        self.assertEqual(len(test_cats), 8)

    def test_pure_python_naive_bayes_fit_predict(self):
        """Verifies built-in Naive Bayes classifier fits and makes sensible predictions."""
        X_train = [
            "battery drains fast dies overnight standby",
            "phone overheats hot temperature burning gaming",
            "wifi disconnects router no internet wlan",
            "storage full disk space out of memory clean",
        ]
        y_train = [
            "Battery draining",
            "Phone overheating",
            "Wi-Fi problems",
            "Storage problems",
        ]

        clf = PurePythonTFIDFNaiveBayes()
        clf.fit(X_train, y_train)

        test_query = ["my phone battery drops to zero while sleeping"]
        preds = clf.predict(test_query)
        self.assertEqual(preds[0], "Battery draining")

        test_query_wifi = ["cannot connect to home wifi router"]
        preds_wifi = clf.predict(test_query_wifi)
        self.assertEqual(preds_wifi[0], "Wi-Fi problems")

    def test_full_pipeline_run_on_benchmark(self):
        """Runs the entire evaluation pipeline on the benchmark dataset."""
        pipeline = DiagnosticEvaluationPipeline(self.benchmark_path)
        results = pipeline.run_evaluation(test_ratio=0.25, seed=42)

        self.assertEqual(results["dataset_size"], 64)
        self.assertFalse(results["data_leakage_detected"])
        self.assertIn("Majority_Class_Baseline", results["models"])
        self.assertIn("PurePython_TFIDF_NaiveBayes", results["models"])

        # Majority class baseline on 8 balanced classes should be 12.5%
        self.assertAlmostEqual(results["models"]["Majority_Class_Baseline"]["accuracy"], 0.125, places=2)

        # Naive Bayes should easily outperform majority class
        nb_acc = results["models"]["PurePython_TFIDF_NaiveBayes"]["accuracy"]
        self.assertGreater(nb_acc, results["models"]["Majority_Class_Baseline"]["accuracy"])


if __name__ == "__main__":
    unittest.main()
