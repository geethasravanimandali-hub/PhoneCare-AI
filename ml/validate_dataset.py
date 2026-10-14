#!/usr/bin/env python3
"""
PhoneCare-AI Dataset Validation Tool
Validates troubleshooting dataset records against schema, constraints, provenance, and citations.
"""

import json
import os
import sys
from collections import Counter
from typing import Dict, List, Any, Tuple, Optional

# Canonical allowed categories matching PhoneCare-AI knowledge base & backend
ALLOWED_CATEGORIES = {
    "Battery draining",
    "Phone overheating",
    "Wi-Fi problems",
    "Mobile network problems",
    "Charging problems",
    "Camera problems",
    "Storage problems",
    "Phone lagging",
}

# Provenance definitions
ALLOWED_PROVENANCE = {
    "VERIFIED_DOCUMENTATION",
    "CONTROLLED_EXPERIMENT",
    "SYNTHETIC_VARIATION",
}

# Allowed observed outcomes
ALLOWED_OUTCOMES = {
    "SUCCESSFUL",
    "PARTIALLY_SUCCESSFUL",
    "UNSUCCESSFUL",
    "UNKNOWN",
}

# Required schema fields for each troubleshooting record
REQUIRED_FIELDS = [
    "case_id",
    "brand",
    "model",
    "android_version",
    "symptom_description",
    "usage_condition",
    "diagnostic_observations",
    "target_fault_category",
    "target_fault_subsystem",
    "recommended_action_id",
    "recommended_action_title",
    "outcome",
    "data_provenance",
    "evidence_source",
    "source_url",
]


class DatasetValidationError(Exception):
    """Exception raised when dataset schema or integrity validation fails."""
    pass


