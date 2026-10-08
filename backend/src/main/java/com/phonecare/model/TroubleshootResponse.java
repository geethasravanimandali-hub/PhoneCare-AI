package com.phonecare.model;

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
    private String status; // "IN_PROGRESS", "SOLVED", "ESCALATED"
    private String finalRecommendation;

    public TroubleshootResponse() {
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
}
