package com.phonecare.model;

import jakarta.validation.constraints.NotBlank;

public class TroubleshootStepRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Feedback is required (SOLVED or NOT_SOLVED)")
    private String feedback;

    public TroubleshootStepRequest() {
    }

    public TroubleshootStepRequest(String sessionId, String feedback) {
        this.sessionId = sessionId;
        this.feedback = feedback;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
