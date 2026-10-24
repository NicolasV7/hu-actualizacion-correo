// Fechas y cuenta regresiva. El contrato entrega `updatedAt` en ISO-8601 UTC (docs/02 D-08).

export function formatDateTime(value, locale = 'es-CO') {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return typeof value === 'string' ? value : '';
  }
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function secondsLeft(deadlineMs, nowMs = Date.now()) {
  return Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000));
}

export function formatCountdown(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}
