package com.phonecare.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SessionState {

    private String sessionId;
    private String brand;
    private String model;
    private String androidVersion;
    private String category;
    private String description;
    private int currentStepNumber;
    private List<String> completedSteps;
    private List<String> stepResults;
    private String status; // "IN_PROGRESS", "SOLVED", "ESCALATED"

    // Outcome-Based & Adaptive Diagnostics Tracking
    private String suspectedCause;
    private String recommendationSource;
    private double confidence;
    private Map<String, String> diagnosticObservations;
    private String pendingFollowUpQuestion;
    private List<String> pendingFollowUpOptions;
    private List<TroubleshootingAttempt> attempts;
    private String createdAt;
    private String updatedAt;

    public SessionState() {
        this.completedSteps = new ArrayList<>();
        this.stepResults = new ArrayList<>();
        this.diagnosticObservations = new LinkedHashMap<>();
        this.pendingFollowUpOptions = new ArrayList<>();
        this.attempts = new ArrayList<>();
        this.createdAt = Instant.now().toString();
        this.updatedAt = this.createdAt;
    }

    public SessionState(String sessionId, String brand, String model, String category, String description) {
        this(sessionId, brand, model, "Unknown", category, description);
    }

    public SessionState(String sessionId, String brand, String model, String androidVersion,
                        String category, String description) {
        this.sessionId = sessionId;
        this.brand = brand;
        this.model = model;
        this.androidVersion = (androidVersion != null && !androidVersion.isBlank()) ? androidVersion : "Unknown";
        this.category = category;
        this.description = description;
        this.currentStepNumber = 1;
        this.completedSteps = new ArrayList<>();
        this.stepResults = new ArrayList<>();
        this.status = "IN_PROGRESS";
        this.diagnosticObservations = new LinkedHashMap<>();
        this.pendingFollowUpOptions = new ArrayList<>();
        this.attempts = new ArrayList<>();
        this.createdAt = Instant.now().toString();
        this.updatedAt = this.createdAt;
        this.confidence = 0.85;
    }

    public boolean hasAttemptedTitle(String title) {
        if (title == null) return false;
        for (TroubleshootingAttempt attempt : attempts) {
            if (title.equalsIgnoreCase(attempt.getStepTitle())) {
                return true;
            }
        }
        for (String step : completedSteps) {
            if (title.equalsIgnoreCase(step)) {
                return true;
            }
        }
        return false;
    }

    public void addAttempt(TroubleshootingAttempt attempt) {
        this.attempts.add(attempt);
        this.updatedAt = Instant.now().toString();
    }

    public TroubleshootingAttempt getLatestAttempt() {
        if (attempts.isEmpty()) return null;
        return attempts.get(attempts.size() - 1);
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public void setAndroidVersion(String androidVersion) {
        this.androidVersion = androidVersion;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCurrentStepNumber() {
        return currentStepNumber;
    }

    public void setCurrentStepNumber(int currentStepNumber) {
        this.currentStepNumber = currentStepNumber;
        this.updatedAt = Instant.now().toString();
    }

    public List<String> getCompletedSteps() {
        return completedSteps;
    }

    public void setCompletedSteps(List<String> completedSteps) {
        this.completedSteps = completedSteps;
    }

    public List<String> getStepResults() {
        return stepResults;
    }

    public void setStepResults(List<String> stepResults) {
        this.stepResults = stepResults;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now().toString();
    }

    public String getSuspectedCause() {
        return suspectedCause;
    }

    public void setSuspectedCause(String suspectedCause) {
        this.suspectedCause = suspectedCause;
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

    public Map<String, String> getDiagnosticObservations() {
        return diagnosticObservations;
    }

    public void setDiagnosticObservations(Map<String, String> diagnosticObservations) {
        this.diagnosticObservations = diagnosticObservations;
    }

    public String getPendingFollowUpQuestion() {
        return pendingFollowUpQuestion;
    }

    public void setPendingFollowUpQuestion(String pendingFollowUpQuestion) {
        this.pendingFollowUpQuestion = pendingFollowUpQuestion;
    }

    public List<String> getPendingFollowUpOptions() {
        return pendingFollowUpOptions;
    }

    public void setPendingFollowUpOptions(List<String> pendingFollowUpOptions) {
        this.pendingFollowUpOptions = pendingFollowUpOptions;
    }

    public List<TroubleshootingAttempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<TroubleshootingAttempt> attempts) {
        this.attempts = attempts;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
