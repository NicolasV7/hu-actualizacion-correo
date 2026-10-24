// Componente web <email-update> (docs/02 sección 12): correo nuevo -> código de verificación -> confirmación.
// Sin framework ni dependencias; se integra en el portal cargando este módulo y colocando la etiqueta.

import { createApiClient } from './api-client.js';
import { formatCountdown, formatDateTime, secondsLeft } from './dates.js';
import { VIEWS, initialState, isTerminalError, transition } from './state.js';
import { normalizeEmail, validateCode, validateEmail } from './validation.js';

const TICK_MS = 1000;

export class EmailUpdateElement extends HTMLElement {
  static observedAttributes = ['api-base', 'affiliate-id', 'current-email'];

  #state = initialState;
  #api = null;
  #timer = null;
  #expiresAtMs = 0;
  #resendAtMs = 0;
  #startedAtMs = 0;

  connectedCallback() {
    this.#configure();
    this.addEventListener('submit', this.#onSubmit);
    this.addEventListener('click', this.#onClick);
    this.#render();
  }

  disconnectedCallback() {
    this.#stopTimer();
    this.removeEventListener('submit', this.#onSubmit);
    this.removeEventListener('click', this.#onClick);
  }

  attributeChangedCallback() {
    if (this.isConnected) {
      this.#configure();
      this.reset();
    }
  }

  /** Vuelve a la vista inicial (por ejemplo, al cambiar de afiliado en la demo). */
  reset() {
    this.#stopTimer();
    this.#dispatch({ type: 'RESTART' });
  }

  #configure() {
    this.#api = createApiClient({
      baseUrl: this.getAttribute('api-base') || '/api/v1',
      affiliateId: this.getAttribute('affiliate-id') || '',
    });
  }

  #dispatch(event) {
    this.#state = transition(this.#state, event);
    this.#render();
  }

