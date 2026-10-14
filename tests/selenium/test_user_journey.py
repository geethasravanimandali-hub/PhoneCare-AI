#!/usr/bin/env python3
"""
PhoneCare-AI Selenium Automated UI Journey Tests
Automates the full user journey:
1. Loading the Web UI
2. Selecting phone brand, model, Android version, and problem category
3. Entering symptom description and submitting for diagnosis
4. Interacting with follow-up clarification questions (if prompted)
5. Reviewing the recommended troubleshooting step and confidence metadata
6. Submitting step feedback (Partially Solved / Not Solved) and checking attempt history
7. Confirming resolution with the Solved action
"""

import argparse
import json
import logging
import os
import sys
import time
import unittest
from http.server import SimpleHTTPRequestHandler, HTTPServer
import threading

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("selenium_test")


class MockBackendHandler(SimpleHTTPRequestHandler):
    """
    In-memory HTTP handler that serves the static frontend and mocks
    the PhoneCare-AI REST endpoints for standalone end-to-end UI testing.
    """

    def __init__(self, *args, **kwargs):
        # Set frontend directory as root for static assets
        frontend_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../frontend"))
        super().__init__(*args, directory=frontend_dir, **kwargs)

    def _send_json(self, status: int, data: dict):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/metadata":
            self._send_json(200, {
                "brandsWithModels": {
                    "Samsung": ["Galaxy S24", "Galaxy S23", "Galaxy A54"],
                    "Google Pixel": ["Pixel 8 Pro", "Pixel 8", "Pixel 7a"],
                    "OnePlus": ["OnePlus 12", "OnePlus 11"]
                },
                "categories": [
                    "Battery draining", "Phone overheating", "Wi-Fi problems",
                    "Mobile network problems", "Charging problems", "Camera problems",
                    "Storage problems", "Phone lagging"
                ]
            })
        elif self.path == "/api/ml/status":
            self._send_json(200, {"status": "ACTIVE", "portable_model_loaded": True})
        else:
            super().do_GET()

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(content_length).decode("utf-8")) if content_length > 0 else {}

        if self.path in ("/api/validate-problem", "/api/validate"):
            self._send_json(200, {
                "outcome": "MATCH",
                "selectedCategory": body.get("category", "Battery draining"),
                "detectedCategories": [body.get("category", "Battery draining")],
                "suggestedCategory": body.get("category", "Battery draining"),
                "message": "Selected category matches description.",
                "safetyPriority": False
            })
        elif self.path == "/api/diagnose":
            # Return follow-up clarification for ambiguous symptom, or direct step
            symptom = body.get("description", "").lower()
            if "clarify" in symptom or "overheat" in symptom:
                self._send_json(200, {
                    "sessionId": "mock-session-123",
                    "brand": body.get("brand", "Samsung"),
                    "model": body.get("model", "Galaxy S24"),
                    "category": body.get("category", "Phone overheating"),
                    "stepNumber": 1,
                    "title": "Diagnostic Clarification",
                    "instruction": "Please specify when the overheating occurs most noticeably.",
                    "explanation": "Helps pinpoint thermal throttling trigger.",
                    "isFinalStep": False,
                    "status": "IN_PROGRESS",
                    "suspectedCause": "Ambiguous symptom profile; requires diagnostic observation",
                    "confidence": 0.70,
                    "evidenceSource": "Android Core Diagnostic Triage Protocol",
                    "evidenceType": "INFERRED",
                    "followUpQuestion": "Under what specific conditions does your phone overheat most noticeably?",
                    "followUpOptions": [
                        "While charging with cable",
                        "While gaming or running heavy apps",
                        "While idle in pocket"
                    ],
                    "attemptHistory": []
                })
            else:
                self._send_json(200, {
                    "sessionId": "mock-session-123",
                    "brand": body.get("brand", "Samsung"),
                    "model": body.get("model", "Galaxy S24"),
                    "category": body.get("category", "Battery draining"),
                    "stepNumber": 1,
                    "title": "Put Unused Apps into Deep Sleep",
                    "instruction": "Navigate to Settings > Battery > Background usage limits and enable Put unused apps to sleep.",
                    "explanation": "Stops background wake-locks from draining battery during idle standby.",
                    "isFinalStep": False,
                    "status": "IN_PROGRESS",
                    "suspectedCause": "Unregulated background wake-locks preventing Doze mode",
                    "confidence": 0.90,
                    "evidenceSource": "PhoneCare-AI ML Model [Trained Classifier & Grounded Guidelines]",
                    "evidenceType": "VERIFIED",
                    "followUpQuestion": None,
                    "followUpOptions": [],
                    "attemptHistory": []
                })
        elif self.path == "/api/troubleshoot":
            outcome = body.get("outcome", "")
            if outcome in ("SOLVED", "SUCCESSFUL"):
                self._send_json(200, {
                    "sessionId": "mock-session-123",
                    "brand": "Samsung",
                    "model": "Galaxy S24",
                    "category": "Battery draining",
                    "stepNumber": 1,
                    "title": "Issue Resolved Successfully",
                    "instruction": "Your smartphone has resumed normal operation.",
                    "explanation": "The applied troubleshooting action successfully resolved the diagnostic condition.",
                    "isFinalStep": True,
                    "status": "SOLVED",
                    "finalRecommendation": "Troubleshooting complete. Keep your device updated with latest security patches.",
                    "suspectedCause": "Resolved configuration anomaly",
                    "confidence": 1.0,
                    "evidenceSource": "Verified User Outcome Confirmation",
                    "evidenceType": "VERIFIED",
                    "attemptHistory": [
                        {
                            "stepNumber": 1,
                            "stepTitle": "Put Unused Apps into Deep Sleep",
                            "userAction": "Executed diagnostic step",
                            "suspectedCause": "Background wake-locks",
                            "attempted": True,
                            "outcome": "SUCCESSFUL",
                            "userObservation": "Battery consumption returned to normal standby levels",
                            "recommendationSource": "DIAGNOSTIC_ML_MODEL",
                            "confidence": 0.90
                        }
                    ]
                })
            else:
                self._send_json(200, {
                    "sessionId": "mock-session-123",
                    "brand": "Samsung",
                    "model": "Galaxy S24",
                    "category": "Battery draining",
                    "stepNumber": 2,
                    "title": "Adjust Screen Refresh Rate & Brightness",
                    "instruction": "Go to Settings > Display > Motion smoothness and switch from Adaptive to Standard (60Hz).",
                    "explanation": "High refresh rate displays consume significant power during screen-on usage.",
                    "isFinalStep": False,
                    "status": "IN_PROGRESS",
                    "suspectedCause": "Display panel refresh draw",
                    "confidence": 0.88,
                    "evidenceSource": "Grounded Knowledge Base [OEM Technical Documentation]",
                    "evidenceType": "VERIFIED",
                    "followUpQuestion": None,
                    "followUpOptions": [],
                    "attemptHistory": [
                        {
                            "stepNumber": 1,
                            "stepTitle": "Put Unused Apps into Deep Sleep",
                            "userAction": "Executed diagnostic step",
                            "suspectedCause": "Background wake-locks",
                            "attempted": True,
                            "outcome": "PARTIALLY_SUCCESSFUL",
                            "userObservation": "Minor improvement, but drain persists",
                            "recommendationSource": "DIAGNOSTIC_ML_MODEL",
                            "confidence": 0.90
                        }
                    ]
                })
        else:
            self.send_error(404, "Endpoint Not Found")

    def log_message(self, format, *args):
        pass  # Suppress noisy HTTP request logs during testing


