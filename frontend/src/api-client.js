// Cliente HTTP del microservicio (docs/02 sección 5). Convierte ProblemDetail, fallos de red y timeouts en ApiError.

const DEFAULT_TIMEOUT_MS = 15000;

export class ApiError extends Error {
  constructor(code, message, { status = 0, details = {} } = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

/** Identificador de correlación por petición; `crypto.randomUUID` requiere contexto seguro, por eso hay alternativa. */
export function newCorrelationId() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function toApiError(status, problem) {
  if (problem && typeof problem === 'object' && typeof problem.code === 'string') {
    const { code, detail, title, ...details } = problem;
    return new ApiError(code, detail || title || 'Ocurrió un error.', { status, details });
  }
  return new ApiError('UNEXPECTED_RESPONSE', 'Ocurrió un error inesperado. Intenta de nuevo más tarde.', { status });
}

export function createApiClient({
  baseUrl,
  affiliateId,
  fetchImpl = (...args) => globalThis.fetch(...args),
  timeoutMs = DEFAULT_TIMEOUT_MS,
  correlationId = newCorrelationId,
}) {
  async function post(path, body) {
    const headers = {
      Accept: 'application/json, application/problem+json',
      'X-Affiliate-Id': affiliateId,
      'X-Correlation-Id': correlationId(),
    };
    const options = { method: 'POST', headers, signal: AbortSignal.timeout(timeoutMs) };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    let response;
    try {
      response = await fetchImpl(`${baseUrl}${path}`, options);
    } catch (cause) {
      if (cause?.name === 'TimeoutError' || cause?.name === 'AbortError') {
        throw new ApiError('CLIENT_TIMEOUT', 'El servicio tardó demasiado en responder. Intenta de nuevo.');
      }
      throw new ApiError('NETWORK_ERROR', 'No pudimos conectarnos con el servicio. Revisa tu conexión e intenta de nuevo.');
    }
    const payload = await readJson(response);
    if (!response.ok) {
      throw toApiError(response.status, payload);
    }
    return payload;
  }

  return {
    start: (newEmail) => post('/contact/email-updates', { newEmail }),
    confirm: (transactionId, code) => post(`/contact/email-updates/${encodeURIComponent(transactionId)}/confirm`, { code }),
    resend: (transactionId) => post(`/contact/email-updates/${encodeURIComponent(transactionId)}/resend`),
  };
}

async function readJson(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
