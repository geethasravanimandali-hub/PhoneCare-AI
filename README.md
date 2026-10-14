# PhoneCare-AI

AI-powered Android phone troubleshooting system combining adaptive conversational diagnostics, session outcome tracking, grounded OEM knowledge base recommendations, and a self-trained diagnostic machine learning pipeline.

---

## 🛠 Technology Stack

- **Backend:** Java 21, Spring Boot 3.3.4, Maven, Spring Web, Jakarta Validation, JUnit 5, MockMvc
- **Frontend:** Vanilla HTML5, Modern CSS3 (`Plus Jakarta Sans`), JavaScript (Fetch API)
- **Machine Learning & Data Tools:** Python 3.9+, scikit-learn 1.6+, NumPy, SciPy
- **Automated UI Testing:** Selenium 4
- **Containerization & Deployment:** Docker multi-stage build (`eclipse-temurin:21`), Docker Compose, Render dynamic `$PORT`
- **Container Orchestration:** Kubernetes (Deployment, Service, ConfigMap manifests for local Minikube / Docker Desktop / Kind)

---

## 📁 Repository Structure

```text
PhoneCare-AI/
├── backend/                              # Spring Boot REST Application
│   ├── src/main/java/com/phonecare/
│   │   ├── controller/                   # REST API Endpoints (/api/diagnose, /api/ml/predict, etc.)
│   │   ├── model/                        # DTOs, SessionState, MlPredictionResult
│   │   └── service/                      # AiAgentService, DiagnosticMlService, KnowledgeBaseService
│   ├── src/main/resources/               # Application properties, UI assets, bundled model weights
│   ├── src/test/java/com/phonecare/      # JUnit 5 & MockMvc Automated Tests (29 tests)
│   └── pom.xml                           # Maven project configuration
├── frontend/                             # Vanilla UI source (synchronized with backend static resources)
│   ├── index.html
│   ├── script.js
│   └── style.css
├── data/
│   ├── dataset/
│   │   └── troubleshooting_benchmark.json # Curated 64-case benchmark dataset
│   └── sessions/                         # Persisted JSON audit logs
├── ml/                                   # Diagnostic ML & Evaluation Pipeline
│   ├── train_model.py                    # Scikit-learn & portable model training pipeline
│   ├── predict.py                        # Diagnostic prediction CLI & inference engine
│   ├── service.py                        # Lightweight zero-dependency HTTP microservice
│   ├── validate_dataset.py               # Dataset schema and provenance validator
│   ├── evaluate_pipeline.py              # ML evaluation & baseline comparison
│   ├── requirements.txt                  # Python dependencies
│   ├── Dockerfile                        # Dedicated Python ML microservice container
│   ├── models/                           # Trained joblib model, portable JSON, and metadata
│   └── tests/                            # Automated Python test suite (22 tests)
├── tests/
│   └── selenium/                         # Automated end-to-end browser UI journey tests
├── k8s/                                  # Kubernetes deployment and service manifests
│   └── phonecare-deployment.yaml
├── docker-compose.yml                    # Multi-container orchestration (Backend + ML Service)
├── Dockerfile                            # Production multi-stage Docker build for Backend
└── README.md                             # Project documentation
```

---

## 🤖 Stage 4: Our Own Machine-Learning Model

PhoneCare-AI includes its own self-trained machine learning diagnostic pipeline:

1. **Training Pipeline (`ml/train_model.py`):**
   - Fits an N-gram `TfidfVectorizer` (unigrams + bigrams) and an L2-regularized `LogisticRegression` classifier on empirical dataset cases.
   - Saves `ml/models/diagnostic_model.joblib`, `ml/models/portable_model.json`, and `ml/models/model_metadata.json`.
   - Also exports pure-Python portable Naive Bayes weights into JSON for environments where scikit-learn is not available.

2. **Inference Interface (`ml/predict.py`):**
   - Accepts symptom descriptions and optional device context (brand, model, Android version).
   - Generates structured JSON output with predicted category, confidence score, confidence level (`HIGH`, `MEDIUM`, `LOW`), top candidate categories, and diagnostic advice.
   - **Calibrated Uncertainty Detection:** Low probability ($p < 0.18$), narrow classification margin ($< 0.04$), or out-of-vocabulary inputs trigger uncertainty flags and prompt follow-up clarification rather than overconfident guesses.

3. **Backend Integration (`DiagnosticMlService.java`):**
   - **Multi-Tier Execution:** First queries the standalone Python ML microservice (`http://localhost:5001/predict`) if configured; falls back to invoking `ml/predict.py` via `ProcessBuilder`; and seamlessly falls back to an in-memory pure-Java evaluator of `portable_model.json` bundled on the classpath.
   - **Resilience:** The backend will never fail even if Python is missing from the environment.
   - Complements the grounded OEM knowledge base without replacing verified instructions.

---

## 📊 Model Evaluation & Baseline Comparison

