# PhoneCare-AI

AI-powered Android phone troubleshooting agent combining adaptive conversational diagnostics, session outcome tracking, evidence-based recommendations, and a self-trained diagnostic machine learning pipeline.

---

## 🛠 Technology Stack

- **Backend:** Java 21, Spring Boot 3.3.4, Maven, Spring Web, Jakarta Validation, JUnit 5, MockMvc
- **Frontend:** Vanilla HTML5, Modern CSS3 (`Plus Jakarta Sans`), JavaScript (Fetch API)
- **Machine Learning & Data Tools:** Python 3.9+, scikit-learn 1.6+, NumPy, SciPy
- **Containerization & Deployment:** Docker multi-stage build (`eclipse-temurin:21`), Render dynamic `$PORT`

---

## 📁 Repository Structure

```text
PhoneCare-AI/
├── backend/                              # Spring Boot REST Application
│   ├── src/main/java/com/phonecare/      # Controllers, Services, Models
│   ├── src/main/resources/               # Static UI resources & Knowledge Base
│   ├── src/test/java/com/phonecare/      # JUnit 5 & MockMvc Automated Tests
│   └── pom.xml                           # Maven project configuration
├── frontend/                             # Vanilla UI source (synchronized with static)
│   ├── index.html
│   ├── script.js
│   └── style.css
├── data/
│   ├── dataset/
│   │   └── troubleshooting_benchmark.json # Curated 64-case benchmark dataset
│   └── sessions/                         # Persisted JSON audit logs
├── ml/                                   # Diagnostic ML & Evaluation Pipeline
│   ├── validate_dataset.py               # Dataset validation script
│   ├── evaluate_pipeline.py              # ML evaluation & baseline comparison
│   ├── requirements.txt                  # Python dependencies
│   └── tests/                            # Automated Python test suite
├── Dockerfile                            # Production multi-stage Docker build
└── README.md                             # Project documentation
```

---

## 🚀 Running the Application

### 1. Run Spring Boot Backend & Web UI Locally
```bash
cd backend
mvn spring-boot:run
```
Open [http://localhost:8080](http://localhost:8080) in your browser.

### 2. Run Java Unit & Integration Tests (23 Tests)
```bash
cd backend
mvn test
```

### 3. Package Standalone Production JAR
```bash
cd backend
mvn clean package -DskipTests
java -jar target/phonecare-backend-1.0.0.jar
```

---

## 📊 Dataset Validation & ML Pipeline

### 1. Virtual Environment Setup
```bash
python3 -m venv ml/.venv
source ml/.venv/bin/activate
pip install -r ml/requirements.txt
```

### 2. Validate Dataset Schema & Integrity
Validates required schema fields, unique case IDs, allowed categories, source citations, and separates synthetic from experimental cases:
```bash
python3 ml/validate_dataset.py data/dataset/troubleshooting_benchmark.json
```

### 3. Run Diagnostic ML Evaluation Pipeline
Splits data into stratified train/test sets, checks for data leakage, and benchmarks the majority-class baseline, pure-Python Naive Bayes, and scikit-learn models:
```bash
python3 ml/evaluate_pipeline.py --dataset data/dataset/troubleshooting_benchmark.json
```

### 4. Run Automated Python Tests (11 Tests)
```bash
python3 -m unittest discover -s ml/tests -p "test_*.py"
```

---

## 📋 Benchmark Dataset Schema

Each troubleshooting record in `data/dataset/troubleshooting_benchmark.json` follows this schema:

| Field | Type | Description |
| :--- | :--- | :--- |
| `case_id` | String | Unique case identifier (e.g., `TC-BAT-001`) |
| `brand` | String | Phone OEM brand (`Samsung`, `Google Pixel`, etc.) |
| `model` | String | Device model (e.g., `Galaxy S24`, `Pixel 8`) |
| `android_version` | String | Android OS release version (e.g., `14`, `13`) |
| `symptom_description` | String | Natural language user complaint |
| `usage_condition` | String | Operating state (e.g., `idle_standby`, `fast_charging`) |
| `diagnostic_observations` | String | Observed hardware/system anomaly |
| `target_fault_category` | String | One of the 8 canonical categories |
| `target_fault_subsystem` | String | Specific component/subsystem affected |
| `recommended_action_id` | String | Machine-readable action code |
| `recommended_action_title`| String | Human-readable action title |
| `outcome` | String | `SUCCESSFUL`, `PARTIALLY_SUCCESSFUL`, `UNKNOWN` |
| `data_provenance` | String | `CONTROLLED_EXPERIMENT`, `VERIFIED_DOCUMENTATION`, `SYNTHETIC_VARIATION` |
| `evidence_source` | String | Official OEM / AOSP documentation title |
| `source_url` | String | Verified HTTP(S) documentation reference |

---

## 🔒 Data Limitations & Governance

- **Sample Size:** The dataset contains 64 carefully curated, balanced cases (8 per category). This is sufficient for reproducible pipeline verification and baseline comparison, but requires ongoing empirical expansion prior to production freeze.
- **Separation of Synthetic Records:** 16 synthetic linguistic variations (25.0%) are strictly isolated from the 48 verified and experimental cases (75.0%).
- **Observed Outcomes:** Documentation-derived steps without physical device test bench verification are strictly recorded as `UNKNOWN` rather than fabricating success rates.
