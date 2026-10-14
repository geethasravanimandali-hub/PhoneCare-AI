package com.phonecare.controller;

import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.MetadataResponse;
import com.phonecare.model.ProblemValidationResponse;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootOutcome;
import com.phonecare.model.TroubleshootResponse;
import com.phonecare.model.TroubleshootStepRequest;
import com.phonecare.model.TroubleshootingAttempt;
import com.phonecare.service.AiAgentService;
import com.phonecare.service.ProblemValidationService;
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
    private final ProblemValidationService validationService;
    private final com.phonecare.service.DiagnosticMlService mlService;

    public TroubleshootingController(SessionService sessionService,
                                     AiAgentService aiAgentService,
                                     ProblemValidationService validationService,
                                     com.phonecare.service.DiagnosticMlService mlService) {
        this.sessionService = sessionService;
        this.aiAgentService = aiAgentService;
        this.validationService = validationService;
        this.mlService = mlService;
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

    @PostMapping({"/validate-problem", "/validate"})
    public ResponseEntity<ProblemValidationResponse> validateProblem(@Valid @RequestBody DiagnosisRequest request) {
        return ResponseEntity.ok(validationService.validate(request));
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

        // If the request contains an answer to a diagnostic clarification question, store observation
        if (request.getFollowUpAnswer() != null && !request.getFollowUpAnswer().isBlank()) {
            sessionService.recordObservation(request.getSessionId(), "user_observation", request.getFollowUpAnswer().trim());
        }

        String outcome = request.getOutcome();
        boolean attempted = request.isAttempted();
        int stepNumber = request.getStepNumber() != null ? request.getStepNumber() : session.getCurrentStepNumber();
        String stepTitle = "Step " + stepNumber;
        if (session.getLatestAttempt() != null && stepNumber == session.getLatestAttempt().getStepNumber()) {
            stepTitle = session.getLatestAttempt().getStepTitle();
        }

        sessionService.recordStepAttempt(
                request.getSessionId(),
                stepNumber,
                stepTitle,
                "Executed diagnostic step",
                session.getSuspectedCause() != null ? session.getSuspectedCause() : "Standard configuration diagnosis",
                attempted,
                TroubleshootOutcome.fromString(outcome),
                request.getUserObservation(),
                session.getRecommendationSource() != null ? session.getRecommendationSource() : "GROUNDED_KNOWLEDGE_BASE",
                session.getConfidence() > 0 ? session.getConfidence() : 0.85
        );

        TroubleshootResponse response = aiAgentService.generateStep(session, outcome);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionState> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @GetMapping("/session/{sessionId}/history")
    public ResponseEntity<List<TroubleshootingAttempt>> getSessionHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId).getAttempts());
    }

    @GetMapping("/sessions")
    public ResponseEntity<Collection<SessionState>> getAllSessions() {
        return ResponseEntity.ok(sessionService.getAllSessions());
    }

    @PostMapping("/ml/predict")
    public ResponseEntity<com.phonecare.model.MlPredictionResult> predictWithMl(@Valid @RequestBody DiagnosisRequest request) {
        com.phonecare.model.MlPredictionResult result = mlService.predict(
                request.getDescription(),
                request.getBrand(),
                request.getModel(),
                request.getAndroidVersion(),
                ""
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ml/status")
    public ResponseEntity<Map<String, Object>> getMlStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ACTIVE");
        status.put("portable_model_loaded", mlService.isPortableModelLoaded());
        status.put("resolved_python", mlService.getResolvedPythonPath());
        return ResponseEntity.ok(status);
    }
}
