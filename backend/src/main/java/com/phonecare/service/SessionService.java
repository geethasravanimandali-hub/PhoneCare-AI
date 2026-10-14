package com.phonecare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootOutcome;
import com.phonecare.model.TroubleshootingAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Persistent storage directory for verified/completed session records
    private final File sessionStoreDir = new File("data/sessions");

    public SessionService() {
        if (!sessionStoreDir.exists()) {
            boolean created = sessionStoreDir.mkdirs();
            if (created) {
                logger.info("Created session storage directory at: {}", sessionStoreDir.getAbsolutePath());
            }
        }
    }

    public SessionState createSession(DiagnosisRequest request) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        String androidVersion = request.getAndroidVersion() != null && !request.getAndroidVersion().isBlank()
                ? request.getAndroidVersion().trim()
                : "Unknown";

        SessionState state = new SessionState(
                sessionId,
                request.getBrand(),
                request.getModel(),
                androidVersion,
                request.getCategory(),
                request.getDescription()
        );

        if (request.getDiagnosticObservations() != null && !request.getDiagnosticObservations().isEmpty()) {
            state.getDiagnosticObservations().putAll(request.getDiagnosticObservations());
        }
        if (request.getFollowUpAnswer() != null && !request.getFollowUpAnswer().isBlank()) {
            state.getDiagnosticObservations().put("initial_follow_up", request.getFollowUpAnswer().trim());
        }

        sessions.put(sessionId, state);
        return state;
    }

    public SessionState getSession(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return state;
    }

    public Collection<SessionState> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void recordObservation(String sessionId, String key, String value) {
        SessionState state = getSession(sessionId);
        state.getDiagnosticObservations().put(key, value);
    }

    public void recordStepAttempt(String sessionId,
                                  int stepNumber,
                                  String stepTitle,
                                  String instruction,
                                  String suspectedCause,
                                  boolean attempted,
                                  TroubleshootOutcome outcome,
                                  String userObservation,
                                  String source,
                                  double confidence) {
        SessionState state = getSession(sessionId);

        TroubleshootingAttempt attempt = new TroubleshootingAttempt(
                stepNumber,
                stepTitle,
                instruction,
                suspectedCause,
                attempted,
                outcome,
                userObservation,
                source,
                confidence
        );

        state.addAttempt(attempt);
        state.getCompletedSteps().add(stepTitle);
        state.getStepResults().add(outcome.name());

        if (outcome == TroubleshootOutcome.SUCCESSFUL) {
            state.setStatus("SOLVED");
            persistSession(state);
        } else if (state.getCurrentStepNumber() >= 4 || "ESCALATED".equalsIgnoreCase(state.getStatus())) {
            state.setStatus("ESCALATED");
            persistSession(state);
        } else {
            state.setCurrentStepNumber(state.getCurrentStepNumber() + 1);
        }
    }

    /**
     * Backward-compatible overload for existing calls.
     */
    public void recordStepResult(String sessionId, String stepTitle, String feedback) {
        TroubleshootOutcome outcome = TroubleshootOutcome.fromString(feedback);
        SessionState state = getSession(sessionId);

        recordStepAttempt(
                sessionId,
                state.getCurrentStepNumber(),
                stepTitle,
                "Executed diagnostic step",
                state.getSuspectedCause() != null ? state.getSuspectedCause() : "Standard configuration diagnosis",
                true,
                outcome,
                feedback,
                state.getRecommendationSource() != null ? state.getRecommendationSource() : "GROUNDED_KNOWLEDGE_BASE",
                state.getConfidence() > 0 ? state.getConfidence() : 0.85
        );
    }

    /**
     * Persists completed session records to disk without PII for building verified dataset.
     */
    private void persistSession(SessionState session) {
        try {
            if (!sessionStoreDir.exists()) {
                sessionStoreDir.mkdirs();
            }
            File sessionFile = new File(sessionStoreDir, session.getSessionId() + ".json");
            objectMapper.writeValue(sessionFile, session);
            logger.info("Successfully persisted session {} to {}", session.getSessionId(), sessionFile.getPath());
        } catch (Exception e) {
            logger.warn("Could not persist session {}: {}", session.getSessionId(), e.getMessage());
        }
    }
}
