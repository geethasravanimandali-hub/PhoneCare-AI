package com.phonecare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootOutcome;
import com.phonecare.model.TroubleshootResponse;
import com.phonecare.model.TroubleshootStepRequest;
import com.phonecare.model.TroubleshootingAttempt;
import com.phonecare.service.AiAgentService;
import com.phonecare.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OutcomeBasedTroubleshootingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private AiAgentService aiAgentService;

    @Test
    void testSessionCreationWithAndroidVersionAndMetadata() {
        DiagnosisRequest req = new DiagnosisRequest(
                "Samsung", "Galaxy S24", "Android 14", "Battery draining", "Battery drains quickly while on standby"
        );
        SessionState session = sessionService.createSession(req);

        assertNotNull(session.getSessionId());
        assertEquals("Samsung", session.getBrand());
        assertEquals("Galaxy S24", session.getModel());
        assertEquals("Android 14", session.getAndroidVersion());
        assertEquals("Battery draining", session.getCategory());
        assertEquals("IN_PROGRESS", session.getStatus());
        assertTrue(session.getAttempts().isEmpty());
    }

    @Test
    void testOutcomeTrackingSuccessfulResolution() {
        DiagnosisRequest req = new DiagnosisRequest(
                "Google Pixel", "Pixel 8", "Android 14", "Wi-Fi problems", "Wi-Fi toggle switch is disabled"
        );
        SessionState session = sessionService.createSession(req);

        // Step 1
        TroubleshootResponse step1 = aiAgentService.generateStep(session, null);
        assertNotNull(step1.getTitle());

        // User reports SUCCESSFUL outcome
        sessionService.recordStepAttempt(
                session.getSessionId(),
                1,
                step1.getTitle(),
                step1.getInstruction(),
                step1.getSuspectedCause(),
                true,
                TroubleshootOutcome.SUCCESSFUL,
                "Network reset immediately fixed the Wi-Fi toggle",
                step1.getEvidenceSource(),
                step1.getConfidence()
        );

        assertEquals("SOLVED", session.getStatus());
        assertEquals(1, session.getAttempts().size());

        TroubleshootingAttempt recordedAttempt = session.getAttempts().get(0);
        assertTrue(recordedAttempt.isAttempted());
        assertEquals(TroubleshootOutcome.SUCCESSFUL, recordedAttempt.getOutcome());
        assertEquals("Network reset immediately fixed the Wi-Fi toggle", recordedAttempt.getUserObservation());

        // Resolution response
        TroubleshootResponse resolution = aiAgentService.generateStep(session, "SUCCESSFUL");
        assertEquals("SOLVED", resolution.getStatus());
        assertTrue(resolution.isFinalStep());
        assertEquals(1.0, resolution.getConfidence());
    }

    @Test
    void testOutcomeTrackingPartiallySuccessfulAdvancesStep() {
        DiagnosisRequest req = new DiagnosisRequest(
                "OnePlus", "OnePlus 12", "Android 14", "Phone overheating", "Phone gets warm during gaming sessions"
        );
        SessionState session = sessionService.createSession(req);

        TroubleshootResponse step1 = aiAgentService.generateStep(session, null);
        assertNotNull(step1.getTitle());

        // Record partially successful attempt
        sessionService.recordStepAttempt(
                session.getSessionId(),
                1,
                step1.getTitle(),
                step1.getInstruction(),
                step1.getSuspectedCause(),
                true,
                TroubleshootOutcome.PARTIALLY_SUCCESSFUL,
                "Cooled down slightly after removing case, but still warm under heavy load",
                step1.getEvidenceSource(),
                step1.getConfidence()
        );

        assertEquals("IN_PROGRESS", session.getStatus());
        assertEquals(2, session.getCurrentStepNumber());

        TroubleshootResponse step2 = aiAgentService.generateStep(session, "PARTIALLY_SUCCESSFUL");
        assertEquals(2, step2.getStepNumber());
        assertNotEquals(step1.getTitle(), step2.getTitle());
    }

    @Test
    void testPreventRepeatingFailedRecommendations() {
        DiagnosisRequest req = new DiagnosisRequest(
                "Motorola", "Edge 50 Pro", "Android 14", "Charging problems", "Phone charges very slowly with USB-C cable"
        );
        SessionState session = sessionService.createSession(req);

        TroubleshootResponse step1 = aiAgentService.generateStep(session, null);
        String step1Title = step1.getTitle();

        // Mark step 1 as UNSUCCESSFUL
        sessionService.recordStepAttempt(
                session.getSessionId(),
                1,
                step1Title,
                step1.getInstruction(),
                step1.getSuspectedCause(),
                true,
                TroubleshootOutcome.UNSUCCESSFUL,
                "Tried new cable, still charging at 2W",
                step1.getEvidenceSource(),
                step1.getConfidence()
        );

        // Step 2 must never repeat Step 1
        TroubleshootResponse step2 = aiAgentService.generateStep(session, "UNSUCCESSFUL");
        assertNotEquals(step1Title, step2.getTitle());
        assertFalse(session.hasAttemptedTitle(step2.getTitle()));
    }

    @Test
    void testAdaptiveClarificationForUnderspecifiedSymptom() {
        // Ambiguous description with no context
        DiagnosisRequest ambiguousReq = new DiagnosisRequest(
                "Samsung", "Galaxy S23", "Android 14", "Phone overheating", "my phone gets very hot"
        );
        SessionState session = sessionService.createSession(ambiguousReq);

        TroubleshootResponse response = aiAgentService.generateStep(session, null);
        assertNotNull(response.getFollowUpQuestion());
        assertFalse(response.getFollowUpOptions().isEmpty());
        assertTrue(response.getFollowUpQuestion().contains("conditions does your phone overheat"));
        assertEquals("INFERRED", response.getEvidenceType());
    }

    @Test
    void testSpecificSymptomBypassesClarification() {
        // Specific description with context (charging)
        DiagnosisRequest specificReq = new DiagnosisRequest(
                "Samsung", "Galaxy S23", "Android 14", "Phone overheating", "phone gets hot while charging on fast charger"
        );
        SessionState session = sessionService.createSession(specificReq);

        TroubleshootResponse response = aiAgentService.generateStep(session, null);
        assertNull(response.getFollowUpQuestion());
        assertNotNull(response.getInstruction());
        assertEquals("VERIFIED", response.getEvidenceType());
        assertNotNull(response.getSuspectedCause());
    }

    @Test
    void testRestApiEndpointsEndToEnd() throws Exception {
        DiagnosisRequest req = new DiagnosisRequest(
                "Samsung", "Galaxy S24", "Android 14", "Battery draining", "Rapid battery drain on standby"
        );

        // 1. POST /api/diagnose
        String diagnoseResStr = mockMvc.perform(post("/api/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.suspectedCause").exists())
                .andExpect(jsonPath("$.confidence").exists())
                .andExpect(jsonPath("$.evidenceSource").exists())
                .andExpect(jsonPath("$.evidenceType").value("VERIFIED"))
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(diagnoseResStr).get("sessionId").asText();

        // 2. POST /api/troubleshoot with detailed outcome
        TroubleshootStepRequest stepReq = new TroubleshootStepRequest(
                sessionId, "NOT_SOLVED", 1, true, "UNSUCCESSFUL", "Did not help standby drain"
        );

        mockMvc.perform(post("/api/troubleshoot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stepReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepNumber").value(2))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 3. GET /api/session/{sessionId}/history
        mockMvc.perform(get("/api/session/" + sessionId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].outcome").value("UNSUCCESSFUL"))
                .andExpect(jsonPath("$[0].attempted").value(true))
                .andExpect(jsonPath("$[0].userObservation").value("Did not help standby drain"));

        // 4. GET /api/sessions
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