Evaluation is performed using stratified train/test splits with strict separation between empirical cases (physical lab tests and verified OEM documentation) and synthetic paraphrases:

| Model / Baseline | Empirical Test Accuracy | Macro F1 | Description |
| :--- | :--- | :--- | :--- |
| **Majority Class Baseline** | **12.5%** | 0.0278 | Predicts most frequent class (random chance on 8 balanced categories) |
| **Pure-Python Naive Bayes** | **37.5%** | 0.3208 | Standard-library TF-IDF Naive Bayes baseline |
| **Trained Scikit-Learn Model** | **50.0%** | **0.4458** | N-gram TF-IDF Vectorizer + Logistic Regression |
| **Synthetic Robustness Test** | **50.0%** | 0.4458 | Evaluated on isolated synthetic variations |

### Limitations & Data Governance
- **Dataset Size:** 64 records (8 per category). This is an initial benchmark prototype suitable for reproducible college demonstration.
- **Data Provenance:** 48 empirical records (75%) are strictly isolated from 16 synthetic linguistic variations (25%).
- **Honest Metrics:** With 8 categories and 4 training records per category, 50% accuracy is 4x better than random guess (12.5%). Metrics are never fabricated.

---

## 🚀 Running the Application Locally

### 1. Run Spring Boot Backend & Web UI
```bash
cd backend
mvn spring-boot:run
```
Open **[http://localhost:8080](http://localhost:8080)** in your browser.

### 2. (Optional) Run Python ML Microservice
```bash
python3 ml/service.py --port 5001
```

### 3. Run CLI ML Predictions Directly
```bash
# Battery drain example
python3 ml/predict.py --symptom "My battery is draining extremely fast overnight when idle" --brand "Samsung" --model "Galaxy S24" --android-version "14" --json

# Wi-Fi problem example
python3 ml/predict.py --symptom "Wi-Fi keeps dropping and disconnecting from home router" --brand "Google Pixel" --model "Pixel 8" --json
```

---

## 🧪 Automated Testing

### 1. Run Python Unit & ML Tests (22 Tests)
Verifies dataset validation, leakage audits, ML evaluation, training, artifact loading, predictions, uncertainty handling, and microservice handlers:
```bash
python3 -m unittest discover -s ml/tests -p "test_*.py"
```

### 2. Run Java Backend Tests (29 Tests)
Verifies session tracking, 3-state outcomes, adaptive clarification, knowledge base integrity, and ML integration:
```bash
cd backend
mvn test
```

### 3. Run Selenium End-to-End UI Journey Tests
Automates the full browser user journey (phone selection, symptom submission, follow-up clarification, step progression, and resolution confirmation):
```bash
python3 tests/selenium/test_user_journey.py
```
*(Tests run in headless Chrome by default and automatically start an in-process mock server if the backend is not already active).*

---

## 🐳 Docker & Docker Compose

### Option A: Run Full Stack with Docker Compose
Starts both the Java backend and the dedicated Python ML service:
```bash
docker-compose up --build
```
- Web UI & Backend: **http://localhost:8080**
- ML Microservice: **http://localhost:5001/health**

### Option B: Build and Run Standalone Backend Container
The multi-stage Dockerfile bundles the executable JAR and embedded portable ML weights:
```bash
docker build -t phonecare-backend .
docker run -p 8080:8080 phonecare-backend
```

---

## ☸️ Kubernetes (Local Deployment)

PhoneCare-AI provides standard Kubernetes manifests in `k8s/phonecare-deployment.yaml` for local deployment on **Minikube**, **Kind**, or **Docker Desktop**:

### 1. Build Local Container Images
```bash
docker build -t phonecare-backend:latest .
docker build -t phonecare-ml-service:latest -f ml/Dockerfile .
```

*(If using Minikube, run `eval $(minikube docker-env)` before building).*

### 2. Apply Kubernetes Manifests
```bash
kubectl apply -f k8s/phonecare-deployment.yaml
```

### 3. Verify Pods and Services
```bash
kubectl get pods -l app=phonecare
kubectl get svc -l app=phonecare
```

### 4. Access the Application
- **Minikube:** `minikube service phonecare-backend`
- **Docker Desktop / NodePort:** Open **http://localhost:30080** or port-forward:
```bash
kubectl port-forward svc/phonecare-backend 8080:8080
```

---

## 📡 REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/metadata` | Supported phone brands, models, and problem categories |
| `POST` | `/api/validate-problem` | Validates symptom description against selected category |
| `POST` | `/api/diagnose` | Starts a troubleshooting session and provides Step 1 or follow-up |
| `POST` | `/api/troubleshoot` | Submits step feedback (`SOLVED`, `PARTIALLY_SUCCESSFUL`, `NOT_SOLVED`) |
| `POST` | `/api/ml/predict` | Direct prediction using PhoneCare-AI ML diagnostic pipeline |
| `GET` | `/api/ml/status` | Reports active ML integration and portable model status |
| `GET` | `/api/session/{id}` | Retrieves session audit state and attempt history |