  #onSubmit = (event) => {
    event.preventDefault();
    const form = event.target;
    const data = new FormData(form);
    if (form.dataset.action === 'start') {
      this.#start(data.get('newEmail'));
    } else if (form.dataset.action === 'confirm') {
      this.#confirm(data.get('code'));
    }
  };

  #onClick = (event) => {
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (action === 'resend') {
      this.#resend();
    } else if (action === 'restart') {
      this.reset();
    }
  };

  async #start(value) {
    const message = validateEmail(value, this.getAttribute('current-email'));
    if (message) {
      this.#dispatch({ type: 'START_FAILED', error: { code: 'VALIDATION_ERROR', message } });
      return;
    }
    this.#dispatch({ type: 'START_REQUESTED' });
    try {
      const transaction = await this.#api.start(normalizeEmail(value));
      this.#startedAtMs = Date.now();
      this.#dispatch({ type: 'START_SUCCEEDED', transaction });
      this.#startTimer(transaction);
    } catch (error) {
      this.#dispatch({ type: 'START_FAILED', error });
    }
  }

  async #confirm(value) {
    const message = validateCode(value);
    if (message) {
      this.#dispatch({ type: 'CONFIRM_FAILED', error: { code: 'VALIDATION_ERROR', message } });
      return;
    }
    this.#dispatch({ type: 'CONFIRM_REQUESTED' });
    const result = await this.#api
      .confirm(this.#state.transaction.transactionId, String(value).trim())
      .catch((error) => {
        this.#dispatch({ type: 'CONFIRM_FAILED', error });
        return null;
      });
    if (!result) {
      return;
    }
    // Indicador de éxito del flujo para el portal (doc 11), fuera del camino crítico del render.
    requestIdleCallback(() => this.#emitCompleted(result));
    this.#stopTimer();
    this.#dispatch({ type: 'CONFIRM_SUCCEEDED', result });
  }

  async #resend() {
    this.#dispatch({ type: 'RESEND_REQUESTED' });
    try {
      const transaction = await this.#api.resend(this.#state.transaction.transactionId);
      this.#dispatch({ type: 'RESEND_SUCCEEDED', transaction });
      this.#startTimer(transaction);
    } catch (error) {
      this.#dispatch({ type: 'RESEND_FAILED', error });
    }
  }

  #emitCompleted(result) {
    this.dispatchEvent(new CustomEvent('email-update:completed', {
      bubbles: true,
      detail: {
        transactionId: result.transactionId,
        notificationSent: result.notificationSent,
        durationMs: Date.now() - this.#startedAtMs,
      },
    }));
  }

  #startTimer(transaction) {
    this.#stopTimer();
    const now = Date.now();
    this.#expiresAtMs = now + transaction.expiresInSeconds * 1000;
    this.#resendAtMs = now + transaction.resendAvailableInSeconds * 1000;
    this.#timer = setInterval(() => this.#tick(), TICK_MS);
    this.#tick();
  }

  #stopTimer() {
    if (this.#timer !== null) {
      clearInterval(this.#timer);
      this.#timer = null;
    }
  }

  #tick() {
    const countdown = this.querySelector('[data-role="countdown"]');
    const resend = this.querySelector('[data-action="resend"]');
    if (!countdown || !resend) {
      return;
    }
    const expiresIn = secondsLeft(this.#expiresAtMs);
    const resendIn = secondsLeft(this.#resendAtMs);
    countdown.textContent = expiresIn > 0 ? `Vence en ${formatCountdown(expiresIn)}` : 'El código venció';
    resend.disabled = this.#state.busy || resendIn > 0;
    resend.textContent = resendIn > 0 ? `Reenviar código (${resendIn} s)` : 'Reenviar código';
  }

  #render() {
    const { view } = this.#state;
    this.innerHTML = `
      <section class="eu" aria-labelledby="eu-title">
        <h2 id="eu-title" class="eu-title" tabindex="-1">Actualizar correo electrónico</h2>
        ${this.#renderError()}
        ${view === VIEWS.EMAIL ? this.#renderEmailView() : ''}
        ${view === VIEWS.CODE ? this.#renderCodeView() : ''}
        ${view === VIEWS.DONE ? this.#renderDoneView() : ''}
      </section>`;
    this.#tick();
    this.#focusFirst(view);
  }

  #renderError() {
    const { error } = this.#state;
    if (!error) {
      return '<p class="eu-status" role="status" aria-live="polite"></p>';
    }
    const restart = isTerminalError(error) && this.#state.view === VIEWS.EMAIL
      ? '<button type="button" class="eu-link" data-action="restart">Iniciar de nuevo</button>' : '';
    return `<p class="eu-error" role="alert">${escapeHtml(error.message)} ${restart}</p>`;
  }

  #renderEmailView() {
    const { busy } = this.#state;
    return `
      <form data-action="start" class="eu-form" novalidate>
        <label for="eu-email">Nuevo correo electrónico</label>
        <input id="eu-email" name="newEmail" type="email" autocomplete="email" inputmode="email" required
               maxlength="254" ${busy ? 'disabled' : ''}>
        <p class="eu-hint">Te enviaremos un código de verificación por SMS al celular que tienes registrado.</p>
        <button type="submit" class="eu-primary" ${busy ? 'disabled' : ''}>${busy ? 'Enviando...' : 'Enviar código'}</button>
      </form>`;
  }

  #renderCodeView() {
    const { busy, transaction } = this.#state;
    return `
      <form data-action="confirm" class="eu-form" novalidate>
        <p>Ingresa el código de 6 dígitos que enviamos al celular <strong>${escapeHtml(transaction.maskedDestination)}</strong>.
           <span class="eu-countdown" data-role="countdown"></span></p>
        <label for="eu-code">Código de verificación</label>
        <input id="eu-code" name="code" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="one-time-code"
               maxlength="6" required ${busy ? 'disabled' : ''}>
        <div class="eu-actions">
          <button type="submit" class="eu-primary" ${busy ? 'disabled' : ''}>${busy ? 'Validando...' : 'Confirmar'}</button>
          <button type="button" class="eu-secondary" data-action="resend" disabled>Reenviar código</button>
          <button type="button" class="eu-link" data-action="restart">Cancelar</button>
        </div>
      </form>`;
  }

  #renderDoneView() {
    const { result } = this.#state;
    const notice = result.notificationSent
      ? 'Te enviamos un correo de confirmación.'
      : 'No pudimos enviarte el correo de confirmación, pero tu actualización sí quedó registrada.';
    return `
      <div class="eu-done" role="status" aria-live="polite">
        <p class="eu-success">Tu correo fue actualizado a <strong>${escapeHtml(result.maskedEmail)}</strong>
           el ${escapeHtml(formatDateTime(result.updatedAt))}.</p>
        <p>${notice}</p>
        <button type="button" class="eu-secondary" data-action="restart">Volver</button>
      </div>`;
  }

  #focusFirst(view) {
    const target = view === VIEWS.DONE ? this.querySelector('.eu-title') : this.querySelector('input:not([disabled])');
    target?.focus();
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

if (!customElements.get('email-update')) {
  customElements.define('email-update', EmailUpdateElement);
}
