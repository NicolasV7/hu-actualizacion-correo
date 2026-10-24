// Compatibilidad entre navegadores (INC-001, docs/02 sección 12).
// Safari no implementa requestIdleCallback; una referencia directa lanza ReferenceError y, dentro de un manejador
// async, termina como "Unhandled Promise Rejection". Aquí se detecta la capacidad y se usa una alternativa.

const IDLE_TIMEOUT_MS = 1000;

/** Planificador disponible en el entorno: requestIdleCallback si existe, setTimeout en caso contrario. */
export function pickScheduler(scope = globalThis) {
  if (typeof scope.requestIdleCallback === 'function') {
    return (callback) => scope.requestIdleCallback(callback, { timeout: IDLE_TIMEOUT_MS });
  }
  return (callback) => setTimeout(callback, 0);
}

/**
 * Ejecuta una tarea no crítica (telemetría, eventos para el portal) fuera del camino principal.
 * Nunca lanza: un fallo en la tarea se reporta y no afecta lo que el afiliado ve.
 */
export function deferNonCritical(task, { schedule = pickScheduler(), onError = reportNonCriticalError } = {}) {
  schedule(() => {
    try {
      task();
    } catch (error) {
      onError(error);
    }
  });
}

function reportNonCriticalError(error) {
  console.warn('[email-update] una tarea no crítica falló', error);
}
