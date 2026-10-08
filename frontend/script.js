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

  const progressBarFill = document.getElementById('progress-bar-fill');
  const pipelineStatus = document.getElementById('pipeline-status');

  // Active Session State
  let currentSessionId = null;
  let metadata = { brandsWithModels: {}, categories: [] };

  // Base API URL
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

  // Dynamic Model dropdown update based on Brand
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

    setButtonLoading(diagnoseBtn, true, 'Formulating Diagnostic Step...');

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

      // Update Device Context Chips
      summaryBrand.textContent = data.brand;
      summaryModel.textContent = data.model;
      summaryCategory.textContent = data.category;
      summaryDescription.textContent = payload.description;

      // Switch panels
      diagnosisView.classList.add('hidden');
      troubleshootingView.classList.remove('hidden');
      troubleshootingView.classList.add('fade-in');
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

  // 4. Render Step or Final Resolution (Zero Emojis, Pure SVGs & Dynamic Pipeline)
  function renderStep(data) {
    updatePipeline(data.stepNumber, data.status);

    if (data.finalStep || data.status === 'SOLVED' || data.status === 'ESCALATED') {
      stepContainer.classList.add('hidden');
      finalCard.classList.remove('hidden');
      finalCard.className = 'resolution-card'; // reset classes

      if (data.status === 'SOLVED') {
        finalCard.classList.add('state-success');
        finalIcon.innerHTML = `
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
        `;
        finalTitle.textContent = data.title || 'Diagnostic Resolved';
        finalMessage.innerHTML = `<p>${data.finalRecommendation || data.instruction}</p>`;
      } else {
        finalCard.classList.add('state-escalated');
        finalIcon.innerHTML = `
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
        `;
        finalTitle.textContent = data.title || 'Escalate to Authorized Support';
        finalMessage.innerHTML = `
          <p style="margin-bottom: 8px;"><strong>${data.explanation}</strong></p>
          <p>${data.finalRecommendation || data.instruction}</p>
        `;
      }
      return;
    }

    // Render active step
    stepBadge.textContent = `Step ${data.stepNumber}`;
    stepStatus.textContent = `Analysis Phase ${data.stepNumber}`;
    stepTitle.textContent = data.title;
    stepInstruction.textContent = data.instruction;
    stepExplanation.textContent = data.explanation;
  }

  // Helper: Visual Pipeline Progress Updates
  function updatePipeline(stepNum, status) {
    const totalSteps = 4;
    let clampedStep = Math.min(Math.max(stepNum || 1, 1), totalSteps);
    let pct = (clampedStep / totalSteps) * 100;

    if (progressBarFill) {
      progressBarFill.style.width = `${pct}%`;
    }

    if (pipelineStatus) {
      if (status === 'SOLVED') {
        pipelineStatus.textContent = 'Resolved';
      } else if (status === 'ESCALATED') {
        pipelineStatus.textContent = 'Escalated';
      } else {
        pipelineStatus.textContent = `Phase ${clampedStep} of ${totalSteps}`;
      }
    }

    // Update active node styling
    for (let i = 1; i <= 4; i++) {
      const node = document.getElementById(`node-${i}`);
      if (node) {
        if (i <= clampedStep) {
          node.classList.add('active');
        } else {
          node.classList.remove('active');
        }
      }
    }
  }

  // 5. Restart / Reset
  btnRestart.addEventListener('click', () => {
    currentSessionId = null;
    diagnosisForm.reset();
    modelSelect.disabled = true;
    modelSelect.innerHTML = '<option value="" disabled selected>Select brand first...</option>';
    troubleshootingView.classList.add('hidden');
    diagnosisView.classList.remove('hidden');
    diagnosisView.classList.add('fade-in');
  });

  // Helpers
  function showError(msg) {
    const alertText = formError.querySelector('.alert-text');
    if (alertText) {
      alertText.textContent = msg;
    } else {
      formError.textContent = msg;
    }
    formError.classList.remove('hidden');
  }

  function hideError() {
    formError.classList.add('hidden');
  }

  function setButtonLoading(btn, isLoading, text) {
    btn.disabled = isLoading;
    const btnText = btn.querySelector('.btn-text');
    const spinner = btn.querySelector('.spinner');
    if (btnText) btnText.textContent = text;
    if (spinner) {
      if (isLoading) spinner.classList.remove('hidden');
      else spinner.classList.add('hidden');
    }
  }

  // Load initial metadata on page ready
  loadMetadata();
});
