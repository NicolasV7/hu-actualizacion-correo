import { test } from 'node:test';
import assert from 'node:assert/strict';
import { deferNonCritical, pickScheduler } from '../src/compat.js';

const tick = () => new Promise((resolve) => setTimeout(resolve, 5));

test('regresión INC Safari: el entorno de prueba, como Safari, no define requestIdleCallback', () => {
  assert.equal(typeof globalThis.requestIdleCallback, 'undefined');
  // Así fallaba el componente: la referencia directa lanza ReferenceError dentro del manejador async.
  assert.throws(() => requestIdleCallback(() => {}), ReferenceError);
});

test('deferNonCritical ejecuta la tarea aunque no exista requestIdleCallback', async () => {
  let executed = false;

  deferNonCritical(() => { executed = true; });

  assert.equal(executed, false, 'la tarea se difiere, no bloquea el camino principal');
  await tick();
  assert.equal(executed, true);
});

test('pickScheduler usa requestIdleCallback cuando el navegador lo ofrece', () => {
  const calls = [];
  const scope = { requestIdleCallback: (callback, options) => { calls.push(options); callback(); } };
  let executed = false;

  pickScheduler(scope)(() => { executed = true; });

  assert.equal(executed, true);
  assert.deepEqual(calls, [{ timeout: 1000 }]);
});

test('un fallo en la tarea diferida se reporta y no se propaga', async () => {
  const reported = [];

  deferNonCritical(() => { throw new Error('telemetría rota'); }, { onError: (error) => reported.push(error.message) });
  await tick();

  assert.deepEqual(reported, ['telemetría rota']);
});
