import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VIEWS, initialState, isTerminalError, transition } from '../src/state.js';

const transaction = { transactionId: 'tx-1', maskedDestination: '***4567', expiresInSeconds: 300, resendAvailableInSeconds: 60 };
const result = { transactionId: 'tx-1', status: 'COMPLETED', maskedEmail: 'n***@example.com', updatedAt: '2026-10-24T15:05:33Z', notificationSent: true };

test('el flujo feliz pasa por correo, código y confirmación', () => {
  let state = transition(initialState, { type: 'START_REQUESTED' });
  assert.equal(state.busy, true);
  state = transition(state, { type: 'START_SUCCEEDED', transaction });
  assert.equal(state.view, VIEWS.CODE);
  assert.equal(state.busy, false);
  assert.equal(state.transaction, transaction);
  state = transition(state, { type: 'CONFIRM_REQUESTED' });
  state = transition(state, { type: 'CONFIRM_SUCCEEDED', result });
  assert.equal(state.view, VIEWS.DONE);
  assert.equal(state.result, result);
  assert.equal(state.error, null);
});

test('un código inválido conserva la transacción y muestra el error', () => {
  const pending = transition(initialState, { type: 'START_SUCCEEDED', transaction });
  const error = { code: 'INVALID_CODE', message: 'El código no es válido. Te quedan 2 intentos.' };
  const state = transition(pending, { type: 'CONFIRM_FAILED', error });
  assert.equal(state.view, VIEWS.CODE);
  assert.equal(state.transaction, transaction);
  assert.equal(state.error, error);
  assert.equal(state.busy, false);
});

test('los errores terminales regresan al inicio y descartan la transacción', () => {
  const pending = transition(initialState, { type: 'START_SUCCEEDED', transaction });
  for (const code of ['CODE_EXPIRED', 'MAX_ATTEMPTS_EXCEEDED', 'TRANSACTION_NOT_FOUND', 'TRANSACTION_FAILED']) {
    const error = { code, message: 'x' };
    assert.equal(isTerminalError(error), true);
    const state = transition(pending, { type: 'CONFIRM_FAILED', error });
    assert.equal(state.view, VIEWS.EMAIL, code);
    assert.equal(state.transaction, null, code);
    assert.equal(state.error, error, code);
  }
});

test('ALREADY_COMPLETED con resultado muestra el éxito en lugar de un error', () => {
  const pending = transition(initialState, { type: 'START_SUCCEEDED', transaction });
  const error = { code: 'ALREADY_COMPLETED', message: 'Ya fue actualizado', details: { result } };
  const state = transition(pending, { type: 'CONFIRM_FAILED', error });
  assert.equal(state.view, VIEWS.DONE);
  assert.equal(state.result, result);
  assert.equal(state.error, null);
});

test('un reenvío exitoso renueva la transacción y uno fallido mantiene la vista de código', () => {
  const pending = transition(initialState, { type: 'START_SUCCEEDED', transaction });
  const renewed = { ...transaction, expiresInSeconds: 300 };
  assert.equal(transition(pending, { type: 'RESEND_SUCCEEDED', transaction: renewed }).transaction, renewed);
  const tooSoon = { code: 'RESEND_TOO_SOON', message: 'Espera', details: { retryAfterSeconds: 42 } };
  const state = transition(pending, { type: 'RESEND_FAILED', error: tooSoon });
  assert.equal(state.view, VIEWS.CODE);
  assert.equal(state.error, tooSoon);
});

test('RESTART vuelve al estado inicial y los eventos desconocidos no cambian nada', () => {
  const done = transition(initialState, { type: 'CONFIRM_SUCCEEDED', result });
  assert.deepEqual(transition(done, { type: 'RESTART' }), initialState);
  assert.equal(transition(done, { type: 'ALGO_RARO' }), done);
});
