package com.phonecare.model;

import java.util.ArrayList;
import java.util.List;

public class ProblemValidationResponse {

    private String outcome; // "MATCH", "MISMATCH", "MULTIPLE_PROBLEMS", "UNCLEAR"
    private String selectedCategory;
    private List<String> detectedCategories;
    private String suggestedCategory;
    private String message;
    private boolean safetyPriority;

    public ProblemValidationResponse() {
        this.detectedCategories = new ArrayList<>();
    }

    public ProblemValidationResponse(String outcome, String selectedCategory, List<String> detectedCategories,
                                     String suggestedCategory, String message, boolean safetyPriority) {
        this.outcome = outcome;
        this.selectedCategory = selectedCategory;
        this.detectedCategories = detectedCategories != null ? detectedCategories : new ArrayList<>();
        this.suggestedCategory = suggestedCategory;
        this.message = message;
        this.safetyPriority = safetyPriority;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(String selectedCategory) {
        this.selectedCategory = selectedCategory;
    }

    public List<String> getDetectedCategories() {
        return detectedCategories;
    }

    public void setDetectedCategories(List<String> detectedCategories) {
        this.detectedCategories = detectedCategories;
    }

    public String getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(String suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSafetyPriority() {
        return safetyPriority;
    }

    public void setSafetyPriority(boolean safetyPriority) {
        this.safetyPriority = safetyPriority;
    }
}
