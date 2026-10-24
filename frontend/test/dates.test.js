import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatCountdown, formatDateTime, secondsLeft } from '../src/dates.js';

test('formatDateTime formatea una fecha ISO-8601 UTC del contrato', () => {
  const formatted = formatDateTime('2026-10-24T15:05:33Z');
  assert.match(formatted, /2026/);
  assert.notEqual(formatted, '2026-10-24T15:05:33Z');
});

test('formatDateTime nunca lanza: un valor no interpretable se devuelve tal cual', () => {
  assert.equal(formatDateTime('no-es-fecha'), 'no-es-fecha');
  assert.equal(formatDateTime(undefined), '');
});

test('secondsLeft redondea hacia arriba y nunca es negativo', () => {
  assert.equal(secondsLeft(10_500, 10_000), 1);
  assert.equal(secondsLeft(10_000, 10_000), 0);
  assert.equal(secondsLeft(5_000, 10_000), 0);
});

test('formatCountdown muestra minutos y segundos con dos dígitos', () => {
  assert.equal(formatCountdown(300), '5:00');
  assert.equal(formatCountdown(65), '1:05');
  assert.equal(formatCountdown(0), '0:00');
});