class PhoneCareSeleniumTest(unittest.TestCase):

    server: HTTPServer = None
    server_thread: threading.Thread = None
    base_url: str = ""
    driver = None

    @classmethod
    def setUpClass(cls):
        # 1. Start local mock server if live Spring Boot backend is not already running on port 8080
        target_url = os.environ.get("PHONECARE_BASE_URL", "http://localhost:8080")
        live_server_found = False
        try:
            import urllib.request
            with urllib.request.urlopen(f"{target_url}/api/metadata", timeout=1):
                live_server_found = True
                cls.base_url = target_url
                logger.info(f"Connected to live backend at {target_url}")
        except Exception:
            live_server_found = False

        if not live_server_found:
            cls.server = HTTPServer(("127.0.0.1", 0), MockBackendHandler)
            cls.port = cls.server.server_address[1]
            cls.base_url = f"http://127.0.0.1:{cls.port}"
            cls.server_thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
            cls.server_thread.start()
            logger.info(f"Started mock test server at {cls.base_url}")

        # 2. Initialize Headless Chrome with Selenium
        try:
            from selenium import webdriver
            from selenium.webdriver.chrome.options import Options

            options = Options()
            options.add_argument("--headless=new")
            options.add_argument("--no-sandbox")
            options.add_argument("--disable-dev-shm-usage")
            options.add_argument("--disable-gpu")
            options.add_argument("--window-size=1280,900")

            cls.driver = webdriver.Chrome(options=options)
            cls.driver.implicitly_wait(5)
            logger.info("Selenium WebDriver Chrome initialized successfully in headless mode.")
        except Exception as e:
            logger.warning(f"Chrome WebDriver could not be initialized: {e}")
            cls.driver = None

    @classmethod
    def tearDownClass(cls):
        if cls.driver:
            try:
                cls.driver.quit()
            except Exception:
                pass
        if cls.server:
            cls.server.shutdown()
            cls.server.server_close()

    def setUp(self):
        if not self.driver:
            self.skipTest("Selenium Chrome driver is not available in the current environment.")

    def test_full_user_journey_battery_drain(self):
        """
        Tests user journey: Form filling -> Diagnosis -> Step 1 -> Not Solved -> Step 2 -> Solved.
        """
        from selenium.webdriver.common.by import By
        from selenium.webdriver.support.ui import Select, WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC

        driver = self.driver
        driver.get(self.base_url)

        # 1. Verify Page Loaded
        WebDriverWait(driver, 5).until(EC.presence_of_element_located((By.ID, "brand-select")))
        self.assertIn("PhoneCare", driver.title)

        # 2. Fill in Phone & Problem Form
        Select(driver.find_element(By.ID, "brand-select")).select_by_visible_text("Samsung")
        time.sleep(0.3)
        Select(driver.find_element(By.ID, "model-select")).select_by_visible_text("Galaxy S24")
        Select(driver.find_element(By.ID, "category-select")).select_by_visible_text("Battery draining")

        android_select = driver.find_element(By.ID, "android-select")
        Select(android_select).select_by_visible_text("Android 14")

        desc_input = driver.find_element(By.ID, "description-input")
        desc_input.clear()
        desc_input.send_keys("My battery drops very quickly overnight when phone is idle.")

        # 3. Submit Diagnosis
        diagnose_btn = driver.find_element(By.ID, "diagnose-btn")
        diagnose_btn.click()

        # 4. Verify Transition to Interactive Troubleshooting View
        troubleshooting_view = WebDriverWait(driver, 5).until(
            EC.visibility_of_element_located((By.ID, "troubleshooting-view"))
        )
        self.assertTrue(troubleshooting_view.is_displayed())

        # Check Device Summary Pill values
        summary_brand = driver.find_element(By.ID, "summary-brand").text
        summary_model = driver.find_element(By.ID, "summary-model").text
        self.assertEqual(summary_brand, "Samsung")
        self.assertEqual(summary_model, "Galaxy S24")

        # 5. Verify Step 1 Card & Root Cause Display
        step_title = WebDriverWait(driver, 5).until(
            EC.visibility_of_element_located((By.ID, "step-title"))
        )
        self.assertTrue(len(step_title.text) > 0)
        logger.info(f"Verified Step 1 Title: {step_title.text}")

        # 6. Submit Observation and Click "Partially Solved"
        obs_input = driver.find_element(By.ID, "step-observation-input")
        obs_input.send_keys("Standby drain slowed down slightly, but issue persists.")

        btn_partial = driver.find_element(By.ID, "btn-partially-solved")
        btn_partial.click()

        # 7. Verify Advance to Next Step & History Card Display
        time.sleep(0.8)
        history_card = driver.find_element(By.ID, "attempt-history-card")
        self.assertFalse("hidden" in history_card.get_attribute("class"))
        history_count = driver.find_element(By.ID, "history-count").text
        self.assertEqual(history_count, "1")
        logger.info("Verified attempt history recorded previous step.")

        # 8. Confirm Final Resolution by Clicking "Solved"
        btn_solved = WebDriverWait(driver, 5).until(
            EC.element_to_be_clickable((By.ID, "btn-solved"))
        )
        btn_solved.click()

        time.sleep(0.8)
        step_status = driver.find_element(By.ID, "step-status").text
        self.assertTrue("Solved" in step_status or "SOLVED" in step_status or "Complete" in step_status)
        logger.info("Verified troubleshooting completed with Solved resolution.")

    def test_adaptive_clarification_interaction(self):
        """
        Tests adaptive clarification flow when symptom requires disambiguation.
        """
        from selenium.webdriver.common.by import By
        from selenium.webdriver.support.ui import Select, WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC

        driver = self.driver
        driver.get(self.base_url)

        WebDriverWait(driver, 5).until(EC.presence_of_element_located((By.ID, "brand-select")))
        Select(driver.find_element(By.ID, "brand-select")).select_by_visible_text("Samsung")
        time.sleep(0.3)
        Select(driver.find_element(By.ID, "model-select")).select_by_visible_text("Galaxy S24")
        Select(driver.find_element(By.ID, "category-select")).select_by_visible_text("Phone overheating")

        desc_input = driver.find_element(By.ID, "description-input")
        desc_input.clear()
        desc_input.send_keys("Phone gets hot clarify")

        driver.find_element(By.ID, "diagnose-btn").click()

        # Verify clarification container is displayed
        clarify_container = WebDriverWait(driver, 5).until(
            EC.visibility_of_element_located((By.ID, "clarification-container"))
        )
        self.assertTrue(clarify_container.is_displayed())

        # Select first option
        options = driver.find_elements(By.CSS_SELECTOR, "#clarification-options input[type='radio']")
        if options:
            driver.execute_script("arguments[0].click();", options[0])
            submit_btn = driver.find_element(By.ID, "btn-submit-clarification")
            self.assertTrue(submit_btn.is_enabled())
            submit_btn.click()
            time.sleep(0.8)

            # Step container should now be visible
            step_container = driver.find_element(By.ID, "step-container")
            self.assertFalse("hidden" in step_container.get_attribute("class"))


if __name__ == "__main__":
    unittest.main()
