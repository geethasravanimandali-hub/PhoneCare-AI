package com.phonecare.controller;

import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.MetadataResponse;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootResponse;
import com.phonecare.model.TroubleshootStepRequest;
import com.phonecare.service.AiAgentService;
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
    private final AiAgentService aiAgentService;

    public TroubleshootingController(SessionService sessionService, AiAgentService aiAgentService) {
        this.sessionService = sessionService;
        this.aiAgentService = aiAgentService;
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
        TroubleshootResponse response = aiAgentService.generateStep(session, null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/troubleshoot")
    public ResponseEntity<TroubleshootResponse> troubleshoot(@Valid @RequestBody TroubleshootStepRequest request) {
        SessionState session = sessionService.getSession(request.getSessionId());

        sessionService.recordStepResult(request.getSessionId(), "Step " + session.getCurrentStepNumber(), request.getFeedback());

        TroubleshootResponse response = aiAgentService.generateStep(session, request.getFeedback());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionState> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }
}
