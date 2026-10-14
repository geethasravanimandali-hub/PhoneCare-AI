package com.phonecare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.MlPredictionResult;
import com.phonecare.service.DiagnosticMlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DiagnosticMlServiceTest {

    @Autowired
    private DiagnosticMlService mlService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testPortableModelEvaluationBatteryDrain() {
        MlPredictionResult result = mlService.evaluatePureJavaPortableModel(
                "battery drains fast overnight on standby",
                "Samsung",
                "Galaxy S24",
                "14",
                ""
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("Battery draining", result.getPredictedCategory());
        assertFalse(result.isUncertain());
        assertTrue(result.getConfidenceScore() > 0.18);
        assertNotNull(result.getTopCandidates());
        assertFalse(result.getTopCandidates().isEmpty());
    }

    @Test
    void testPortableModelEvaluationWifi() {
        MlPredictionResult result = mlService.evaluatePureJavaPortableModel(
                "wifi keeps dropping and disconnecting from router",
                "Google Pixel",
                "Pixel 8",
                "14",
                ""
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("Wi-Fi problems", result.getPredictedCategory());
        assertFalse(result.isUncertain());
    }

    @Test
    void testOutOfDistributionSymptomUncertainty() {
        MlPredictionResult result = mlService.evaluatePureJavaPortableModel(
                "xyzqwerty foobarbaz nonesuch qwertyuiop",
                "",
                "",
                "",
                ""
        );

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.isUncertain(), "Unfamiliar symptom must be flagged as uncertain");
        assertEquals("LOW", result.getConfidenceLevel());
        assertNotNull(result.getUncertaintyReason());
    }

    @Test
    void testEmptyInputValidation() {
        MlPredictionResult result = mlService.predict("", "Samsung", "S24", "14", "");
        assertNotNull(result);
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.isUncertain());
    }

    @Test
    void testMlPredictEndpoint() throws Exception {
        DiagnosisRequest req = new DiagnosisRequest(
                "Samsung", "Galaxy S24", "Android 14", "Battery draining", "Battery dies fast"
        );

        mockMvc.perform(post("/api/ml/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.predicted_category").isNotEmpty());
    }

    @Test
    void testMlStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/ml/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.portable_model_loaded").value(true));
    }
}
