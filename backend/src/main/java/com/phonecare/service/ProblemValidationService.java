package com.phonecare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonecare.model.DiagnosisRequest;
import com.phonecare.model.ProblemValidationResponse;
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
import java.util.regex.Pattern;

@Service
public class ProblemValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ProblemValidationService.class);

    private final KnowledgeBaseService kbService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.model:gemini-1.5-flash}")
    private String modelName;

    // Ordered mapping of problem categories to regex symptom patterns
    private static final Map<String, List<Pattern>> CATEGORY_PATTERNS = new LinkedHashMap<>();

    static {
        // 1. Wi-Fi problems (check before generic network)
        CATEGORY_PATTERNS.put("Wi-Fi problems", List.of(
                Pattern.compile("\\b(wi-?fi|wlan|router|hotspot|ssid)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(wireless\\s+network)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 2. Mobile network problems
        CATEGORY_PATTERNS.put("Mobile network problems", List.of(
                Pattern.compile("\\b(cellular|mobile\\s+data|sim\\s+card|e?sim|no\\s+service|emergency\\s+calls?|call\\s+drops?|dropped\\s+calls?|cannot\\s+make\\s+calls?|reception|signal\\s+bars?|no\\s+signal|carrier|lte|5g|4g)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 3. Charging problems
        CATEGORY_PATTERNS.put("Charging problems", List.of(
                Pattern.compile("\\b(charg(ing|er|es)?|slow\\s+charg\\w+|usb-?c|charging\\s+cable|charging\\s+port|moisture\\s+detected|plugged\\s+in|not\\s+charging|won'?t\\s+charge)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 4. Phone overheating
        CATEGORY_PATTERNS.put("Phone overheating", List.of(
                Pattern.compile("\\b(over-?heat(ing|s|ed)?|heat(ing|s)?\\s+up|too\\s+hot|very\\s+hot|extremely\\s+hot|thermal|burning\\s+hot|warm\\s+to\\s+touch|temperature\\s+warning)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 5. Battery draining
        CATEGORY_PATTERNS.put("Battery draining", List.of(
                Pattern.compile("\\b(batter(y|ies)|drain(ing|s|ed)?|deplet(ing|ion|e)?|discharg(ing|e)?|percentage\\s+drop|battery\\s+life|dies\\s+fast|run\\s+out\\s+of\\s+juice)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 6. Camera problems
        CATEGORY_PATTERNS.put("Camera problems", List.of(
                Pattern.compile("\\b(camera|lens|photo|photos|picture|pictures|blurry\\s+photo|focus|shutter|video\\s+recording|front\\s+camera|rear\\s+camera)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 7. Storage problems
        CATEGORY_PATTERNS.put("Storage problems", List.of(
                Pattern.compile("\\b(storage|internal\\s+storage|storage\\s+full|out\\s+of\\s+space|out\\s+of\\s+storage|low\\s+storage|low\\s+memory|insufficient\\s+space|free\\s+up\\s+space|recycle\\s+bin)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // 8. Phone lagging
        CATEGORY_PATTERNS.put("Phone lagging", List.of(
                Pattern.compile("\\b(lag(ging|s|ged)?|sluggish|freeze|freezing|freezes|stutter(ing|s)?|unresponsive|hang(ing|s)?|app\\s+crashes|crashing|slow\\s+performance|delayed\\s+touch)\\b", Pattern.CASE_INSENSITIVE)
        ));
    }

    public ProblemValidationService(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    public ProblemValidationResponse validate(DiagnosisRequest request) {
        String description = request.getDescription() != null ? request.getDescription().trim() : "";
        String selectedCategory = request.getCategory() != null ? request.getCategory().trim() : "";

        // 1. Safety Priority Check: Safety always overrides other considerations
        if (kbService.hasSafetyHazard(selectedCategory, description)) {
            return new ProblemValidationResponse(
                    "MATCH",
                    selectedCategory,
                    List.of(selectedCategory),
                    selectedCategory,
                    "Safety hazard detected. Proceeding immediately to safety escalation.",
                    true
            );
        }

        // 2. If Live AI is configured, try classification with graceful fallback
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                ProblemValidationResponse aiResponse = callGeminiValidation(request);
                if (aiResponse != null) {
                    return aiResponse;
                }
            } catch (Exception e) {
                logger.warn("Live AI problem validation failed: {}. Falling back to rule-based keyword validation.", e.getMessage());
            }
        }

        // 3. Deterministic Rule-Based Symptom Detection (Robust Fallback)
        return evaluateRuleBasedValidation(selectedCategory, description);
    }

    public ProblemValidationResponse evaluateRuleBasedValidation(String selectedCategory, String description) {
        List<String> detected = detectCategories(description);

        // Case A: UNCLEAR - No specific symptoms recognized or input is extremely generic
        if (detected.isEmpty()) {
            return new ProblemValidationResponse(
                    "UNCLEAR",
                    selectedCategory,
                    Collections.emptyList(),
                    null,
                    "Your description is too brief or unclear. Please provide more details about what is happening with your phone (for example: battery, Wi-Fi, overheating, charging, camera, storage, or lagging).",
                    false
            );
        }

        // Case B: MULTIPLE_PROBLEMS - Description contains symptoms from 2 or more distinct categories
        if (detected.size() > 1) {
            String catList = String.join(", ", detected);
            return new ProblemValidationResponse(
                    "MULTIPLE_PROBLEMS",
                    selectedCategory,
                    detected,
                    detected.get(0),
                    "We noticed multiple issues mentioned in your description (" + catList + "). Which problem would you like to troubleshoot first?",
                    false
            );
        }

        // Single category detected
        String singleDetected = detected.get(0);

        // Case C: MATCH - Single detected category matches the selected category
        if (singleDetected.equalsIgnoreCase(selectedCategory)) {
            return new ProblemValidationResponse(
                    "MATCH",
                    selectedCategory,
                    detected,
                    selectedCategory,
                    "Selected category matches your problem description.",
                    false
            );
        }

        // Case D: MISMATCH - Single detected category is different from selected category
        return new ProblemValidationResponse(
                "MISMATCH",
                selectedCategory,
                detected,
                singleDetected,
                "It looks like your issue might be related to " + singleDetected + " instead of " + selectedCategory + ". Which one would you like to troubleshoot?",
                false
        );
    }

    public List<String> detectCategories(String description) {
        if (description == null || description.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, List<Pattern>> entry : CATEGORY_PATTERNS.entrySet()) {
            String category = entry.getKey();
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(description).find()) {
                    matched.add(category);
                    break;
                }
            }
        }
        return matched;
    }

    private ProblemValidationResponse callGeminiValidation(DiagnosisRequest request) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey.trim();

        String prompt = String.format(
                "You are an Android troubleshooting problem classifier. Evaluate the user's selected category vs their description.\n" +
                "Selected Category: \"%s\"\n" +
                "User Description: \"%s\"\n\n" +
                "Available Categories:\n" +
                "- Battery draining\n" +
                "- Phone overheating\n" +
                "- Wi-Fi problems\n" +
                "- Mobile network problems\n" +
                "- Charging problems\n" +
                "- Camera problems\n" +
                "- Storage problems\n" +
                "- Phone lagging\n\n" +
                "Rules:\n" +
                "1. If description has no clear technical symptoms or is too vague (e.g. 'not working', 'help', 'bad'), outcome is UNCLEAR.\n" +
                "2. If description clearly mentions 2 or more distinct problems from different categories, outcome is MULTIPLE_PROBLEMS.\n" +
                "3. If description describes only 1 problem that matches Selected Category, outcome is MATCH.\n" +
                "4. If description describes 1 problem that differs from Selected Category, outcome is MISMATCH with suggestedCategory set to that detected problem.\n\n" +
                "Return ONLY valid JSON matching this schema:\n" +
                "{\"outcome\": \"MATCH|MISMATCH|MULTIPLE_PROBLEMS|UNCLEAR\", \"detectedCategories\": [\"...\"], \"suggestedCategory\": \"...\", \"message\": \"...\"}",
                request.getCategory(), request.getDescription()
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
            JsonNode resultNode = objectMapper.readTree(text);

            String outcome = resultNode.path("outcome").asText("MATCH");
            List<String> detected = new ArrayList<>();
            if (resultNode.has("detectedCategories")) {
                for (JsonNode cat : resultNode.path("detectedCategories")) {
                    detected.add(cat.asText());
                }
            }
            String suggested = resultNode.path("suggestedCategory").isNull() ? null : resultNode.path("suggestedCategory").asText();
            String msg = resultNode.path("message").asText("Validation complete.");

            return new ProblemValidationResponse(outcome, request.getCategory(), detected, suggested, msg, false);
        }
        return null;
    }
}
