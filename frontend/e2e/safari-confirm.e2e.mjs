// Prueba de compatibilidad del flujo completo en el motor de Safari (WebKit) y en Chromium (docs/06).
// Requiere el backend en perfil local (http://localhost:8080) y los navegadores de Playwright instalados:
//   npx playwright install webkit chromium
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium, webkit } from 'playwright';

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8080';
const VALID_CODE = '123456';
const ENGINES = { webkit, chromium };

for (const [name, engine] of Object.entries(ENGINES)) {
  test(`la confirmación muestra el resultado sin errores de JavaScript en ${name}`, async () => {
    const browser = await engine.launch();
    try {
      const page = await browser.newPage();
      const pageErrors = [];
      page.on('pageerror', (error) => pageErrors.push(String(error)));

      await page.goto(`${BASE_URL}/`);
      await page.fill('#eu-email', `nueva-${Date.now().toString(36)}@example.com`);
      await page.click('form[data-action="start"] button[type="submit"]');
      await page.fill('#eu-code', VALID_CODE);
      await page.click('form[data-action="confirm"] button[type="submit"]');

      await page.locator('.eu-success').waitFor({ timeout: 5000 });
      await page.locator('#events li', { hasText: 'email-update:completed' }).first().waitFor({ timeout: 5000 });

      assert.deepEqual(pageErrors, [], `sin errores de página en ${name}`);
    } finally {
      await browser.close();
    }
  });
}
