package com.phonecare;

import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootResponse;
import com.phonecare.service.AiAgentService;
import com.phonecare.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiAgentServiceTest {

    @Autowired
    private AiAgentService aiAgentService;

    @Autowired
    private SessionService sessionService;

    @Test
    void testSafetyEscalationWhenPhysicalHazardPresent() {
        DiagnosisRequest hazardReq = new DiagnosisRequest(
                "Samsung", "Galaxy S24", "Battery draining", "The battery is swollen and smells like burning"
        );
        SessionState session = sessionService.createSession(hazardReq);

        TroubleshootResponse response = aiAgentService.generateStep(session, null);
        assertEquals("ESCALATED", response.getStatus());
        assertTrue(response.isFinalStep());
        assertTrue(response.getTitle().contains("Safety Alert"));
    }

    @Test
    void testSequentialStepGenerationAndNoDuplicates() {
        DiagnosisRequest normalReq = new DiagnosisRequest(
                "Google Pixel", "Pixel 8", "Wi-Fi problems", "Wi-Fi disconnects frequently"
        );
        SessionState session = sessionService.createSession(normalReq);

        // Step 1
        TroubleshootResponse step1 = aiAgentService.generateStep(session, null);
        assertEquals(1, step1.getStepNumber());
        assertEquals("IN_PROGRESS", step1.getStatus());
        assertFalse(step1.isFinalStep());

        // Step 2 (Feedback: NOT_SOLVED)
        sessionService.recordStepResult(session.getSessionId(), step1.getTitle(), "NOT_SOLVED");
        TroubleshootResponse step2 = aiAgentService.generateStep(session, "NOT_SOLVED");
        assertEquals(2, step2.getStepNumber());
        assertNotEquals(step1.getTitle(), step2.getTitle()); // Must not repeat step 1
    }

    @Test
    void testResolutionWhenUserClicksSolved() {
        DiagnosisRequest req = new DiagnosisRequest(
                "OnePlus", "OnePlus 12", "Phone lagging", "Phone stutters during scrolling"
        );
        SessionState session = sessionService.createSession(req);

        TroubleshootResponse response = aiAgentService.generateStep(session, "SOLVED");
        assertEquals("SOLVED", response.getStatus());
        assertTrue(response.isFinalStep());
    }
}
