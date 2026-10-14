package com.phonecare.model;

import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiagnosisRequest {

    @NotBlank(message = "Phone brand is required")
    private String brand;

    @NotBlank(message = "Phone model is required")
    private String model;

    private String androidVersion;

    @NotBlank(message = "Problem category is required")
    private String category;

    @NotBlank(message = "Problem description is required")
    private String description;

    private Map<String, String> diagnosticObservations = new LinkedHashMap<>();

    private String followUpAnswer;

    public DiagnosisRequest() {
    }

    public DiagnosisRequest(String brand, String model, String category, String description) {
        this(brand, model, "Unknown", category, description);
    }

    public DiagnosisRequest(String brand, String model, String androidVersion, String category, String description) {
        this.brand = brand;
        this.model = model;
        this.androidVersion = androidVersion;
        this.category = category;
        this.description = description;
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

    public Map<String, String> getDiagnosticObservations() {
        return diagnosticObservations;
    }

    public void setDiagnosticObservations(Map<String, String> diagnosticObservations) {
        this.diagnosticObservations = diagnosticObservations != null ? diagnosticObservations : new LinkedHashMap<>();
    }

    public String getFollowUpAnswer() {
        return followUpAnswer;
    }

    public void setFollowUpAnswer(String followUpAnswer) {
        this.followUpAnswer = followUpAnswer;
    }
}
