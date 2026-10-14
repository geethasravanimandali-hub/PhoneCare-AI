package com.phonecare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonecare.model.SessionState;
import com.phonecare.model.TroubleshootResponse;
import com.phonecare.model.TroubleshootingAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
                    kbService.getSafetyAdvice(session.getCategory()),
                    "Critical physical battery/thermal safety hazard",
                    1.0,
                    "Universal Android Hardware Safety Standards (UL 1642 / IEC 62133)",
                    "VERIFIED"
            );
        }

        // Rule 2: Problem Solved Resolution
        if ("SOLVED".equalsIgnoreCase(feedback) || "SUCCESSFUL".equalsIgnoreCase(feedback)) {
            session.setStatus("SOLVED");
            TroubleshootResponse response = new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    session.getCurrentStepNumber(),
                    "Issue Resolved Successfully",
                    "Your smartphone has resumed normal operation.",
                    "The applied troubleshooting action successfully resolved the diagnostic condition.",
                    true,
                    "SOLVED",
                    "Troubleshooting complete. Keep your " + session.getBrand() + " device updated with latest security patches.",
                    session.getSuspectedCause() != null ? session.getSuspectedCause() : "Resolved configuration anomaly",
                    1.0,
                    "Verified User Outcome Confirmation",
                    "VERIFIED"
            );
            response.setAttemptHistory(session.getAttempts());
            return response;
        }

        // Rule 3: Escalation Threshold (Max safe steps reached or all steps exhausted)
        int stepNumber = session.getCurrentStepNumber();
        if (stepNumber >= 4) {
            session.setStatus("ESCALATED");
            TroubleshootResponse response = new TroubleshootResponse(
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
                    "We recommend booking an appointment at an official " + session.getBrand() + " Authorized Service Center. Do not attempt to open the device.",
                    "Potential component-level hardware failure",
                    0.95,
                    "OEM Standard Service Protocol",
                    "VERIFIED"
            );
            response.setAttemptHistory(session.getAttempts());
            return response;
        }

        // Rule 4: Adaptive Diagnostic Disambiguation (Ask follow-up questions when symptoms are underspecified)
        if (stepNumber == 1 && session.getCompletedSteps().isEmpty() && needsClarification(session)) {
            return generateAdaptiveClarificationQuestion(session);
        }

        // Rule 5: Try LLM Call if API Key is Present
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                TroubleshootResponse aiResponse = callGeminiLlm(session);
                if (aiResponse != null) {
                    session.setSuspectedCause(aiResponse.getSuspectedCause());
                    session.setRecommendationSource("GEMINI_LLM");
                    session.setConfidence(aiResponse.getConfidence() != null ? aiResponse.getConfidence() : 0.85);
                    aiResponse.setAttemptHistory(session.getAttempts());
                    return aiResponse;
                }
            } catch (Exception e) {
                logger.warn("Live AI LLM call failed or timed out: {}. Seamlessly falling back to grounded Knowledge Base.", e.getMessage());
            }
        }

        // Rule 6: Grounded Knowledge Base Engine with Non-Repeating Step Selection
        return generateAdaptiveKbStep(session, stepNumber);
    }

    /**
     * Determines whether the symptom description requires diagnostic disambiguation.
     */
    public boolean needsClarification(SessionState session) {
        if (!session.getDiagnosticObservations().isEmpty()) {
            return false;
        }
        String desc = session.getDescription() != null ? session.getDescription().toLowerCase() : "";
        String cat = session.getCategory() != null ? session.getCategory() : "";

        if ("Phone overheating".equalsIgnoreCase(cat)) {
            return !desc.contains("charg") && !desc.contains("game") && !desc.contains("gaming")
                    && !desc.contains("idle") && !desc.contains("standby") && !desc.contains("camera")
                    && !desc.contains("navigation");
        }
        if ("Battery draining".equalsIgnoreCase(cat)) {
            return !desc.contains("standby") && !desc.contains("overnight") && !desc.contains("screen")
                    && !desc.contains("update") && !desc.contains("5g");
        }
        if ("Wi-Fi problems".equalsIgnoreCase(cat)) {
            return !desc.contains("all") && !desc.contains("home") && !desc.contains("router")
                    && !desc.contains("toggle") && !desc.contains("grey");
        }
        if ("Charging problems".equalsIgnoreCase(cat)) {
            return !desc.contains("slow") && !desc.contains("cable") && !desc.contains("port")
                    && !desc.contains("moisture") && !desc.contains("won't charge");
        }
        return false;
    }

    /**
     * Constructs an adaptive clarification prompt to gather context before recommending a fix.
     */
    private TroubleshootResponse generateAdaptiveClarificationQuestion(SessionState session) {
        String cat = session.getCategory();
        String question;
        List<String> options;

        if ("Phone overheating".equalsIgnoreCase(cat)) {
            question = "Under what specific conditions does your phone overheat most noticeably?";
            options = List.of(
                    "While charging with cable or wireless pad",
                    "While gaming or running high-performance apps",
                    "While idle in pocket or on standby",
                    "While using camera or GPS navigation outdoors"
            );
        } else if ("Battery draining".equalsIgnoreCase(cat)) {
            question = "When does the battery drain occur most severely?";
            options = List.of(
                    "Overnight while phone is completely idle",
                    "Rapidly during active screen-on usage",
                    "Immediately following a recent system software update",
                    "Only when using Mobile Data / 5G away from Wi-Fi"
            );
        } else if ("Wi-Fi problems".equalsIgnoreCase(cat)) {
            question = "Does this Wi-Fi issue occur on all networks or only a single specific network?";
            options = List.of(
                    "Only on one specific home/office Wi-Fi router",
                    "Across all Wi-Fi networks and public hotspots",
                    "The Wi-Fi toggle switch is greyed out and cannot be activated"
            );
        } else {
            question = "What behavior do you observe when plugging in your charging cable?";
            options = List.of(
                    "Phone does not detect charger at all",
                    "Phone charges extremely slowly (many hours)",
                    "Charging connects and disconnects repeatedly",
                    "Moisture or debris alert is displayed"
            );
        }

        session.setPendingFollowUpQuestion(question);
        session.setPendingFollowUpOptions(options);

        TroubleshootResponse response = new TroubleshootResponse(
                session.getSessionId(),
                session.getBrand(),
                session.getModel(),
                session.getCategory(),
                1,
                "Diagnostic Clarification",
                "To choose the most accurate diagnostic procedure without guessing, please specify when this symptom happens.",
                "Symptom patterns vary significantly across Android hardware subsystems. Pinpointing usage conditions narrows down the suspected root cause.",
                false,
                "IN_PROGRESS",
                null,
                "Ambiguous symptom profile; requires diagnostic observation to establish root cause",
                0.70,
                "Android Core Diagnostic Triage Protocol",
                "INFERRED"
        );
        response.setFollowUpQuestion(question);
        response.setFollowUpOptions(options);
        return response;
    }

    /**
     * Selects the next non-attempted diagnostic step from the grounded Knowledge Base.
     */
    private TroubleshootResponse generateAdaptiveKbStep(SessionState session, int stepNumber) {
        List<JsonNode> allSteps = kbService.getAllSteps(session.getCategory());
        String brandPath = kbService.getBrandPath(session.getCategory(), session.getBrand());

        // Find the first step that has NOT been previously attempted
        JsonNode selectedStep = null;
        for (JsonNode stepNode : allSteps) {
            String title = stepNode.path("title").asText();
            if (!session.hasAttemptedTitle(title)) {
                selectedStep = stepNode;
                break;
            }
        }

        // If all safe KB steps have been attempted, escalate
        if (selectedStep == null) {
            session.setStatus("ESCALATED");
            TroubleshootResponse response = new TroubleshootResponse(
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
                    "We recommend booking an appointment at an official " + session.getBrand() + " Authorized Service Center. Do not attempt to open the device.",
                    "Potential component-level hardware failure",
                    0.95,
                    "OEM Standard Service Protocol",
                    "VERIFIED"
            );
            response.setAttemptHistory(session.getAttempts());
            return response;
        }

        String title = selectedStep.path("title").asText("Recommended Diagnostic Step");
        String instruction = selectedStep.path("action").asText() + " (On " + session.getBrand() + ": " + brandPath + ")";
        String explanation = selectedStep.path("rationale").asText("Standard configuration audit for " + session.getCategory() + ".");

        String suspectedCause = deriveSuspectedCause(session.getCategory(), title);
        session.setSuspectedCause(suspectedCause);
        session.setRecommendationSource("GROUNDED_KNOWLEDGE_BASE");
        session.setConfidence(0.90);

        String evidenceSource = "Grounded Knowledge Base [OEM " + session.getBrand() + " Technical Documentation & AOSP System Guidelines]";

        TroubleshootResponse response = new TroubleshootResponse(
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
                null,
                suspectedCause,
                0.90,
                evidenceSource,
                "VERIFIED"
        );
        response.setAttemptHistory(session.getAttempts());
        return response;
    }

    private String deriveSuspectedCause(String category, String stepTitle) {
        if (category == null) return "System anomaly";
        return switch (category) {
            case "Battery draining" -> "Unregulated background wake-locks or display refresh draw preventing deep sleep state (Doze mode)";
            case "Phone overheating" -> "Thermal build-up from continuous CPU/GPU load, trapped heat dissipation, or modem power spikes";
            case "Wi-Fi problems" -> "Stale DHCP lease, DNS resolver conflict, or corrupted network interface configuration";
            case "Mobile network problems" -> "Stale carrier tower registration or corrupted APN (Access Point Name) routing table";
            case "Charging problems" -> "Debris accumulation, high-resistance charging cable, or battery thermal safety limiter";
            case "Phone lagging" -> "Accumulated background app cache, aggressive thermal throttling, or low available RAM";
            case "Storage problems" -> "Hidden app cache accumulation, orphaned temporary downloads, or full recycle bin";
            case "Camera problems" -> "Stale camera sensor cache or hardware-camera service deadlock";
            default -> "Configuration mismatch or background software state conflict";
        };
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
                "CRITICAL: Do NOT repeat any step in the 'Steps already tried and failed' list.\n" +
                "Provide ONLY valid JSON (without markdown code fences) matching this structure:\n" +
                "{\"stepNumber\": %d, \"title\": \"...\", \"instruction\": \"...\", \"explanation\": \"...\", \"suspectedCause\": \"...\", \"confidence\": 0.88, \"isFinalStep\": false, \"status\": \"IN_PROGRESS\", \"finalRecommendation\": null}",
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

            text = text.replace("```json", "").replace("```", "").trim();
            JsonNode stepJson = objectMapper.readTree(text);

            String title = stepJson.path("title").asText("Recommended Action");

            // Safeguard against LLM repeating a failed step
            if (session.hasAttemptedTitle(title)) {
                logger.warn("LLM proposed step '{}' which was already attempted. Falling back to KB safe step.", title);
                return null;
            }

            return new TroubleshootResponse(
                    session.getSessionId(),
                    session.getBrand(),
                    session.getModel(),
                    session.getCategory(),
                    stepJson.path("stepNumber").asInt(session.getCurrentStepNumber()),
                    title,
                    stepJson.path("instruction").asText(),
                    stepJson.path("explanation").asText(),
                    stepJson.path("isFinalStep").asBoolean(false),
                    stepJson.path("status").asText("IN_PROGRESS"),
                    stepJson.path("finalRecommendation").isNull() ? null : stepJson.path("finalRecommendation").asText(),
                    stepJson.path("suspectedCause").asText("Software state anomaly"),
                    stepJson.path("confidence").asDouble(0.85),
                    "Gemini AI Reasoning Model (Context Grounded)",
                    "INFERRED"
            );
        }
        return null;
    }
}
