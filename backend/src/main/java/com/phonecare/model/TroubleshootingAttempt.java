package com.phonecare.model;

import java.time.Instant;

public class TroubleshootingAttempt {

    private int stepNumber;
    private String stepTitle;
    private String instruction;
    private String suspectedCause;
    private boolean attempted;
    private TroubleshootOutcome outcome;
    private String userObservation;
    private String recommendationSource;
    private double confidence;
    private String timestamp;

    public TroubleshootingAttempt() {
        this.timestamp = Instant.now().toString();
        this.attempted = true;
        this.outcome = TroubleshootOutcome.UNKNOWN;
    }

    public TroubleshootingAttempt(int stepNumber, String stepTitle, String instruction,
                                  String suspectedCause, boolean attempted, TroubleshootOutcome outcome,
                                  String userObservation, String recommendationSource, double confidence) {
        this.stepNumber = stepNumber;
        this.stepTitle = stepTitle;
        this.instruction = instruction;
        this.suspectedCause = suspectedCause;
        this.attempted = attempted;
        this.outcome = outcome != null ? outcome : TroubleshootOutcome.UNKNOWN;
        this.userObservation = userObservation;
        this.recommendationSource = recommendationSource;
        this.confidence = confidence;
        this.timestamp = Instant.now().toString();
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getStepTitle() {
        return stepTitle;
    }

    public void setStepTitle(String stepTitle) {
        this.stepTitle = stepTitle;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getSuspectedCause() {
        return suspectedCause;
    }

    public void setSuspectedCause(String suspectedCause) {
        this.suspectedCause = suspectedCause;
    }

    public boolean isAttempted() {
        return attempted;
    }

    public void setAttempted(boolean attempted) {
        this.attempted = attempted;
    }

    public TroubleshootOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(TroubleshootOutcome outcome) {
        this.outcome = outcome;
    }

    public String getUserObservation() {
        return userObservation;
    }

    public void setUserObservation(String userObservation) {
        this.userObservation = userObservation;
    }

    public String getRecommendationSource() {
        return recommendationSource;
    }

    public void setRecommendationSource(String recommendationSource) {
        this.recommendationSource = recommendationSource;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
