#!/usr/bin/env python3
"""
Unit tests for PhoneCare-AI Dataset Validation Tool
"""

import json
import os
import tempfile
import unittest

from ml.validate_dataset import DatasetValidator, DatasetValidationError


class TestDatasetValidation(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.benchmark_path = "data/dataset/troubleshooting_benchmark.json"

    def test_valid_benchmark_dataset(self):
        """Verifies that the canonical benchmark dataset passes all validation rules."""
        validator = DatasetValidator(self.benchmark_path)
        validator.load()
        is_valid = validator.validate()
        self.assertTrue(is_valid, f"Canonical benchmark should pass validation: {validator.errors}")
        self.assertEqual(len(validator.errors), 0)
        self.assertEqual(len(validator.records), 64)

        report = validator.generate_report()
        self.assertEqual(report["total_records"], 64)
        self.assertEqual(len(report["category_distribution"]), 8)

    def test_missing_required_fields(self):
        """Verifies that records missing required fields fail validation."""
        bad_data = {
            "records": [
                {
                    "case_id": "TEST-001",
                    "brand": "Samsung",
                    # Missing 'model', 'symptom_description', etc.
                    "target_fault_category": "Battery draining"
                }
            ]
        }
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as tf:
            json.dump(bad_data, tf)
            temp_path = tf.name

        try:
            validator = DatasetValidator(temp_path)
            validator.load()
            is_valid = validator.validate()
            self.assertFalse(is_valid)
            self.assertTrue(any("Missing required field" in err for err in validator.errors))
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_duplicate_case_id(self):
        """Verifies duplicate Case IDs are detected and flagged as errors."""
        bad_data = {
            "records": [
                {
                    "case_id": "DUPLICATE-001",
                    "brand": "Samsung",
                    "model": "Galaxy S24",
                    "android_version": "14",
                    "symptom_description": "Battery issue description here",
                    "usage_condition": "idle",
                    "diagnostic_observations": "Rapid drain",
                    "target_fault_category": "Battery draining",
                    "target_fault_subsystem": "wake_locks",
                    "recommended_action_id": "restrict_apps",
                    "recommended_action_title": "Restrict apps",
                    "outcome": "SUCCESSFUL",
                    "data_provenance": "CONTROLLED_EXPERIMENT",
                    "evidence_source": "Samsung Manual",
                    "source_url": "https://example.com"
                },
                {
                    "case_id": "DUPLICATE-001",  # Duplicate ID
                    "brand": "Google Pixel",
                    "model": "Pixel 8",
                    "android_version": "14",
                    "symptom_description": "Second issue description here",
                    "usage_condition": "idle",
                    "diagnostic_observations": "Rapid drain",
                    "target_fault_category": "Battery draining",
                    "target_fault_subsystem": "wake_locks",
                    "recommended_action_id": "restrict_apps",
                    "recommended_action_title": "Restrict apps",
                    "outcome": "SUCCESSFUL",
                    "data_provenance": "CONTROLLED_EXPERIMENT",
                    "evidence_source": "Pixel Manual",
                    "source_url": "https://example.com"
                }
            ]
        }
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as tf:
            json.dump(bad_data, tf)
            temp_path = tf.name

        try:
            validator = DatasetValidator(temp_path)
            validator.load()
            is_valid = validator.validate()
            self.assertFalse(is_valid)
            self.assertTrue(any("Duplicate case_id found" in err for err in validator.errors))
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_invalid_category(self):
        """Verifies invalid or misspelled categories fail validation."""
        bad_data = {
            "records": [
                {
                    "case_id": "TEST-002",
                    "brand": "Samsung",
                    "model": "Galaxy S24",
                    "android_version": "14",
                    "symptom_description": "My screen is cracked",
                    "usage_condition": "general",
                    "diagnostic_observations": "Physical shatter",
                    "target_fault_category": "Cracked Screen",  # Not one of 8 allowed categories
                    "target_fault_subsystem": "display_glass",
                    "recommended_action_id": "visit_service_center",
                    "recommended_action_title": "Screen replacement",
                    "outcome": "UNKNOWN",
                    "data_provenance": "SYNTHETIC_VARIATION",
                    "evidence_source": "None",
                    "source_url": "https://example.com"
                }
            ]
        }
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as tf:
            json.dump(bad_data, tf)
            temp_path = tf.name

        try:
            validator = DatasetValidator(temp_path)
            validator.load()
            is_valid = validator.validate()
            self.assertFalse(is_valid)
            self.assertTrue(any("Invalid target_fault_category" in err for err in validator.errors))
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_verified_documentation_requires_valid_url(self):
        """Verifies that VERIFIED_DOCUMENTATION records require a valid HTTP(S) source URL."""
        bad_data = {
            "records": [
                {
                    "case_id": "TEST-003",
                    "brand": "OnePlus",
                    "model": "OnePlus 12",
                    "android_version": "14",
                    "symptom_description": "Wi-Fi toggle issue",
                    "usage_condition": "general",
                    "diagnostic_observations": "Driver lock",
                    "target_fault_category": "Wi-Fi problems",
                    "target_fault_subsystem": "wpa_supplicant",
                    "recommended_action_id": "reset_network",
                    "recommended_action_title": "Reset network",
                    "outcome": "UNKNOWN",
                    "data_provenance": "VERIFIED_DOCUMENTATION",
                    "evidence_source": "Internal Forum",
                    "source_url": "invalid_url_without_http"  # Invalid URL
                }
            ]
        }
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as tf:
            json.dump(bad_data, tf)
            temp_path = tf.name

        try:
            validator = DatasetValidator(temp_path)
            validator.load()
            is_valid = validator.validate()
            self.assertFalse(is_valid)
            self.assertTrue(any("must have a valid HTTP(S)" in err for err in validator.errors))
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_provenance_separation_helpers(self):
        """Verifies that helper methods accurately filter by provenance."""
        validator = DatasetValidator(self.benchmark_path)
        validator.load()
        validator.validate()

        synthetic = validator.get_records_by_provenance("SYNTHETIC_VARIATION")
        experimental = validator.get_records_by_provenance("CONTROLLED_EXPERIMENT")
        verified_doc = validator.get_records_by_provenance("VERIFIED_DOCUMENTATION")

        self.assertEqual(len(synthetic), 16)
        self.assertEqual(len(experimental), 35)
        self.assertEqual(len(verified_doc), 13)
        self.assertEqual(len(synthetic) + len(experimental) + len(verified_doc), 64)


if __name__ == "__main__":
    unittest.main()
