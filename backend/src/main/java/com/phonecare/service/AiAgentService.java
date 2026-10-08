package com.phonecare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiAgentService {

    private static final Logger logger = LoggerFactory.getLogger(AiAgentService.class);

    private final KnowledgeBaseService kbService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.model:gemini-1.5-flash}")
    private String modelName;

    public AiAgentService(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    public TroubleshootResponse generateStep(SessionState session, String feedback) {
        // Rule 1: Immediate Safety Hazard Escalation Check
        if (kbService.hasSafetyHazard(session.getCategory(), session.getDescription())) {
            session.setStatus("ESCALATED");
            return new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    session.getCurrentStepNumber(),
                    "Safety Alert: Physical Hazard Detected",
                    "Do NOT attempt further software troubleshooting or charge the device.",
                    "Physical risks (such as battery swelling, liquid intrusion, or burning odors) pose critical chemical or electrical hazards.",
                    true,
                    "ESCALATED",
                    kbService.getSafetyAdvice(session.getCategory())
            );
        }

        // Rule 2: Problem Solved Resolution
        if ("SOLVED".equalsIgnoreCase(feedback)) {
            session.setStatus("SOLVED");
            return new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    session.getCurrentStepNumber(),
                    "Issue Resolved Successfully",
                    "Your smartphone has resumed normal operation.",
                    "The applied configuration change successfully resolved the diagnostic condition.",
                    true,
                    "SOLVED",
                    "Troubleshooting complete. Keep your " + session.getBrand() + " device updated with latest security patches."
            );
        }

        // Rule 3: Escalation Threshold (Max safe steps reached)
        int stepNumber = session.getCurrentStepNumber();
        if (stepNumber >= 4) {
            session.setStatus("ESCALATED");
            return new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    stepNumber,
                    "Escalate to Authorized Service Center",
                    "All safe, non-invasive software troubleshooting procedures have been exhausted.",
                    "Persistent symptoms despite system-level configuration resets indicate a hardware component fault on your " + session.getModel() + ".",
                    true,
                    "ESCALATED",
                    "We recommend booking an appointment at an official " + session.getBrand() + " Authorized Service Center. Do not attempt to open the device."
            );
        }

        // Rule 4: Try LLM Call if API Key is Present
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                TroubleshootResponse aiResponse = callGeminiLlm(session);
                if (aiResponse != null) {
                    return aiResponse;
                }
            } catch (Exception e) {
                logger.warn("Live AI LLM call failed or timed out: {}. Seamlessly falling back to grounded Knowledge Base.", e.getMessage());
            }
        } else {
            logger.info("AI_API_KEY not configured. Using deterministic Grounded Knowledge Base engine.");
        }

        // Rule 5: Fallback to Grounded Knowledge Base Engine
        return generateGroundedKbStep(session, stepNumber);
    }

    private TroubleshootResponse callGeminiLlm(SessionState session) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey.trim();

        String brandPath = kbService.getBrandPath(session.getCategory(), session.getBrand());
        String stepsTried = session.getCompletedSteps().isEmpty() ? "None" : String.join("; ", session.getCompletedSteps());

        String prompt = String.format(
                "You are PhoneCare AI. Troubleshoot this Android issue:\n" +
                "Brand: %s\nModel: %s\nCategory: %s\nUser Description: %s\n" +
                "Steps already tried and failed: [%s]\n" +
                "Current Step Number: %d\n" +
                "Brand UI Path Reference: %s\n\n" +
                "Provide ONLY valid JSON (without markdown code fences) matching this structure:\n" +
                "{\"stepNumber\": %d, \"title\": \"...\", \"instruction\": \"...\", \"explanation\": \"...\", \"isFinalStep\": false, \"status\": \"IN_PROGRESS\", \"finalRecommendation\": null}",
                session.getBrand(), session.getModel(), session.getCategory(), session.getDescription(),
                stepsTried, session.getCurrentStepNumber(), brandPath, session.getCurrentStepNumber()
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            // Strip markdown backticks if returned by model
            text = text.replace("```json", "").replace("```", "").trim();
            JsonNode stepJson = objectMapper.readTree(text);

            return new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    stepJson.path("stepNumber").asInt(session.getCurrentStepNumber()),
                    stepJson.path("title").asText("Recommended Action"),
                    stepJson.path("instruction").asText(),
                    stepJson.path("explanation").asText(),
                    stepJson.path("isFinalStep").asBoolean(false),
                    stepJson.path("status").asText("IN_PROGRESS"),
                    stepJson.path("finalRecommendation").isNull() ? null : stepJson.path("finalRecommendation").asText()
            );
        }
        return null;
    }

    private TroubleshootResponse generateGroundedKbStep(SessionState session, int stepNumber) {
        JsonNode kbStep = kbService.getStep(session.getCategory(), stepNumber);
        String brandPath = kbService.getBrandPath(session.getCategory(), session.getBrand());

        String title = "Recommended Diagnostic Step";
        String instruction = "Open Settings on your " + session.getBrand() + " device: " + brandPath + " and verify the settings.";
        String explanation = "Standard configuration audit for " + session.getCategory() + " on " + session.getModel() + ".";

        if (kbStep != null) {
            title = kbStep.path("title").asText(title);
            instruction = kbStep.path("action").asText() + " (On " + session.getBrand() + ": " + brandPath + ")";
            explanation = kbStep.path("rationale").asText(explanation);
        }

        return new TroubleshootResponse(
                session.getSessionId(),
                session.getBrand(),
                session.getModel(),
                session.getCategory(),
                stepNumber,
                title,
                instruction,
                explanation,
                false,
                "IN_PROGRESS",
                null
        );
    }
}
