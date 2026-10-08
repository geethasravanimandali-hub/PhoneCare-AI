package com.phonecare.model;

import jakarta.validation.constraints.NotBlank;

public class DiagnosisRequest {

    @NotBlank(message = "Phone brand is required")
    private String brand;

    @NotBlank(message = "Phone model is required")
    private String model;

    @NotBlank(message = "Problem category is required")
    private String category;

    @NotBlank(message = "Problem description is required")
    private String description;

    public DiagnosisRequest() {
    }

    public DiagnosisRequest(String brand, String model, String category, String description) {
        this.brand = brand;
        this.model = model;
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
}
