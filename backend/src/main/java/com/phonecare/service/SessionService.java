package com.phonecare.service;

import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.SessionState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState createSession(DiagnosisRequest request) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        SessionState state = new SessionState(
                sessionId,
                request.getBrand(),
                request.getModel(),
                request.getCategory(),
                request.getDescription()
        );
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

    public void recordStepResult(String sessionId, String stepTitle, String feedback) {
        SessionState state = getSession(sessionId);
        state.getCompletedSteps().add(stepTitle);
        state.getStepResults().add(feedback);
        if ("SOLVED".equalsIgnoreCase(feedback)) {
            state.setStatus("SOLVED");
        } else {
            state.setCurrentStepNumber(state.getCurrentStepNumber() + 1);
        }
    }
}
