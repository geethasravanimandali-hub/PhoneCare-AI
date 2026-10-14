package com.phonecare.model;

import java.util.ArrayList;
import java.util.List;

public class TroubleshootResponse {

    private String sessionId;
    private String brand;
    private String model;
    private String category;
    private int stepNumber;
    private String title;
    private String instruction;
    private String explanation;
    private boolean isFinalStep;
    private String status; // "IN_PROGRESS", "SOLVED", "ESCALATED", "NEEDS_CLARIFICATION"
    private String finalRecommendation;

    // Outcome-Based & Adaptive Diagnostics Enhancements
    private String suspectedCause;
    private Double confidence;
    private String evidenceSource;
    private String evidenceType; // "VERIFIED", "INFERRED", "UNKNOWN"
    private String followUpQuestion;
    private List<String> followUpOptions;
    private List<TroubleshootingAttempt> attemptHistory;

    public TroubleshootResponse() {
        this.followUpOptions = new ArrayList<>();
        this.attemptHistory = new ArrayList<>();
    }

    public TroubleshootResponse(String sessionId, String brand, String model, String category,
                                int stepNumber, String title, String instruction, String explanation,
                                boolean isFinalStep, String status, String finalRecommendation) {
        this.sessionId = sessionId;
        this.brand = brand;
        this.model = model;
        this.category = category;
        this.stepNumber = stepNumber;
        this.title = title;
        this.instruction = instruction;
        this.explanation = explanation;
        this.isFinalStep = isFinalStep;
        this.status = status;
        this.finalRecommendation = finalRecommendation;
        this.followUpOptions = new ArrayList<>();
        this.attemptHistory = new ArrayList<>();
        this.evidenceType = "VERIFIED";
        this.confidence = 0.85;
    }

    public TroubleshootResponse(String sessionId, String brand, String model, String category,
                                int stepNumber, String title, String instruction, String explanation,
                                boolean isFinalStep, String status, String finalRecommendation,
                                String suspectedCause, Double confidence, String evidenceSource,
                                String evidenceType) {
        this(sessionId, brand, model, category, stepNumber, title, instruction, explanation,
                isFinalStep, status, finalRecommendation);
        this.suspectedCause = suspectedCause;
        this.confidence = confidence;
        this.evidenceSource = evidenceSource;
        this.evidenceType = evidenceType;
    }

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isFinalStep() {
        return isFinalStep;
    }

    public void setFinalStep(boolean finalStep) {
        isFinalStep = finalStep;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFinalRecommendation() {
        return finalRecommendation;
    }

    public void setFinalRecommendation(String finalRecommendation) {
        this.finalRecommendation = finalRecommendation;
    }

    public String getSuspectedCause() {
        return suspectedCause;
    }

    public void setSuspectedCause(String suspectedCause) {
        this.suspectedCause = suspectedCause;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getEvidenceSource() {
        return evidenceSource;
    }

    public void setEvidenceSource(String evidenceSource) {
        this.evidenceSource = evidenceSource;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getFollowUpQuestion() {
        return followUpQuestion;
    }

    public void setFollowUpQuestion(String followUpQuestion) {
        this.followUpQuestion = followUpQuestion;
    }

    public List<String> getFollowUpOptions() {
        return followUpOptions;
    }

    public void setFollowUpOptions(List<String> followUpOptions) {
        this.followUpOptions = followUpOptions;
    }

    public List<TroubleshootingAttempt> getAttemptHistory() {
        return attemptHistory;
    }

    public void setAttemptHistory(List<TroubleshootingAttempt> attemptHistory) {
        this.attemptHistory = attemptHistory;
    }
}
