package com.phonecare;

import com.phonecare.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KnowledgeBaseServiceTest {

    @Autowired
    private KnowledgeBaseService kbService;

    @Test
    void testCategoriesLoaded() {
        assertNotNull(kbService.getCategoryNode("Battery draining"));
        assertNotNull(kbService.getCategoryNode("Phone overheating"));
        assertNotNull(kbService.getCategoryNode("Wi-Fi problems"));
        assertNotNull(kbService.getCategoryNode("Charging problems"));
    }

    @Test
    void testBrandSpecificPath() {
        String samsungPath = kbService.getBrandPath("Battery draining", "Samsung");
        assertTrue(samsungPath.contains("Settings → Battery"));

        String pixelPath = kbService.getBrandPath("Battery draining", "Google Pixel");
        assertTrue(pixelPath.contains("Adaptive Battery"));
    }

    @Test
    void testSafetyHazardDetection() {
        boolean hazard = kbService.hasSafetyHazard("Battery draining", "The battery is swollen and smelling like smoke");
        assertTrue(hazard);

        boolean normal = kbService.hasSafetyHazard("Battery draining", "The battery drains fast when watching YouTube");
        assertFalse(normal);
    }
}
