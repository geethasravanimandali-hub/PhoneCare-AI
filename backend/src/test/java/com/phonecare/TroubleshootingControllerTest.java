package com.phonecare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.TroubleshootStepRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TroubleshootingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetMetadataReturnsBrandsAndCategories() throws Exception {
        mockMvc.perform(get("/api/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.brandsWithModels.Samsung").isArray());
    }

    @Test
    void testStaticIndexHtmlServesAdaptiveUiElements() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("android-select")))
                .andExpect(content().string(containsString("clarification-container")))
                .andExpect(content().string(containsString("btn-partially-solved")))
                .andExpect(content().string(containsString("step-observation-input")))
                .andExpect(content().string(containsString("attempt-history-card")));
    }

    @Test
    void testDiagnoseFlow() throws Exception {
        DiagnosisRequest request = new DiagnosisRequest(
                "Samsung",
                "Galaxy S24",
                "Android 14",
                "Battery draining",
                "Battery drains quickly during standby"
        );

        String response = mockMvc.perform(post("/api/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.stepNumber").value(1))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.evidenceType").value("VERIFIED"))
                .andReturn().getResponse().getContentAsString();

        // Extract session ID
        String sessionId = objectMapper.readTree(response).get("sessionId").asText();

        // Test step 2 (not solved with observation)
        TroubleshootStepRequest stepReq = new TroubleshootStepRequest(
                sessionId, "NOT_SOLVED", 1, true, "UNSUCCESSFUL", "Standby drain continues"
        );
        mockMvc.perform(post("/api/troubleshoot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stepReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepNumber").value(2))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.attemptHistory").isArray());
    }

    @Test
    void testAdaptiveClarificationWorkflow() throws Exception {
        // 1. Ambiguous description triggers clarification question
        DiagnosisRequest ambiguousReq = new DiagnosisRequest(
                "Google Pixel",
                "Pixel 8",
                "Android 14",
                "Phone overheating",
                "Phone gets very hot"
        );

        String diagnoseRes = mockMvc.perform(post("/api/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ambiguousReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followUpQuestion").exists())
                .andExpect(jsonPath("$.followUpOptions").isArray())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(diagnoseRes).get("sessionId").asText();

        // 2. Submit user's selected clarification condition
        TroubleshootStepRequest clarifyReq = new TroubleshootStepRequest();
        clarifyReq.setSessionId(sessionId);
        clarifyReq.setFeedback("CLARIFICATION_ANSWER");
        clarifyReq.setFollowUpAnswer("While charging with cable or wireless pad");

        mockMvc.perform(post("/api/troubleshoot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clarifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.instruction").exists())
                .andExpect(jsonPath("$.suspectedCause").exists())
                .andExpect(jsonPath("$.confidence").exists());
    }
}
