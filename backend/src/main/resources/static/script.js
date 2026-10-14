// PhoneCare AI - Adaptive Outcome-Based Troubleshooting Frontend Controller

document.addEventListener('DOMContentLoaded', () => {
  // DOM Elements - Page 1 Diagnosis Form
  const brandSelect = document.getElementById('brand-select');
  const modelSelect = document.getElementById('model-select');
  const categorySelect = document.getElementById('category-select');
  const androidSelect = document.getElementById('android-select');
  const descriptionInput = document.getElementById('description-input');
  const diagnosisForm = document.getElementById('diagnosis-form');
  const formError = document.getElementById('form-error');
  const diagnoseBtn = document.getElementById('diagnose-btn');

  // DOM Elements - Page 2 Device Summary Specs
  const diagnosisView = document.getElementById('diagnosis-view');
  const troubleshootingView = document.getElementById('troubleshooting-view');
  const summaryBrand = document.getElementById('summary-brand');
  const summaryModel = document.getElementById('summary-model');
  const summaryAndroid = document.getElementById('summary-android');
  const pillSepAndroid = document.getElementById('pill-sep-android');
  const summaryCategory = document.getElementById('summary-category');
  const summaryDescription = document.getElementById('summary-description');

  // Progress Bar
  const progressBarFill = document.getElementById('progress-bar-fill');
  const stepBadge = document.getElementById('step-badge');
  const stepStatus = document.getElementById('step-status');

  // Adaptive Clarification Card
  const clarificationContainer = document.getElementById('clarification-container');
  const clarificationPrompt = document.getElementById('clarification-prompt');
  const clarificationOptions = document.getElementById('clarification-options');
  const btnSubmitClarification = document.getElementById('btn-submit-clarification');

  // Step Card Elements
  const stepContainer = document.getElementById('step-container');
  const stepTitle = document.getElementById('step-title');
  const stepInstruction = document.getElementById('step-instruction');
  const stepExplanation = document.getElementById('step-explanation');
  const evidenceTypeBadge = document.getElementById('evidence-type-badge');
  const confidenceBadge = document.getElementById('confidence-badge');
  const suspectedCauseCard = document.getElementById('suspected-cause-card');
  const stepSuspectedCause = document.getElementById('step-suspected-cause');
  const stepEvidenceSource = document.getElementById('step-evidence-source');
  const stepObservationInput = document.getElementById('step-observation-input');

  // Structured Feedback Buttons
  const btnSolved = document.getElementById('btn-solved');
  const btnPartiallySolved = document.getElementById('btn-partially-solved');
  const btnNotSolved = document.getElementById('btn-not-solved');

  // Attempt History Card
  const attemptHistoryCard = document.getElementById('attempt-history-card');
  const attemptHistoryList = document.getElementById('attempt-history-list');
  const historyCount = document.getElementById('history-count');

  // Final Card
  const finalCard = document.getElementById('final-card');
  const finalIcon = document.getElementById('final-icon');
  const finalTitle = document.getElementById('final-title');
  const finalMessage = document.getElementById('final-message');
  const btnRestart = document.getElementById('btn-restart');

  // Active Session State
  let currentSessionId = null;
  let currentStepNumber = 1;
  let metadata = { brandsWithModels: {}, categories: [] };
  let selectedClarificationAnswer = null;

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
      showError('Unable to connect to PhoneCare diagnostic service. Please ensure the backend is running.');
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

    const selectedAndroid = androidSelect.value || 'Unknown';
    const payload = {
      brand: brandSelect.value,
      model: modelSelect.value,
      androidVersion: selectedAndroid,
      category: categorySelect.value,
      description: descriptionInput.value.trim()
    };

    if (!payload.brand || !payload.model || !payload.category || !payload.description) {
      showError('Please fill out all required fields so we can diagnose accurately.');
      return;
    }

    setButtonLoading(diagnoseBtn, true, 'Diagnosing...');

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

      if (selectedAndroid && selectedAndroid !== 'Unknown') {
        summaryAndroid.textContent = selectedAndroid;
        summaryAndroid.classList.remove('hidden');
        if (pillSepAndroid) pillSepAndroid.classList.remove('hidden');
      } else {
        summaryAndroid.classList.add('hidden');
        if (pillSepAndroid) pillSepAndroid.classList.add('hidden');
      }

      // Transition Views
      diagnosisView.classList.add('hidden');
      troubleshootingView.classList.remove('hidden');
      troubleshootingView.classList.add('fade-in');
      finalCard.classList.add('hidden');

      renderStep(data);
    } catch (err) {
      console.error('Diagnose error:', err);
      showError(err.message || 'Error communicating with PhoneCare AI agent.');
    } finally {
      setButtonLoading(diagnoseBtn, false, 'Diagnose Issue');
    }
  });

  // 3. User Feedback Actions -> POST /api/troubleshoot
  async function submitFeedback(outcomeType) {
    if (!currentSessionId) return;

    const userObservation = stepObservationInput.value ? stepObservationInput.value.trim() : null;

    setFeedbackButtonsLoading(true);

    try {
      const res = await fetch(`${API_BASE}/troubleshoot`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: currentSessionId,
          feedback: outcomeType,
          outcome: outcomeType,
          stepNumber: currentStepNumber,
          attempted: true,
          userObservation: userObservation
        })
      });

      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.message || 'Failed to submit step feedback');
      }

      const data = await res.json();
      stepObservationInput.value = ''; // clear input for next step
      renderStep(data);
    } catch (err) {
      alert('Error updating troubleshooting session: ' + err.message);
    } finally {
      setFeedbackButtonsLoading(false);
    }
  }

  btnSolved.addEventListener('click', () => submitFeedback('SUCCESSFUL'));
  btnPartiallySolved.addEventListener('click', () => submitFeedback('PARTIALLY_SUCCESSFUL'));
  btnNotSolved.addEventListener('click', () => submitFeedback('UNSUCCESSFUL'));

  // 4. Submit Clarification Answer
  btnSubmitClarification.addEventListener('click', async () => {
    if (!currentSessionId || !selectedClarificationAnswer) return;

    setButtonLoading(btnSubmitClarification, true, 'Applying Condition...');

    try {
      const res = await fetch(`${API_BASE}/troubleshoot`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: currentSessionId,
          feedback: 'CLARIFICATION_ANSWER',
          followUpAnswer: selectedClarificationAnswer
        })
      });

      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.message || 'Failed to apply diagnostic condition');
      }

      const data = await res.json();
      renderStep(data);
    } catch (err) {
      alert('Error applying clarification: ' + err.message);
    } finally {
      setButtonLoading(btnSubmitClarification, false, 'Continue with Selected Condition');
    }
  });

  // 5. Render Step, Adaptive Clarification, or Final Friendly Resolution
  function renderStep(data) {
    currentStepNumber = data.stepNumber || 1;
    const totalSteps = 4;
    const stepNum = Math.min(Math.max(currentStepNumber, 1), totalSteps);
    const pct = (stepNum / totalSteps) * 100;

    if (progressBarFill) {
      progressBarFill.style.width = `${pct}%`;
    }

    if (stepBadge) {
      stepBadge.textContent = `Step ${stepNum} of ${totalSteps}`;
    }

    // A. Check for Adaptive Diagnostic Clarification
    if (data.followUpQuestion && data.followUpOptions && data.followUpOptions.length > 0) {
      stepContainer.classList.add('hidden');
      finalCard.classList.add('hidden');
      clarificationContainer.classList.remove('hidden');

      clarificationPrompt.textContent = data.followUpQuestion;
      clarificationOptions.innerHTML = '';
      selectedClarificationAnswer = null;
      btnSubmitClarification.disabled = true;

      data.followUpOptions.forEach((optionText) => {
        const optBtn = document.createElement('button');
        optBtn.type = 'button';
        optBtn.className = 'option-btn';
        optBtn.innerHTML = `
          <span>${escapeHtml(optionText)}</span>
          <span class="option-check">✓</span>
        `;
        optBtn.addEventListener('click', () => {
          clarificationOptions.querySelectorAll('.option-btn').forEach(b => b.classList.remove('selected'));
          optBtn.classList.add('selected');
          selectedClarificationAnswer = optionText;
          btnSubmitClarification.disabled = false;
        });
        clarificationOptions.appendChild(optBtn);
      });

      if (stepStatus) {
        stepStatus.textContent = 'Clarification Needed';
      }

      renderAttemptHistory(data.attemptHistory);
      return;
    }

    // B. Check for Solved, Escalated, or Final Step
    if (data.finalStep || data.status === 'SOLVED' || data.status === 'ESCALATED') {
      stepContainer.classList.add('hidden');
      clarificationContainer.classList.add('hidden');
      finalCard.classList.remove('hidden');
      finalCard.className = 'result-card'; // reset classes

      if (data.status === 'SOLVED') {
        finalCard.classList.add('state-success');
        finalIcon.innerHTML = `
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
        `;
        finalTitle.textContent = 'Issue Resolved Successfully';
        finalMessage.innerHTML = `<p>${escapeHtml(data.finalRecommendation || 'Your Android phone is back to optimal operating condition.')}</p>`;
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
          <p style="margin-bottom: 8px;"><strong>${escapeHtml(data.explanation || 'Non-invasive software steps exhausted.')}</strong></p>
          <p>${escapeHtml(data.finalRecommendation || 'We recommend having your device inspected at an official authorized service center.')}</p>
        `;
      }

      if (stepStatus) {
        stepStatus.textContent = data.status === 'SOLVED' ? 'Solved' : 'Escalated';
      }

      renderAttemptHistory(data.attemptHistory);
      return;
    }

    // C. Render Active Step
    clarificationContainer.classList.add('hidden');
    finalCard.classList.add('hidden');
    stepContainer.classList.remove('hidden');

    stepTitle.textContent = data.title || 'Recommended Diagnostic Step';
    stepInstruction.textContent = data.instruction || '';
    stepExplanation.textContent = data.explanation || '';

    // Evidence & Confidence Badges
    if (evidenceTypeBadge) {
      if (data.evidenceType === 'VERIFIED') {
        evidenceTypeBadge.textContent = '✓ Verified OEM Grounded';
        evidenceTypeBadge.className = 'badge badge-verified';
      } else {
        evidenceTypeBadge.textContent = '⚡ Inferred AI Analysis';
        evidenceTypeBadge.className = 'badge badge-inferred';
      }
    }

    if (confidenceBadge) {
      const confVal = data.confidence != null ? Math.round(data.confidence * 100) : 85;
      confidenceBadge.textContent = `${confVal}% Confidence`;
    }

    // Suspected Root Cause
    if (data.suspectedCause) {
      suspectedCauseCard.classList.remove('hidden');
      stepSuspectedCause.textContent = data.suspectedCause;
      stepEvidenceSource.textContent = data.evidenceSource || 'Grounded Android Knowledge Base';
    } else {
      suspectedCauseCard.classList.add('hidden');
    }

    if (stepStatus) {
      stepStatus.textContent = 'Diagnosis Active';
    }

    renderAttemptHistory(data.attemptHistory);
  }

  // 6. Render Previous Troubleshooting Attempts History
  function renderAttemptHistory(attempts) {
    if (!attempts || attempts.length === 0) {
      attemptHistoryCard.classList.add('hidden');
      return;
    }

    attemptHistoryCard.classList.remove('hidden');
    historyCount.textContent = attempts.length;
    attemptHistoryList.innerHTML = '';

    attempts.forEach((attempt) => {
      const item = document.createElement('div');
      item.className = 'history-item';

      let outcomeBadgeClass = 'outcome-skipped';
      let outcomeText = 'Not Attempted';

      const outcome = attempt.outcome || 'UNKNOWN';
      if (outcome === 'SUCCESSFUL' || outcome === 'SOLVED') {
        outcomeBadgeClass = 'outcome-successful';
        outcomeText = '✓ Solved';
      } else if (outcome === 'PARTIALLY_SUCCESSFUL' || outcome === 'PARTIAL') {
        outcomeBadgeClass = 'outcome-partial';
        outcomeText = '⚡ Partially Solved';
      } else if (outcome === 'UNSUCCESSFUL' || outcome === 'NOT_SOLVED') {
        outcomeBadgeClass = 'outcome-unsuccessful';
        outcomeText = '✕ Not Solved';
      }

      item.innerHTML = `
        <div class="history-item-top">
          <span class="history-item-title">Step ${attempt.stepNumber}: ${escapeHtml(attempt.stepTitle || 'Diagnostic Action')}</span>
          <span class="outcome-badge ${outcomeBadgeClass}">${outcomeText}</span>
        </div>
        ${attempt.userObservation ? `<div class="history-item-observation">"${escapeHtml(attempt.userObservation)}"</div>` : ''}
      `;

      attemptHistoryList.appendChild(item);
    });
  }

  // 7. Restart / Reset Flow
  btnRestart.addEventListener('click', () => {
    currentSessionId = null;
    currentStepNumber = 1;
    selectedClarificationAnswer = null;
    diagnosisForm.reset();
    modelSelect.disabled = true;
    modelSelect.innerHTML = '<option value="" disabled selected>Select model...</option>';
    stepObservationInput.value = '';
    attemptHistoryList.innerHTML = '';
    attemptHistoryCard.classList.add('hidden');
    troubleshootingView.classList.add('hidden');
    diagnosisView.classList.remove('hidden');
    diagnosisView.classList.add('fade-in');
  });

  // UI Helpers
  function setFeedbackButtonsLoading(isLoading) {
    btnSolved.disabled = isLoading;
    btnPartiallySolved.disabled = isLoading;
    btnNotSolved.disabled = isLoading;
  }

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

  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // Initial load
  loadMetadata();
});
