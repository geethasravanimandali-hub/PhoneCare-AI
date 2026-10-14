package com.phonecare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonecare.model.MlPredictionResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DiagnosticMlService {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticMlService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    @Value("${ml.enabled:true}")
    private boolean mlEnabled;

    @Value("${ml.service.url:}")
    private String mlServiceUrl;

    @Value("${ml.python.path:}")
    private String configuredPythonPath;

    @Value("${ml.models.dir:}")
    private String configuredModelsDir;

    private String resolvedPythonPath;
    private File resolvedPredictScript;
    private File resolvedModelsDir;

    // In-memory pure-Java portable model state for resilient instant fallback
    private boolean portableModelLoaded = false;
    private List<String> portableClasses = new ArrayList<>();
    private Map<String, Double> portableClassPriors = new HashMap<>();
    private Map<String, Map<String, Double>> portableWordCounts = new HashMap<>();
    private Map<String, Double> portableTotalWords = new HashMap<>();
    private Map<String, Double> portableIdf = new HashMap<>();
    private Set<String> portableVocabulary = new HashSet<>();
    private double portableAlpha = 1.0;

    public DiagnosticMlService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        resolvePaths();
        loadPortableModel();
    }

    private void resolvePaths() {
        // Search potential project roots
        List<String> candidateRoots = List.of(
                ".",
                "..",
                "PhoneCare-AI",
                "../PhoneCare-AI"
        );

        for (String root : candidateRoots) {
            File script = new File(root, "ml/predict.py");
            if (script.exists() && script.isFile()) {
                this.resolvedPredictScript = script.getAbsoluteFile();
                this.resolvedModelsDir = new File(root, "ml/models").getAbsoluteFile();
                break;
            }
        }

        if (this.resolvedPredictScript == null) {
            File cwdScript = new File("ml/predict.py");
            if (cwdScript.exists()) {
                this.resolvedPredictScript = cwdScript.getAbsoluteFile();
                this.resolvedModelsDir = new File("ml/models").getAbsoluteFile();
            }
        }

        // Resolve Python binary
        if (configuredPythonPath != null && !configuredPythonPath.isBlank()) {
            this.resolvedPythonPath = configuredPythonPath.trim();
        } else if (resolvedPredictScript != null) {
            File projectDir = resolvedPredictScript.getParentFile().getParentFile();
            File venvPython = new File(projectDir, "ml/.venv/bin/python");
            if (venvPython.exists() && venvPython.canExecute()) {
                this.resolvedPythonPath = venvPython.getAbsolutePath();
            } else {
                this.resolvedPythonPath = "python3";
            }
        } else {
            this.resolvedPythonPath = "python3";
        }

        logger.info("DiagnosticMlService initialized. Resolved python: {}, script: {}, modelsDir: {}",
                resolvedPythonPath,
                resolvedPredictScript != null ? resolvedPredictScript.getPath() : "NOT_FOUND",
                resolvedModelsDir != null ? resolvedModelsDir.getPath() : "NOT_FOUND");
    }

    private void loadPortableModel() {
        // 1. Try classpath resource first (production JAR & test environments)
        try (java.io.InputStream is = getClass().getResourceAsStream("/models/portable_model.json")) {
            if (is != null) {
                JsonNode root = objectMapper.readTree(is);
                parsePortableModelJson(root, "classpath:/models/portable_model.json");
                return;
            }
        } catch (Exception e) {
            logger.debug("Classpath /models/portable_model.json not available: {}", e.getMessage());
        }

        // 2. Try filesystem locations
        List<File> candidates = new ArrayList<>();
        if (resolvedModelsDir != null) {
            candidates.add(new File(resolvedModelsDir, "portable_model.json"));
        }
        candidates.add(new File("ml/models/portable_model.json"));
        candidates.add(new File("../ml/models/portable_model.json"));

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                try {
                    JsonNode root = objectMapper.readTree(candidate);
                    parsePortableModelJson(root, candidate.getPath());
                    return;
                } catch (Exception e) {
                    logger.warn("Failed loading portable model from {}: {}", candidate.getPath(), e.getMessage());
                }
            }
        }
        logger.info("No portable_model.json found at startup; will query HTTP service or ProcessBuilder directly.");
    }

    private void parsePortableModelJson(JsonNode root, String sourceDescription) {
        this.portableClasses = new ArrayList<>();
        for (JsonNode c : root.path("classes")) {
            this.portableClasses.add(c.asText());
        }

        this.portableClassPriors = new HashMap<>();
        JsonNode priorsNode = root.path("class_priors");
        priorsNode.fields().forEachRemaining(entry ->
                portableClassPriors.put(entry.getKey(), entry.getValue().asDouble())
        );

        this.portableWordCounts = new HashMap<>();
        JsonNode wcNode = root.path("word_counts");
        wcNode.fields().forEachRemaining(entry -> {
            Map<String, Double> counts = new HashMap<>();
            entry.getValue().fields().forEachRemaining(w -> counts.put(w.getKey(), w.getValue().asDouble()));
            portableWordCounts.put(entry.getKey(), counts);
        });

        this.portableTotalWords = new HashMap<>();
        JsonNode twNode = root.path("total_words");
        twNode.fields().forEachRemaining(entry ->
                portableTotalWords.put(entry.getKey(), entry.getValue().asDouble())
        );

        this.portableIdf = new HashMap<>();
        JsonNode idfNode = root.path("idf");
        idfNode.fields().forEachRemaining(entry ->
                portableIdf.put(entry.getKey(), entry.getValue().asDouble())
        );

        this.portableVocabulary = new HashSet<>();
        for (JsonNode v : root.path("vocabulary")) {
            portableVocabulary.add(v.asText());
        }

        this.portableAlpha = root.path("alpha").asDouble(1.0);
        this.portableModelLoaded = true;
        logger.info("DiagnosticMlService loaded in-memory portable model with {} classes and {} vocabulary words from {}",
                portableClasses.size(), portableVocabulary.size(), sourceDescription);
    }

    public MlPredictionResult predict(String symptom, String brand, String model, String androidVersion, String observations) {
        if (!mlEnabled) {
            return MlPredictionResult.error("ML inference disabled via configuration.");
        }
        if (symptom == null || symptom.trim().isEmpty()) {
            return MlPredictionResult.error("Symptom description is empty.");
        }

        // Priority 1: Standalone HTTP Microservice if configured and accessible
        if (mlServiceUrl != null && !mlServiceUrl.isBlank()) {
            try {
                MlPredictionResult httpResult = callHttpService(symptom, brand, model, androidVersion, observations);
                if (httpResult != null && "SUCCESS".equals(httpResult.getStatus())) {
                    return httpResult;
                }
            } catch (Exception e) {
                logger.debug("ML HTTP service unavailable at {}: {}. Falling back to CLI/in-memory.", mlServiceUrl, e.getMessage());
            }
        }

        // Priority 2: Direct Python CLI invocation via ProcessBuilder (scikit-learn with fallback)
        if (resolvedPredictScript != null && resolvedPredictScript.exists()) {
            try {
                MlPredictionResult cliResult = callPythonCli(symptom, brand, model, androidVersion, observations);
                if (cliResult != null && "SUCCESS".equals(cliResult.getStatus())) {
                    return cliResult;
                }
            } catch (Exception e) {
                logger.warn("Python CLI invocation failed: {}. Falling back to in-memory portable inference.", e.getMessage());
            }
        }

        // Priority 3: Built-in pure-Java portable Naive Bayes evaluator (zero Python dependency required)
        if (portableModelLoaded) {
            return evaluatePureJavaPortableModel(symptom, brand, model, androidVersion, observations);
        }

        return MlPredictionResult.error("No ML model artifacts or execution engines available.");
    }

    private MlPredictionResult callHttpService(String symptom, String brand, String model,
                                               String androidVersion, String observations) {
        Map<String, String> payload = new HashMap<>();
        payload.put("symptom", symptom);
        payload.put("brand", brand != null ? brand : "");
        payload.put("model", model != null ? model : "");
        payload.put("android_version", androidVersion != null ? androidVersion : "");
        payload.put("observations", observations != null ? observations : "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(mlServiceUrl, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                return objectMapper.readValue(response.getBody(), MlPredictionResult.class);
            } catch (Exception e) {
                logger.warn("Failed parsing ML HTTP response: {}", e.getMessage());
            }
        }
        return null;
    }

    private MlPredictionResult callPythonCli(String symptom, String brand, String model,
                                             String androidVersion, String observations) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(resolvedPythonPath);
        command.add(resolvedPredictScript.getAbsolutePath());
        command.add("--symptom");
        command.add(symptom);

        if (brand != null && !brand.isBlank()) {
            command.add("--brand");
            command.add(brand);
        }
        if (model != null && !model.isBlank()) {
            command.add("--model");
            command.add(model);
        }
        if (androidVersion != null && !androidVersion.isBlank()) {
            command.add("--android-version");
            command.add(androidVersion);
        }
        if (observations != null && !observations.isBlank()) {
            command.add("--observations");
            command.add(observations);
        }
        if (resolvedModelsDir != null && resolvedModelsDir.exists()) {
            command.add("--models-dir");
            command.add(resolvedModelsDir.getAbsolutePath());
        }
        command.add("--json");

        ProcessBuilder pb = new ProcessBuilder(command);
        if (resolvedPredictScript.getParentFile() != null) {
            pb.directory(resolvedPredictScript.getParentFile().getParentFile());
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        String jsonText = output.toString().trim();
        if (jsonText.startsWith("{") && jsonText.endsWith("}")) {
            return objectMapper.readValue(jsonText, MlPredictionResult.class);
        }

        if (exitCode != 0) {
            throw new IllegalStateException("Predict script exited with code " + exitCode + ": " + jsonText);
        }
        return null;
    }

    /**
     * Pure-Java evaluation of portable_model.json weights.
     * Guarantees self-contained ML inference even in bare-metal Java containers without Python.
     */
    public MlPredictionResult evaluatePureJavaPortableModel(String symptom, String brand, String model,
                                                            String androidVersion, String observations) {
        String inputCombined = symptom.toLowerCase();
        if (brand != null && !brand.isBlank()) inputCombined += " " + brand.toLowerCase();
        if (model != null && !model.isBlank()) inputCombined += " " + model.toLowerCase();
        if (androidVersion != null && !androidVersion.isBlank()) inputCombined += " android " + androidVersion.toLowerCase();
        if (observations != null && !observations.isBlank()) inputCombined += " " + observations.toLowerCase();

        if (!portableModelLoaded || portableClasses.isEmpty()) {
            return MlPredictionResult.error("Portable model weights not loaded.");
        }

        List<String> tokens = tokenize(inputCombined);
        if (tokens.isEmpty()) {
            return MlPredictionResult.error("No recognizable tokens in symptom input.");
        }

        int recognizedCount = 0;
        for (String tok : tokens) {
            if (portableVocabulary.contains(tok)) {
                recognizedCount++;
            }
        }
        double vocabOverlap = (double) recognizedCount / tokens.size();

        int vocabSize = Math.max(portableVocabulary.size(), 1);
        Map<String, Double> scores = new HashMap<>();

        for (String cat : portableClasses) {
            double prior = portableClassPriors.getOrDefault(cat, -Math.log(portableClasses.size()));
            double totalWordsInClass = portableTotalWords.getOrDefault(cat, 0.0);
            double denom = totalWordsInClass + portableAlpha * vocabSize;
            Map<String, Double> wc = portableWordCounts.getOrDefault(cat, Collections.emptyMap());

            double logProb = prior;
            for (String tok : tokens) {
                double idfWeight = portableIdf.getOrDefault(tok, 1.0);
                double freq = wc.getOrDefault(tok, 0.0);
                double probW = (freq + portableAlpha) / denom;
                logProb += Math.log(probW) * idfWeight;
            }
            scores.put(cat, logProb);
        }

        // Softmax normalization
        double maxScore = scores.values().stream().max(Double::compare).orElse(0.0);
        double sumExp = 0.0;
        Map<String, Double> expScores = new HashMap<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            double e = Math.exp(entry.getValue() - maxScore);
            expScores.put(entry.getKey(), e);
            sumExp += e;
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map.Entry<String, Double> entry : expScores.entrySet()) {
            double prob = Math.round((entry.getValue() / sumExp) * 10000.0) / 10000.0;
            candidates.add(Map.of("category", entry.getKey(), "score", prob));
        }
        candidates.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));

        String topCategory = (String) candidates.get(0).get("category");
        double topScore = (Double) candidates.get(0).get("score");
        double secondScore = candidates.size() > 1 ? (Double) candidates.get(1).get("score") : 0.0;
        double margin = topScore - secondScore;

        boolean isUncertain = false;
        String uncertaintyReason = null;
        String confidenceLevel = "LOW";

        if (recognizedCount < 1 || vocabOverlap < 0.15) {
            isUncertain = true;
            confidenceLevel = "LOW";
            uncertaintyReason = "Input symptom contains mostly unfamiliar or out-of-vocabulary terms.";
        } else if (topScore < 0.18) {
            isUncertain = true;
            confidenceLevel = "LOW";
            uncertaintyReason = "Model predicted probability is below diagnostic confidence threshold (p < 0.18).";
        } else if (margin < 0.04) {
            isUncertain = true;
            confidenceLevel = "LOW";
            uncertaintyReason = String.format("Narrow margin (%.3f) between top candidate and alternative.", margin);
        } else if (topScore >= 0.30 && margin >= 0.08) {
            confidenceLevel = "HIGH";
            isUncertain = false;
        } else {
            confidenceLevel = "MEDIUM";
            isUncertain = false;
        }

        MlPredictionResult result = new MlPredictionResult(
                "SUCCESS",
                topCategory,
                topScore,
                confidenceLevel,
                isUncertain,
                uncertaintyReason,
                "Pure-Java Portable Naive Bayes Evaluator"
        );
        result.setVocabularyOverlapRatio(Math.round(vocabOverlap * 1000.0) / 1000.0);
        result.setTopCandidates(candidates.subList(0, Math.min(candidates.size(), 3)));
        result.setDiagnosticAdvice(generateDiagnosticAdvice(topCategory, isUncertain));
        return result;
    }

    private List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9_\\-]+\\b").matcher(text.toLowerCase());
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private String generateDiagnosticAdvice(String category, boolean isUncertain) {
        if (isUncertain) {
            return "Diagnostic confidence is low or symptom is ambiguous. " +
                   "Recommend asking targeted follow-up questions regarding specific operating conditions.";
        }
        Map<String, String> adviceMap = Map.of(
                "Battery draining", "Inspect background app battery consumption and system wakelocks under Settings > Battery.",
                "Phone overheating", "Inspect active high-load background tasks, thermal throttling status, and avoid heavy use while charging.",
                "Wi-Fi problems", "Verify DHCP/DNS connectivity, reset network settings, or toggle airplane mode to re-initialize wireless interface.",
                "Mobile network problems", "Check SIM status, toggle airplane mode to refresh carrier tower registration, and verify APN settings.",
                "Charging problems", "Inspect USB-C port for lint/debris, verify cable resistance, and check charger output wattage.",
                "Camera problems", "Clear camera app storage cache, check third-party app permissions, and verify hardware sensor lock.",
                "Storage problems", "Review app cache accumulation, clean download cache, and inspect external SD or media files.",
                "Phone lagging", "Check available RAM, reboot device to clear memory leaks, and verify background process limits."
        );
        return adviceMap.getOrDefault(category, "Review system settings and follow grounded diagnostic steps.");
    }

    public boolean isPortableModelLoaded() {
        return portableModelLoaded;
    }

    public String getResolvedPythonPath() {
        return resolvedPythonPath;
    }
}
