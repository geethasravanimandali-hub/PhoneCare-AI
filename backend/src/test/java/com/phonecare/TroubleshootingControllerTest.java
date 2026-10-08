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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void testDiagnoseFlow() throws Exception {
        DiagnosisRequest request = new DiagnosisRequest(
                "Samsung",
                "Galaxy S24",
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
                .andReturn().getResponse().getContentAsString();

        // Extract session ID
        String sessionId = objectMapper.readTree(response).get("sessionId").asText();

        // Test step 2 (not solved)
        TroubleshootStepRequest stepReq = new TroubleshootStepRequest(sessionId, "NOT_SOLVED");
        mockMvc.perform(post("/api/troubleshoot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stepReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepNumber").value(2))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
}
