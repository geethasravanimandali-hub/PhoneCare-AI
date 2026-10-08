package com.phonecare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode kbRoot;

    @PostConstruct
    public void loadKnowledgeBase() {
        try {
            ClassPathResource resource = new ClassPathResource("knowledge/troubleshooting-kb.json");
            try (InputStream inputStream = resource.getInputStream()) {
                kbRoot = objectMapper.readTree(inputStream);
                logger.info("Knowledge Base loaded successfully with {} categories",
                        kbRoot.path("categories").size());
            }
        } catch (Exception e) {
            logger.error("Failed to load troubleshooting knowledge base JSON", e);
            throw new RuntimeException("Knowledge base initialization failed", e);
        }
    }

    public JsonNode getCategoryNode(String categoryName) {
        if (kbRoot == null || categoryName == null) {
            return null;
        }
        for (JsonNode cat : kbRoot.path("categories")) {
            if (categoryName.equalsIgnoreCase(cat.path("name").asText()) ||
                categoryName.equalsIgnoreCase(cat.path("id").asText())) {
                return cat;
            }
        }
        return null;
    }

    public String getBrandPath(String categoryName, String brand) {
        JsonNode catNode = getCategoryNode(categoryName);
        if (catNode != null && brand != null) {
            JsonNode paths = catNode.path("brandSpecificPaths");
            if (paths.has(brand)) {
                return paths.get(brand).asText();
            }
        }
        return "Settings → System / Device Settings";
    }

    public JsonNode getStep(String categoryName, int stepNumber) {
        JsonNode catNode = getCategoryNode(categoryName);
        if (catNode != null) {
            for (JsonNode step : catNode.path("safeSteps")) {
                if (step.path("step").asInt() == stepNumber) {
                    return step;
                }
            }
        }
        return null;
    }

    public boolean hasSafetyHazard(String categoryName, String userDescription) {
        if (userDescription == null) return false;
        String lowerDesc = userDescription.toLowerCase();

        // Universal critical hardware hazards
        List<String> universalTriggers = List.of(
                "smoke", "fire", "burning", "sparks", "swollen", "swelling",
                "bulging", "melted", "water damaged", "liquid damage"
        );
        for (String trigger : universalTriggers) {
            if (lowerDesc.contains(trigger)) {
                return true;
            }
        }

        // Category specific triggers
        JsonNode catNode = getCategoryNode(categoryName);
        if (catNode != null) {
            for (JsonNode trigger : catNode.path("safetyEscalation").path("triggers")) {
                if (lowerDesc.contains(trigger.asText().toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getSafetyAdvice(String categoryName) {
        JsonNode catNode = getCategoryNode(categoryName);
        if (catNode != null && catNode.has("safetyEscalation")) {
            return catNode.path("safetyEscalation").path("advice").asText();
        }
        return "A critical hardware risk has been detected. Please disconnect the device and take it immediately to an authorized brand service center.";
    }
}
