#!/usr/bin/env python3
"""
Unit tests for PhoneCare-AI ML Model Training, Artifact Loading, and Prediction
"""

import json
import os
import shutil
import tempfile
import unittest

from ml.predict import DiagnosticPredictor
from ml.train_model import train_and_evaluate


class TestDiagnosticML(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.models_dir = "ml/models"
        cls.dataset_path = "data/dataset/troubleshooting_benchmark.json"

    def test_artifacts_exist_and_valid(self):
        """Verifies that model artifacts exist and have expected structure."""
        joblib_path = os.path.join(self.models_dir, "diagnostic_model.joblib")
        portable_path = os.path.join(self.models_dir, "portable_model.json")
        metadata_path = os.path.join(self.models_dir, "model_metadata.json")

        self.assertTrue(os.path.exists(portable_path), "portable_model.json must exist")
        self.assertTrue(os.path.exists(metadata_path), "model_metadata.json must exist")

        with open(metadata_path, "r", encoding="utf-8") as f:
            metadata = json.load(f)

        self.assertIn("baselines", metadata)
        self.assertIn("trained_model", metadata)
        self.assertIn("categories", metadata)
        self.assertEqual(len(metadata["categories"]), 8)

    def test_predict_battery_drain(self):
        """Verifies prediction for canonical battery drain symptom."""
        predictor = DiagnosticPredictor(models_dir=self.models_dir)
        result = predictor.predict(
            symptom="Battery drains very fast overnight while on standby",
            brand="Samsung",
            model="Galaxy S24",
            android_version="14"
        )

        self.assertEqual(result["status"], "SUCCESS")
        self.assertEqual(result["predicted_category"], "Battery draining")
        self.assertIn(result["confidence_level"], ("MEDIUM", "HIGH"))
        self.assertFalse(result["is_uncertain"])
        self.assertGreater(result["vocabulary_overlap_ratio"], 0.3)

    def test_predict_wifi_issue(self):
        """Verifies prediction for canonical Wi-Fi symptom."""
        predictor = DiagnosticPredictor(models_dir=self.models_dir)
        result = predictor.predict(
            symptom="Cannot connect to home Wi-Fi router, keeps dropping and obtaining IP address",
            brand="Google Pixel",
            model="Pixel 8"
        )

        self.assertEqual(result["status"], "SUCCESS")
        self.assertEqual(result["predicted_category"], "Wi-Fi problems")
        self.assertIn(result["confidence_level"], ("MEDIUM", "HIGH"))

    def test_portable_fallback_prediction(self):
        """Verifies prediction using pure-Python portable weights."""
        predictor = DiagnosticPredictor(models_dir=self.models_dir, force_portable=True)
        self.assertIn("Pure-Python", predictor.model_type)

        result = predictor.predict(
            symptom="phone gets burning hot while fast charging",
            brand="Xiaomi",
            model="Xiaomi 14"
        )

        self.assertEqual(result["status"], "SUCCESS")
        self.assertIn(result["predicted_category"], ("Phone overheating", "Charging problems"))
        self.assertIn("confidence_score", result)
        self.assertIn("top_candidates", result)
        self.assertEqual(len(result["top_candidates"]), 3)

    def test_invalid_input_rejection(self):
        """Verifies empty or whitespace-only symptoms are rejected with ValueError."""
        predictor = DiagnosticPredictor(models_dir=self.models_dir)

        with self.assertRaises(ValueError):
            predictor.predict("")

        with self.assertRaises(ValueError):
            predictor.predict("     ")

        with self.assertRaises(ValueError):
            predictor.predict(None)

        with self.assertRaises(ValueError):
            predictor.predict("!!! ??? ...")

    def test_out_of_vocabulary_uncertainty(self):
        """Verifies out-of-distribution symptom triggers uncertainty flag."""
        predictor = DiagnosticPredictor(models_dir=self.models_dir)
        result = predictor.predict(
            symptom="xyzqwerty zzzblabla nonesuch qwertyuiop"
        )

        self.assertEqual(result["status"], "SUCCESS")
        self.assertTrue(result["is_uncertain"])
        self.assertEqual(result["confidence_level"], "LOW")
        self.assertIn("unfamiliar", result["uncertainty_reason"].lower())

    def test_training_pipeline_run(self):
        """Verifies train_and_evaluate runs and creates artifacts in a temp directory."""
        temp_dir = tempfile.mkdtemp(prefix="phonecare_ml_test_")
        try:
            results = train_and_evaluate(
                dataset_path=self.dataset_path,
                output_dir=temp_dir,
                seed=42
            )

            self.assertIn("trained_model", results)
            self.assertIn("empirical_test_accuracy", results["trained_model"])
            self.assertTrue(os.path.exists(os.path.join(temp_dir, "portable_model.json")))
            self.assertTrue(os.path.exists(os.path.join(temp_dir, "model_metadata.json")))
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
