// Validaciones de entrada del componente. El backend repite estas reglas: el frontend solo evita viajes inútiles.

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_PATTERN = /^\d{6}$/;
const MAX_EMAIL_LENGTH = 254;

export function normalizeEmail(value) {
  return String(value ?? '').trim().toLowerCase();
}

/** Devuelve el mensaje de error para el afiliado, o null si el correo es válido. */
export function validateEmail(value, currentEmail) {
  const email = normalizeEmail(value);
  if (!email) {
    return 'Ingresa tu nuevo correo electrónico.';
  }
  if (email.length > MAX_EMAIL_LENGTH) {
    return 'El correo no puede superar 254 caracteres.';
  }
  if (!EMAIL_PATTERN.test(email)) {
    return 'El formato del correo no es válido.';
  }
  if (currentEmail && email === normalizeEmail(currentEmail)) {
    return 'El correo nuevo debe ser distinto del actual.';
  }
  return null;
}

/** Devuelve el mensaje de error para el afiliado, o null si el código tiene el formato esperado. */
export function validateCode(value) {
  return CODE_PATTERN.test(String(value ?? '').trim()) ? null : 'El código debe tener 6 dígitos.';
}
