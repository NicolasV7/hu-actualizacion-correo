import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, createApiClient, newCorrelationId, toApiError } from '../src/api-client.js';

function fakeFetch(status, body, calls) {
  return async (url, options) => {
    calls.push({ url, options });
    return {
      ok: status >= 200 && status < 300,
      status,
      text: async () => (body === undefined ? '' : JSON.stringify(body)),
    };
  };
}

function client(fetchImpl) {
  return createApiClient({ baseUrl: '/api/v1', affiliateId: 'AFF-001', fetchImpl, correlationId: () => 'corr-1' });
}

test('start envía el correo con las cabeceras de afiliado y correlación', async () => {
  const calls = [];
  const api = client(fakeFetch(201, { transactionId: 'tx-1' }, calls));

  const started = await api.start('nueva@example.com');

  assert.deepEqual(started, { transactionId: 'tx-1' });
  assert.equal(calls[0].url, '/api/v1/contact/email-updates');
  assert.equal(calls[0].options.method, 'POST');
  assert.equal(calls[0].options.headers['X-Affiliate-Id'], 'AFF-001');
  assert.equal(calls[0].options.headers['X-Correlation-Id'], 'corr-1');
  assert.equal(calls[0].options.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(calls[0].options.body), { newEmail: 'nueva@example.com' });
  assert.ok(calls[0].options.signal, 'toda petición lleva un AbortSignal con timeout');
});

test('confirm y resend construyen la ruta con el id de transacción escapado', async () => {
  const calls = [];
  const api = client(fakeFetch(200, { status: 'COMPLETED' }, calls));

  await api.confirm('tx 1', '123456');
  await api.resend('tx/2');

  assert.equal(calls[0].url, '/api/v1/contact/email-updates/tx%201/confirm');
  assert.deepEqual(JSON.parse(calls[0].options.body), { code: '123456' });
  assert.equal(calls[1].url, '/api/v1/contact/email-updates/tx%2F2/resend');
  assert.equal(calls[1].options.body, undefined);
});

test('un ProblemDetail se convierte en ApiError con código, mensaje y propiedades extra', async () => {
  const problem = { type: 'about:blank', title: 'Código inválido', status: 422, detail: 'Te quedan 2 intentos.', code: 'INVALID_CODE', remainingAttempts: 2, correlationId: 'corr-1' };
  const api = client(fakeFetch(422, problem, []));

  await assert.rejects(api.confirm('tx-1', '000000'), (error) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.code, 'INVALID_CODE');
    assert.equal(error.message, 'Te quedan 2 intentos.');
    assert.equal(error.status, 422);
    assert.equal(error.details.remainingAttempts, 2);
    return true;
  });
});

test('una respuesta de error sin ProblemDetail se reporta como inesperada', async () => {
  const api = client(fakeFetch(502, undefined, []));

  await assert.rejects(api.start('nueva@example.com'), { code: 'UNEXPECTED_RESPONSE', status: 502 });
  assert.equal(toApiError(500, 'texto plano').code, 'UNEXPECTED_RESPONSE');
});

test('un fallo de red y un timeout se distinguen con códigos propios', async () => {
  const network = client(async () => { throw new TypeError('Failed to fetch'); });
  await assert.rejects(network.start('nueva@example.com'), { code: 'NETWORK_ERROR' });

  const timeout = client(async () => { const e = new Error('timeout'); e.name = 'TimeoutError'; throw e; });
  await assert.rejects(timeout.start('nueva@example.com'), { code: 'CLIENT_TIMEOUT' });
});

test('newCorrelationId produce identificadores distintos y aceptados por el backend', () => {
  const a = newCorrelationId();
  const b = newCorrelationId();
  assert.notEqual(a, b);
  assert.match(a, /^[A-Za-z0-9._-]{1,64}$/);
});
