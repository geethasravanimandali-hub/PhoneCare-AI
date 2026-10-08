package com.phonecare.model;

import java.util.ArrayList;
import java.util.List;

public class SessionState {

    private String sessionId;
    private String brand;
    private String model;
    private String category;
    private String description;
    private int currentStepNumber;
    private List<String> completedSteps;
    private List<String> stepResults;
    private String status; // "IN_PROGRESS", "SOLVED", "ESCALATED"

    public SessionState() {
        this.completedSteps = new ArrayList<>();
        this.stepResults = new ArrayList<>();
    }

    public SessionState(String sessionId, String brand, String model, String category, String description) {
        this.sessionId = sessionId;
        this.brand = brand;
        this.model = model;
        this.category = category;
        this.description = description;
        this.currentStepNumber = 1;
        this.completedSteps = new ArrayList<>();
        this.stepResults = new ArrayList<>();
        this.status = "IN_PROGRESS";
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
    }
}
