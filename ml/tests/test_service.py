#!/usr/bin/env python3
"""
Unit tests for PhoneCare-AI ML Prediction HTTP Microservice Handlers
Tests request parsing, response formatting, and error handling without requiring network sockets.
"""

import io
import json
import unittest
from unittest.mock import MagicMock

from ml.predict import DiagnosticPredictor
from ml.service import DiagnosticServiceHandler


class DummyRequest:
    def __init__(self, data=b""):
        self.data = data

    def makefile(self, mode, *args, **kwargs):
        if "b" in mode:
            return io.BytesIO(self.data)
        return io.StringIO(self.data.decode("utf-8"))

    def sendall(self, data):
        pass


class TestMLServiceHandlers(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.predictor = DiagnosticPredictor(models_dir="ml/models")
        DiagnosticServiceHandler.predictor = cls.predictor

    def _create_handler(self, method: str, path: str, body: dict = None):
        """Creates a DiagnosticServiceHandler configured with in-memory streams."""
        body_bytes = json.dumps(body).encode("utf-8") if body is not None else b""
        rfile = io.BytesIO(body_bytes)
        wfile = io.BytesIO()

        # Instantiate handler without calling default __init__ which binds to real socket
        handler = DiagnosticServiceHandler.__new__(DiagnosticServiceHandler)
        handler.rfile = rfile
        handler.wfile = wfile
        handler.command = method
        handler.path = path
        handler.request_version = "HTTP/1.1"
        handler.requestline = f"{method} {path} HTTP/1.1"
        handler.headers = {
            "Content-Length": str(len(body_bytes)),
            "Content-Type": "application/json"
        }
        handler.client_address = ("127.0.0.1", 12345)
        handler.server = MagicMock()
        return handler, wfile

    def _extract_response(self, wfile: io.BytesIO):
        """Extracts status code and JSON payload from serialized HTTP response."""
        wfile.seek(0)
        output = wfile.read().decode("utf-8")
        parts = output.split("\r\n\r\n", 1)
        header_part = parts[0]
        body_part = parts[1] if len(parts) > 1 else ""
        status_line = header_part.splitlines()[0]
        status_code = int(status_line.split()[1])
        data = json.loads(body_part) if body_part else {}
        return status_code, data

    def test_health_check_endpoint(self):
        """Verifies GET /health returns UP status and model details."""
        handler, wfile = self._create_handler("GET", "/health")
        handler.do_GET()

        status_code, data = self._extract_response(wfile)
        self.assertEqual(status_code, 200)
        self.assertEqual(data["status"], "UP")
        self.assertEqual(data["classes_count"], 8)
        self.assertIn("scikit-learn", data["model_type"])

    def test_predict_endpoint_success(self):
        """Verifies POST /predict processes valid input and returns predictions."""
        payload = {
            "symptom": "battery draining very quickly overnight on standby",
            "brand": "Samsung",
            "model": "Galaxy S24",
            "android_version": "14"
        }
        handler, wfile = self._create_handler("POST", "/predict", payload)
        handler.do_POST()

        status_code, data = self._extract_response(wfile)
        self.assertEqual(status_code, 200)
        self.assertEqual(data["status"], "SUCCESS")
        self.assertEqual(data["predicted_category"], "Battery draining")
        self.assertIn(data["confidence_level"], ("MEDIUM", "HIGH"))
        self.assertIn("top_candidates", data)

    def test_predict_endpoint_empty_symptom(self):
        """Verifies POST /predict returns 400 Bad Request for whitespace symptom."""
        payload = {"symptom": "   "}
        handler, wfile = self._create_handler("POST", "/predict", payload)
        handler.do_POST()

        status_code, data = self._extract_response(wfile)
        self.assertEqual(status_code, 400)
        self.assertEqual(data["status"], "ERROR")
        self.assertTrue(data["is_uncertain"])

    def test_not_found_endpoint(self):
        """Verifies unknown endpoint returns 404."""
        handler, wfile = self._create_handler("GET", "/unknown-route")
        handler.do_GET()

        status_code, data = self._extract_response(wfile)
        self.assertEqual(status_code, 404)


if __name__ == "__main__":
    unittest.main()
