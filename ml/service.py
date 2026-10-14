#!/usr/bin/env python3
"""
PhoneCare-AI Lightweight Diagnostic ML Prediction Microservice
Provides a zero-extra-dependency HTTP REST API around DiagnosticPredictor
using Python's standard library http.server.
"""

import argparse
import json
import logging
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Optional

# Ensure project root is on sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ml.predict import DiagnosticPredictor

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("ml_service")


class DiagnosticServiceHandler(BaseHTTPRequestHandler):

    predictor: Optional[DiagnosticPredictor] = None

    def _send_json(self, status_code: int, data: dict):
        body = json.dumps(data, indent=2).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self._send_json(200, {"status": "OK"})

    def do_GET(self):
        if self.path in ("/health", "/api/health", "/"):
            self._send_json(200, {
                "status": "UP",
                "service": "PhoneCare-AI ML Diagnostic Service",
                "model_type": self.predictor.model_type if self.predictor else "NOT_INITIALIZED",
                "classes_count": len(self.predictor.classes) if self.predictor else 0
            })
        else:
            self._send_json(404, {"error": "Not Found", "path": self.path})

    def do_POST(self):
        if self.path in ("/predict", "/api/predict"):
            content_length = int(self.headers.get("Content-Length", 0))
            if content_length == 0:
                self._send_json(400, {
                    "status": "ERROR",
                    "message": "Empty request body. JSON payload with 'symptom' required."
                })
                return

            try:
                body_bytes = self.rfile.read(content_length)
                payload = json.loads(body_bytes.decode("utf-8"))
            except Exception as e:
                self._send_json(400, {
                    "status": "ERROR",
                    "message": f"Malformed JSON: {str(e)}"
                })
                return

            symptom = payload.get("symptom", "")
            brand = payload.get("brand", "")
            model = payload.get("model", "")
            android_version = payload.get("android_version", payload.get("androidVersion", ""))
            observations = payload.get("observations", payload.get("diagnosticObservations", ""))

            try:
                result = self.predictor.predict(
                    symptom=symptom,
                    brand=brand,
                    model=model,
                    android_version=android_version,
                    diagnostic_observations=observations
                )
                self._send_json(200, result)
            except ValueError as ve:
                self._send_json(400, {
                    "status": "ERROR",
                    "message": str(ve),
                    "is_uncertain": True,
                    "confidence_level": "LOW"
                })
            except Exception as ex:
                logger.error(f"Prediction failed: {ex}", exc_info=True)
                self._send_json(500, {
                    "status": "ERROR",
                    "message": f"Internal inference error: {str(ex)}",
                    "is_uncertain": True,
                    "confidence_level": "LOW"
                })
        else:
            self._send_json(404, {"error": "Not Found", "path": self.path})

    def log_message(self, format, *args):
        logger.info(f"{self.address_string()} - {format % args}")


def run_service(host: str = "0.0.0.0", port: int = 5001, models_dir: Optional[str] = None):
    logger.info(f"Initializing DiagnosticPredictor from models_dir={models_dir}...")
    predictor = DiagnosticPredictor(models_dir=models_dir)
    logger.info(f"Loaded model: {predictor.model_type} ({len(predictor.classes)} classes)")

    DiagnosticServiceHandler.predictor = predictor
    server = HTTPServer((host, port), DiagnosticServiceHandler)
    logger.info(f"PhoneCare-AI ML Service listening on http://{host}:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("Service interrupted by user. Shutting down...")
    finally:
        server.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="PhoneCare-AI ML Prediction HTTP Microservice")
    parser.add_argument("--host", default="0.0.0.0", help="Binding host")
    parser.add_argument("--port", type=int, default=5001, help="Binding port (default: 5001)")
    parser.add_argument("--models-dir", default=None, help="Directory containing model artifacts")

    args = parser.parse_args()
    run_service(host=args.host, port=args.port, models_dir=args.models_dir)
