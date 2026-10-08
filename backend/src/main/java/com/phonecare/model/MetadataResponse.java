package com.phonecare.model;

import java.util.List;
import java.util.Map;

public class MetadataResponse {

    private Map<String, List<String>> brandsWithModels;
    private List<String> categories;

    public MetadataResponse() {
    }

    public MetadataResponse(Map<String, List<String>> brandsWithModels, List<String> categories) {
        this.brandsWithModels = brandsWithModels;
        this.categories = categories;
    }

    public Map<String, List<String>> getBrandsWithModels() {
        return brandsWithModels;
    }

    public void setBrandsWithModels(Map<String, List<String>> brandsWithModels) {
        this.brandsWithModels = brandsWithModels;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }
}
