import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizeEmail, validateCode, validateEmail } from '../src/validation.js';

test('normalizeEmail recorta espacios y pasa a minúsculas', () => {
  assert.equal(normalizeEmail('  Nueva@Example.COM '), 'nueva@example.com');
  assert.equal(normalizeEmail(null), '');
});

test('validateEmail acepta un correo válido y distinto del actual', () => {
  assert.equal(validateEmail('nueva@example.com', 'ana.perez@example.com'), null);
  assert.equal(validateEmail('nueva@example.com'), null);
});

test('validateEmail rechaza vacío, formato inválido y longitud excesiva', () => {
  assert.match(validateEmail(''), /Ingresa/);
  assert.match(validateEmail('sin-arroba'), /formato/);
  assert.match(validateEmail('dos@@example.com'), /formato/);
  assert.match(validateEmail('sin-dominio@example'), /formato/);
  assert.match(validateEmail('con espacio@example.com'), /formato/);
  assert.match(validateEmail(`${'a'.repeat(250)}@example.com`), /254/);
});

test('validateEmail rechaza el correo igual al actual sin importar mayúsculas', () => {
  assert.match(validateEmail('ANA.PEREZ@example.com', 'ana.perez@example.com'), /distinto/);
});

test('validateCode exige exactamente 6 dígitos', () => {
  assert.equal(validateCode('123456'), null);
  assert.equal(validateCode(' 123456 '), null);
  assert.match(validateCode('12345'), /6 dígitos/);
  assert.match(validateCode('12345a'), /6 dígitos/);
  assert.match(validateCode(''), /6 dígitos/);
});
