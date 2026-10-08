// PhoneCare AI - Interactive Frontend Controller

document.addEventListener('DOMContentLoaded', () => {
  // DOM Elements - Page 1
  const brandSelect = document.getElementById('brand-select');
  const modelSelect = document.getElementById('model-select');
  const categorySelect = document.getElementById('category-select');
  const descriptionInput = document.getElementById('description-input');
  const diagnosisForm = document.getElementById('diagnosis-form');
  const formError = document.getElementById('form-error');
  const diagnoseBtn = document.getElementById('diagnose-btn');

  // DOM Elements - Page 2
  const diagnosisView = document.getElementById('diagnosis-view');
  const troubleshootingView = document.getElementById('troubleshooting-view');
  const summaryBrand = document.getElementById('summary-brand');
  const summaryModel = document.getElementById('summary-model');
  const summaryCategory = document.getElementById('summary-category');
  const summaryDescription = document.getElementById('summary-description');

  const stepContainer = document.getElementById('step-container');
  const stepBadge = document.getElementById('step-badge');
  const stepStatus = document.getElementById('step-status');
  const stepTitle = document.getElementById('step-title');
  const stepInstruction = document.getElementById('step-instruction');
  const stepExplanation = document.getElementById('step-explanation');

  const btnSolved = document.getElementById('btn-solved');
  const btnNotSolved = document.getElementById('btn-not-solved');

  const finalCard = document.getElementById('final-card');
  const finalIcon = document.getElementById('final-icon');
  const finalTitle = document.getElementById('final-title');
  const finalMessage = document.getElementById('final-message');
  const btnRestart = document.getElementById('btn-restart');

  // Active Session State
  let currentSessionId = null;
  let metadata = { brandsWithModels: {}, categories: [] };

  // Base API URL (relative path works for both direct & proxied deployments)
  const API_BASE = '/api';

  // 1. Initialize Metadata Dropdowns
  async function loadMetadata() {
    try {
      const response = await fetch(`${API_BASE}/metadata`);
      if (!response.ok) throw new Error('Failed to load device metadata');

      metadata = await response.json();

      // Populate Brands
      Object.keys(metadata.brandsWithModels).forEach(brand => {
        const opt = document.createElement('option');
        opt.value = brand;
        opt.textContent = brand;
        brandSelect.appendChild(opt);
      });

      // Populate Categories
      metadata.categories.forEach(cat => {
        const opt = document.createElement('option');
        opt.value = cat;
        opt.textContent = cat;
        categorySelect.appendChild(opt);
      });
    } catch (err) {
      console.error('Metadata error:', err);
      showError('Unable to connect to PhoneCare backend service. Please check that the server is running on port 8080.');
    }
  }

  // Brand selection triggers dynamic Model dropdown update
  brandSelect.addEventListener('change', () => {
    const selectedBrand = brandSelect.value;
    modelSelect.innerHTML = '<option value="" disabled selected>Select phone model...</option>';

    if (selectedBrand && metadata.brandsWithModels[selectedBrand]) {
      metadata.brandsWithModels[selectedBrand].forEach(model => {
        const opt = document.createElement('option');
        opt.value = model;
        opt.textContent = model;
        modelSelect.appendChild(opt);
      });
      modelSelect.disabled = false;
    } else {
      modelSelect.disabled = true;
    }
  });

  // 2. Form Submission -> POST /api/diagnose
  diagnosisForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideError();

    const payload = {
      brand: brandSelect.value,
      model: modelSelect.value,
      category: categorySelect.value,
      description: descriptionInput.value.trim()
    };

    if (!payload.brand || !payload.model || !payload.category || !payload.description) {
      showError('Please complete all form fields.');
      return;
    }

    setButtonLoading(diagnoseBtn, true, 'Analyzing Problem...');

    try {
      const res = await fetch(`${API_BASE}/diagnose`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.message || 'Diagnosis service request failed');
      }

      const data = await res.json();
      currentSessionId = data.sessionId;

      // Update Device Summary Banner
      summaryBrand.textContent = data.brand;
      summaryModel.textContent = data.model;
      summaryCategory.textContent = data.category;
      summaryDescription.textContent = payload.description;

      // Switch views
      diagnosisView.classList.add('hidden');
      troubleshootingView.classList.remove('hidden');
      finalCard.classList.add('hidden');
      stepContainer.classList.remove('hidden');

      renderStep(data);
    } catch (err) {
      console.error('Diagnose error:', err);
      showError(err.message || 'Error communicating with AI agent.');
    } finally {
      setButtonLoading(diagnoseBtn, false, 'Diagnose Issue');
    }
  });

  // 3. User Feedback Actions -> POST /api/troubleshoot
  async function submitFeedback(feedbackType) {
    if (!currentSessionId) return;

    btnSolved.disabled = true;
    btnNotSolved.disabled = true;

    try {
      const res = await fetch(`${API_BASE}/troubleshoot`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: currentSessionId,
          feedback: feedbackType
        })
      });

      if (!res.ok) throw new Error('Failed to submit step feedback');

      const data = await res.json();
      renderStep(data);
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      btnSolved.disabled = false;
      btnNotSolved.disabled = false;
    }
  }

  btnSolved.addEventListener('click', () => submitFeedback('SOLVED'));
  btnNotSolved.addEventListener('click', () => submitFeedback('NOT_SOLVED'));

  // 4. Render Step or Final Conclusion
  function renderStep(data) {
    if (data.finalStep || data.status === 'SOLVED' || data.status === 'ESCALATED') {
      stepContainer.classList.add('hidden');
      finalCard.classList.remove('hidden');

      if (data.status === 'SOLVED') {
        finalIcon.textContent = '✅';
        finalTitle.textContent = data.title || 'Issue Resolved!';
        finalMessage.textContent = data.finalRecommendation || data.instruction;
      } else {
        finalIcon.textContent = '🛡️';
        finalTitle.textContent = data.title || 'Official Service Recommended';
        finalMessage.innerHTML = `<strong>${data.explanation}</strong><br><br>${data.finalRecommendation || data.instruction}`;
      }
      return;
    }

    // Render active step
    stepBadge.textContent = `Step ${data.stepNumber}`;
    stepStatus.textContent = `Diagnostic Active`;
    stepTitle.textContent = data.title;
    stepInstruction.textContent = data.instruction;
    stepExplanation.textContent = data.explanation;
  }

  // 5. Restart / Reset
  btnRestart.addEventListener('click', () => {
    currentSessionId = null;
    diagnosisForm.reset();
    modelSelect.disabled = true;
    troubleshootingView.classList.add('hidden');
    diagnosisView.classList.remove('hidden');
  });

  // Helpers
  function showError(msg) {
    formError.textContent = msg;
    formError.classList.remove('hidden');
  }

  function hideError() {
    formError.classList.add('hidden');
    formError.textContent = '';
  }

  function setButtonLoading(btn, isLoading, text) {
    btn.disabled = isLoading;
    const btnText = btn.querySelector('.btn-text');
    if (btnText) btnText.textContent = text;
  }

  // Load metadata on page load
  loadMetadata();
});