class DatasetValidator:
    """Validates PhoneCare-AI dataset records and generates comprehensive audit reports."""

    def __init__(self, dataset_path: str):
        self.dataset_path = dataset_path
        self.raw_data: Dict[str, Any] = {}
        self.records: List[Dict[str, Any]] = []
        self.errors: List[str] = []
        self.warnings: List[str] = []

    def load(self) -> None:
        """Loads and parses the dataset JSON file."""
        if not os.path.exists(self.dataset_path):
            raise FileNotFoundError(f"Dataset file not found at: {self.dataset_path}")

        try:
            with open(self.dataset_path, "r", encoding="utf-8") as f:
                self.raw_data = json.load(f)
        except json.JSONDecodeError as e:
            raise DatasetValidationError(f"Invalid JSON syntax in {self.dataset_path}: {e}")

        if not isinstance(self.raw_data, dict):
            raise DatasetValidationError("Dataset root must be a JSON object")

        if "records" not in self.raw_data or not isinstance(self.raw_data["records"], list):
            raise DatasetValidationError("Dataset must contain a 'records' list")

        self.records = self.raw_data["records"]

    def validate(self) -> bool:
        """Performs comprehensive validation across all dataset records."""
        self.errors.clear()
        self.warnings.clear()

        if not self.records:
            self.errors.append("Dataset 'records' list is empty.")
            return False

        seen_ids = set()

        for idx, record in enumerate(self.records):
            record_id = record.get("case_id", f"Record#{idx}")

            # 1. Check all required fields are present and non-empty
            for field in REQUIRED_FIELDS:
                if field not in record:
                    self.errors.append(f"[{record_id}] Missing required field '{field}'")
                elif record[field] is None:
                    self.errors.append(f"[{record_id}] Field '{field}' cannot be null")
                elif isinstance(record[field], str) and not record[field].strip():
                    self.errors.append(f"[{record_id}] Field '{field}' cannot be empty")

            # 2. Check Case ID uniqueness
            case_id = record.get("case_id")
            if case_id:
                if case_id in seen_ids:
                    self.errors.append(f"Duplicate case_id found: '{case_id}' at index {idx}")
                seen_ids.add(case_id)

            # 3. Check allowed category
            cat = record.get("target_fault_category")
            if cat and cat not in ALLOWED_CATEGORIES:
                self.errors.append(
                    f"[{record_id}] Invalid target_fault_category '{cat}'. "
                    f"Allowed categories: {sorted(ALLOWED_CATEGORIES)}"
                )

            # 4. Check allowed data provenance
            prov = record.get("data_provenance")
            if prov and prov not in ALLOWED_PROVENANCE:
                self.errors.append(
                    f"[{record_id}] Invalid data_provenance '{prov}'. "
                    f"Allowed: {sorted(ALLOWED_PROVENANCE)}"
                )

            # 5. Check allowed outcome
            outcome = record.get("outcome")
            if outcome and outcome not in ALLOWED_OUTCOMES:
                self.errors.append(
                    f"[{record_id}] Invalid outcome '{outcome}'. "
                    f"Allowed: {sorted(ALLOWED_OUTCOMES)}"
                )

            # 6. Source citation requirements for verified/experimental records
            if prov in ("VERIFIED_DOCUMENTATION", "CONTROLLED_EXPERIMENT"):
                source = record.get("evidence_source", "")
                url = record.get("source_url", "")
                if not source or not source.strip():
                    self.errors.append(f"[{record_id}] {prov} records must have an 'evidence_source'")
                if not url or not (url.startswith("http://") or url.startswith("https://")):
                    self.errors.append(
                        f"[{record_id}] {prov} records must have a valid HTTP(S) 'source_url'"
                    )

            # 7. Strict Integrity: Verified documentation cannot claim observed success unless tested
            if prov == "VERIFIED_DOCUMENTATION" and outcome in ("SUCCESSFUL", "PARTIALLY_SUCCESSFUL", "UNSUCCESSFUL"):
                self.warnings.append(
                    f"[{record_id}] VERIFIED_DOCUMENTATION has observed outcome '{outcome}'. "
                    f"Ensure this was an actual observed test on a device, not assumed from doc recommendation."
                )

        return len(self.errors) == 0

    def generate_report(self) -> Dict[str, Any]:
        """Generates statistical distribution and validation summary."""
        total = len(self.records)
        categories = Counter(r.get("target_fault_category") for r in self.records)
        provenance = Counter(r.get("data_provenance") for r in self.records)
        outcomes = Counter(r.get("outcome") for r in self.records)
        brands = Counter(r.get("brand") for r in self.records)

        return {
            "total_records": total,
            "category_distribution": dict(categories),
            "provenance_distribution": dict(provenance),
            "outcome_distribution": dict(outcomes),
            "brand_distribution": dict(brands),
            "error_count": len(self.errors),
            "warning_count": len(self.warnings),
            "errors": self.errors,
            "warnings": self.warnings,
            "is_valid": len(self.errors) == 0,
        }

    def print_summary(self) -> None:
        """Prints a human-readable validation summary."""
        report = self.generate_report()
        print("=" * 65)
        print("  PhoneCare-AI Troubleshooting Dataset Validation Report")
        print("=" * 65)
        print(f"Dataset Path: {self.dataset_path}")
        print(f"Total Records: {report['total_records']}")
        print(f"Status:        {'PASSED (VALID)' if report['is_valid'] else 'FAILED'}")
        print("-" * 65)

        print("\n[Provenance Breakdown]")
        for prov, count in report["provenance_distribution"].items():
            pct = (count / report['total_records']) * 100 if report['total_records'] else 0
            print(f"  • {prov:<25}: {count:3d} ({pct:5.1f}%)")

        print("\n[Fault Category Breakdown]")
        for cat, count in report["category_distribution"].items():
            print(f"  • {cat:<25}: {count:3d} records")

        print("\n[Observed Outcome Breakdown]")
        for outcome, count in report["outcome_distribution"].items():
            print(f"  • {outcome:<25}: {count:3d}")

        print("\n[Brand Coverage]")
        for brand, count in report["brand_distribution"].items():
            print(f"  • {brand:<25}: {count:3d}")

        if report["warnings"]:
            print(f"\n[Warnings ({len(report['warnings'])})]")
            for w in report["warnings"][:5]:
                print(f"  ⚠ {w}")
            if len(report["warnings"]) > 5:
                print(f"  ... and {len(report['warnings']) - 5} more warnings")

        if report["errors"]:
            print(f"\n[Errors ({len(report['errors'])})]")
            for e in report["errors"]:
                print(f"  ✖ {e}")
        print("=" * 65)

    def get_records_by_provenance(self, provenance_type: str) -> List[Dict[str, Any]]:
        """Returns subset of records matching a specific provenance type."""
        return [r for r in self.records if r.get("data_provenance") == provenance_type]


def validate_dataset_file(filepath: str) -> Tuple[bool, Dict[str, Any]]:
    """Helper function to load and validate dataset file."""
    validator = DatasetValidator(filepath)
    validator.load()
    is_valid = validator.validate()
    return is_valid, validator.generate_report()


if __name__ == "__main__":
    dataset_file = sys.argv[1] if len(sys.argv) > 1 else "data/dataset/troubleshooting_benchmark.json"
    validator = DatasetValidator(dataset_file)
    try:
        validator.load()
        valid = validator.validate()
        validator.print_summary()
        sys.exit(0 if valid else 1)
    except Exception as exc:
        print(f"Validation failed with exception: {exc}", file=sys.stderr)
        sys.exit(1)
