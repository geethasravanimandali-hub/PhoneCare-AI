package com.phonecare;

import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.ProblemValidationResponse;
import com.phonecare.service.KnowledgeBaseService;
import com.phonecare.service.ProblemValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProblemValidationServiceTest {

    @Autowired
    private ProblemValidationService validationService;

    @Autowired
    private KnowledgeBaseService kbService;

    @Test
    void testCategoryMatch() {
        DiagnosisRequest req = new DiagnosisRequest(
                "Samsung", "Galaxy S24", "Battery draining", "My battery drains extremely fast while idle"
        );
        ProblemValidationResponse response = validationService.validate(req);
        assertEquals("MATCH", response.getOutcome());
        assertEquals("Battery draining", response.getSelectedCategory());
        assertFalse(response.isSafetyPriority());
    }

    @Test
    void testCategoryMismatch() {
        DiagnosisRequest req = new DiagnosisRequest(
                "Samsung", "Galaxy S24", "Battery draining", "My Wi-Fi keeps disconnecting from the home router"
        );
        ProblemValidationResponse response = validationService.validate(req);
        assertEquals("MISMATCH", response.getOutcome());
        assertEquals("Wi-Fi problems", response.getSuggestedCategory());
        assertFalse(response.isSafetyPriority());
    }

    @Test
    void testMultipleProblemsDetected() {
        DiagnosisRequest req = new DiagnosisRequest(
                "Google Pixel", "Pixel 8", "Battery draining", "My phone is overheating and battery drains fast"
        );
        ProblemValidationResponse response = validationService.validate(req);
        assertEquals("MULTIPLE_PROBLEMS", response.getOutcome());
        assertTrue(response.getDetectedCategories().size() >= 2);
    }

    @Test
    void testUnclearDescription() {
        DiagnosisRequest req = new DiagnosisRequest(
                "OnePlus", "OnePlus 12", "Phone lagging", "it is broken please fix it"
        );
        ProblemValidationResponse response = validationService.validate(req);
        assertEquals("UNCLEAR", response.getOutcome());
        assertTrue(response.getMessage().contains("too brief or unclear"));
    }

    @Test
    void testSafetyPriorityOverridesValidation() {
        DiagnosisRequest req = new DiagnosisRequest(
                "Xiaomi", "Xiaomi 14", "Charging problems", "The battery is swollen and smells like smoke"
        );
        ProblemValidationResponse response = validationService.validate(req);
        assertEquals("MATCH", response.getOutcome());
        assertTrue(response.isSafetyPriority());
        assertTrue(response.getMessage().contains("Safety hazard detected"));
    }
}
