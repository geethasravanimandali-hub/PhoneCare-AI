package com.phonecare.model;

import jakarta.validation.constraints.NotBlank;

public class TroubleshootStepRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Feedback is required (SOLVED, NOT_SOLVED, or detailed outcome)")
    private String feedback;

    private Integer stepNumber;

    private Boolean attempted;

    private String outcome;

    private String userObservation;

    private String followUpAnswer;

    public TroubleshootStepRequest() {
        this.attempted = true;
    }

    public TroubleshootStepRequest(String sessionId, String feedback) {
        this.sessionId = sessionId;
        this.feedback = feedback;
        this.attempted = true;
    }

    public TroubleshootStepRequest(String sessionId, String feedback, Integer stepNumber,
                                   Boolean attempted, String outcome, String userObservation) {
        this.sessionId = sessionId;
        this.feedback = feedback;
        this.stepNumber = stepNumber;
        this.attempted = attempted != null ? attempted : true;
        this.outcome = outcome;
        this.userObservation = userObservation;
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

    public Integer getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(Integer stepNumber) {
        this.stepNumber = stepNumber;
    }

    public Boolean getAttempted() {
        return attempted;
    }

    public boolean isAttempted() {
        return attempted == null || attempted;
    }

    public void setAttempted(Boolean attempted) {
        this.attempted = attempted;
    }

    public String getOutcome() {
        if (outcome != null && !outcome.trim().isEmpty()) {
            return outcome;
        }
        if ("SOLVED".equalsIgnoreCase(feedback)) {
            return "SUCCESSFUL";
        } else if ("NOT_SOLVED".equalsIgnoreCase(feedback)) {
            return "UNSUCCESSFUL";
        }
        return feedback != null ? feedback : "UNKNOWN";
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getUserObservation() {
        return userObservation;
    }

    public void setUserObservation(String userObservation) {
        this.userObservation = userObservation;
    }

    public String getFollowUpAnswer() {
        return followUpAnswer;
    }

    public void setFollowUpAnswer(String followUpAnswer) {
        this.followUpAnswer = followUpAnswer;
    }
}
