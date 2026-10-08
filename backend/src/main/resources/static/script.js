// PhoneCare AI - Friendly & Clean Interactive Frontend Controller

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
      showError('Unable to connect to PhoneCare diagnostic service. Please check your network connection.');
    }
  }

  // Dynamic Model dropdown update based on Brand selection
  brandSelect.addEventListener('change', () => {
    const selectedBrand = brandSelect.value;
    modelSelect.innerHTML = '<option value="" disabled selected>Select model...</option>';

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
      showError('Please fill out all fields so we can diagnose accurately.');
      return;
    }

    setButtonLoading(diagnoseBtn, true, 'Finding best step...');

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

      // Update Device Summary Spec Pill
      summaryBrand.textContent = data.brand;
      summaryModel.textContent = data.model;
      summaryCategory.textContent = data.category;
      summaryDescription.textContent = payload.description;

      // Transition Views
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

  // 4. Render Step or Final Friendly Resolution
  function renderStep(data) {
    const totalSteps = 4;
    const stepNum = Math.min(Math.max(data.stepNumber || 1, 1), totalSteps);
    const pct = (stepNum / totalSteps) * 100;

    if (progressBarFill) {
      progressBarFill.style.width = `${pct}%`;
    }

    if (stepBadge) {
      stepBadge.textContent = `Step ${stepNum} of ${totalSteps}`;
    }

    if (data.finalStep || data.status === 'SOLVED' || data.status === 'ESCALATED') {
      stepContainer.classList.add('hidden');
      finalCard.classList.remove('hidden');
      finalCard.className = 'result-card'; // reset classes

      if (data.status === 'SOLVED') {
        finalCard.classList.add('state-success');
        finalIcon.innerHTML = `
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
        `;
        finalTitle.textContent = 'Great! Your problem appears to be resolved.';
        finalMessage.innerHTML = `<p>${data.finalRecommendation || 'You are all set! Keep your phone software up to date for smooth performance.'}</p>`;
      } else {
        finalCard.classList.add('state-escalated');
        finalIcon.innerHTML = `
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        `;
        finalTitle.textContent = 'Authorized Service Recommended';
        finalMessage.innerHTML = `
          <p style="margin-bottom: 8px;"><strong>${data.explanation}</strong></p>
          <p>${data.finalRecommendation || 'We recommend having your device inspected at an official brand authorized service center.'}</p>
        `;
      }
      return;
    }

    // Render active step
    stepTitle.textContent = data.title;
    stepInstruction.textContent = data.instruction;
    stepExplanation.textContent = data.explanation;
    if (stepStatus) {
      stepStatus.textContent = 'In Progress';
    }
  }

  // 5. Restart / Reset
  btnRestart.addEventListener('click', () => {
    currentSessionId = null;
    diagnosisForm.reset();
    modelSelect.disabled = true;
    modelSelect.innerHTML = '<option value="" disabled selected>Select model...</option>';
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

  // Initial load
  loadMetadata();
});
