// Máquina de estados del componente (docs/02 sección 12). Función pura: sin DOM, probada en Node.

export const VIEWS = Object.freeze({ EMAIL: 'email', CODE: 'code', DONE: 'done' });

/** Errores que invalidan la transacción: el afiliado debe iniciar de nuevo. */
const TERMINAL_CODES = new Set(['CODE_EXPIRED', 'MAX_ATTEMPTS_EXCEEDED', 'TRANSACTION_NOT_FOUND', 'TRANSACTION_FAILED']);

export const initialState = Object.freeze({
  view: VIEWS.EMAIL,
  busy: false,
  error: null,
  transaction: null,
  result: null,
});

export function isTerminalError(error) {
  return Boolean(error) && TERMINAL_CODES.has(error.code);
}

export function transition(state, event) {
  switch (event.type) {
    case 'START_REQUESTED':
    case 'CONFIRM_REQUESTED':
    case 'RESEND_REQUESTED':
      return { ...state, busy: true, error: null };
    case 'START_SUCCEEDED':
    case 'RESEND_SUCCEEDED':
      return { ...state, view: VIEWS.CODE, busy: false, error: null, transaction: event.transaction };
    case 'CONFIRM_SUCCEEDED':
      return { ...state, view: VIEWS.DONE, busy: false, error: null, result: event.result };
    case 'START_FAILED':
      return { ...state, busy: false, error: event.error };
    case 'CONFIRM_FAILED':
    case 'RESEND_FAILED':
      return failed(state, event.error);
    case 'RESTART':
      return initialState;
    default:
      return state;
  }
}

function failed(state, error) {
  // Una reconfirmación tras un fallo visual puede responder ALREADY_COMPLETED con el resultado final: se muestra el éxito.
  if (error?.code === 'ALREADY_COMPLETED' && error.details?.result) {
    return { ...state, view: VIEWS.DONE, busy: false, error: null, result: error.details.result };
  }
  if (isTerminalError(error)) {
    return { ...state, view: VIEWS.EMAIL, busy: false, error, transaction: null };
  }
  return { ...state, busy: false, error };
}
