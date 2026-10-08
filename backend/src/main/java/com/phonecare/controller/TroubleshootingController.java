package com.phonecare.controller;

import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.MetadataResponse;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootResponse;
import com.phonecare.model.TroubleshootStepRequest;
import com.phonecare.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TroubleshootingController {

    private final SessionService sessionService;

    public TroubleshootingController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/metadata")
    public ResponseEntity<MetadataResponse> getMetadata() {
        Map<String, List<String>> brandsWithModels = new LinkedHashMap<>();
        brandsWithModels.put("Samsung", List.of("Galaxy S24", "Galaxy S23", "Galaxy A54", "Galaxy Z Fold 5"));
        brandsWithModels.put("Google Pixel", List.of("Pixel 8 Pro", "Pixel 8", "Pixel 7a", "Pixel 6"));
        brandsWithModels.put("OnePlus", List.of("OnePlus 12", "OnePlus 11", "OnePlus Nord 3"));
        brandsWithModels.put("Xiaomi", List.of("Xiaomi 14", "Redmi Note 13", "Poco X6 Pro"));
        brandsWithModels.put("Motorola", List.of("Edge 50 Pro", "Moto G84", "Razr 40"));

        List<String> categories = List.of(
                "Battery draining",
                "Phone overheating",
                "Wi-Fi problems",
                "Mobile network problems",
                "Charging problems",
                "Camera problems",
                "Storage problems",
                "Phone lagging"
        );

        return ResponseEntity.ok(new MetadataResponse(brandsWithModels, categories));
    }

    @PostMapping("/diagnose")
    public ResponseEntity<TroubleshootResponse> diagnose(@Valid @RequestBody DiagnosisRequest request) {
        SessionState session = sessionService.createSession(request);

        // Initial diagnostic step
        TroubleshootResponse response = new TroubleshootResponse(
                session.getSessionId(),
                request.getBrand(),
                request.getModel(),
                request.getCategory(),
                1,
                "Check Background Usage and Power Consumers",
                "Go to Settings → Battery → Background usage limits. Inspect apps actively running in the background and put unused high-drain apps to sleep.",
                "High power consumption in " + request.getModel() + " is frequently caused by unoptimized background apps constantly running CPU wake locks.",
                false,
                "IN_PROGRESS",
                null
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/troubleshoot")
    public ResponseEntity<TroubleshootResponse> troubleshoot(@Valid @RequestBody TroubleshootStepRequest request) {
        SessionState session = sessionService.getSession(request.getSessionId());

        if ("SOLVED".equalsIgnoreCase(request.getFeedback())) {
            sessionService.recordStepResult(request.getSessionId(), "Step " + session.getCurrentStepNumber(), "SOLVED");
            TroubleshootResponse response = new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    session.getCurrentStepNumber(),
                    "Problem Successfully Resolved!",
                    "Your device settings have resolved the issue.",
                    "The troubleshooting procedure was successful.",
                    true,
                    "SOLVED",
                    "Issue resolved! Maintain your current settings and keep system software up to date."
            );
            return ResponseEntity.ok(response);
        }

        // When NOT_SOLVED, advance to next step
        sessionService.recordStepResult(request.getSessionId(), "Step " + session.getCurrentStepNumber(), "NOT_SOLVED");
        int nextStep = session.getCurrentStepNumber();

        if (nextStep >= 4) {
            // Threshold reached: recommend professional brand service
            TroubleshootResponse response = new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    nextStep,
                    "Escalate to Authorized Service Center",
                    "All safe software troubleshooting procedures have been completed without resolving the problem.",
                    "Persistent symptoms after clearing caches, resetting configurations, and testing safe mode suggest a hardware or component-level fault.",
                    true,
                    "ESCALATED",
                    "We recommend scheduling an appointment at an official " + session.getBrand() + " Authorized Service Center. Do not attempt hardware disassembly."
            );
            return ResponseEntity.ok(response);
        }

        // Return Step 2 or 3
        TroubleshootResponse response = new TroubleshootResponse(
                session.getSessionId(),
                session.getBrand(),
                session.getModel(),
                session.getCategory(),
                nextStep,
                "Clear System Cache & Reset Problem Subsystem",
                "Open Settings → General management → Reset → Reset network settings (or clear partition cache from recovery). Then restart the device.",
                "Stale cache files often cause sync loops and excessive battery/system drain on " + session.getBrand() + " devices.",
                false,
                "IN_PROGRESS",
                null
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionState> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }
}
