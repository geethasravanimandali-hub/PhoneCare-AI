package com.phonecare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MlPredictionResult {

    private String status; // "SUCCESS" or "ERROR"

    @JsonProperty("predicted_category")
    private String predictedCategory;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("confidence_level")
    private String confidenceLevel; // "HIGH", "MEDIUM", "LOW"

    @JsonProperty("is_uncertain")
    private boolean isUncertain;

    @JsonProperty("uncertainty_reason")
    private String uncertaintyReason;

    @JsonProperty("model_type")
    private String modelType;

    @JsonProperty("vocabulary_overlap_ratio")
    private Double vocabularyOverlapRatio;

    @JsonProperty("diagnostic_advice")
    private String diagnosticAdvice;

    @JsonProperty("top_candidates")
    private List<Map<String, Object>> topCandidates = new ArrayList<>();

    public MlPredictionResult() {
    }

    public MlPredictionResult(String status, String predictedCategory, Double confidenceScore,
                              String confidenceLevel, boolean isUncertain, String uncertaintyReason,
                              String modelType) {
        this.status = status;
        this.predictedCategory = predictedCategory;
        this.confidenceScore = confidenceScore;
        this.confidenceLevel = confidenceLevel;
        this.isUncertain = isUncertain;
        this.uncertaintyReason = uncertaintyReason;
        this.modelType = modelType;
    }

    public static MlPredictionResult error(String message) {
        MlPredictionResult res = new MlPredictionResult();
        res.setStatus("ERROR");
        res.setIsUncertain(true);
        res.setConfidenceLevel("LOW");
        res.setUncertaintyReason(message);
        res.setConfidenceScore(0.0);
        return res;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public void setPredictedCategory(String predictedCategory) {
        this.predictedCategory = predictedCategory;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public boolean isUncertain() {
        return isUncertain;
    }

    public void setIsUncertain(boolean uncertain) {
        isUncertain = uncertain;
    }

    public String getUncertaintyReason() {
        return uncertaintyReason;
    }

    public void setUncertaintyReason(String uncertaintyReason) {
        this.uncertaintyReason = uncertaintyReason;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Double getVocabularyOverlapRatio() {
        return vocabularyOverlapRatio;
    }

    public void setVocabularyOverlapRatio(Double vocabularyOverlapRatio) {
        this.vocabularyOverlapRatio = vocabularyOverlapRatio;
    }

    public String getDiagnosticAdvice() {
        return diagnosticAdvice;
    }

    public void setDiagnosticAdvice(String diagnosticAdvice) {
        this.diagnosticAdvice = diagnosticAdvice;
    }

    public List<Map<String, Object>> getTopCandidates() {
        return topCandidates;
    }

    public void setTopCandidates(List<Map<String, Object>> topCandidates) {
        this.topCandidates = topCandidates != null ? topCandidates : new ArrayList<>();
    }
}
