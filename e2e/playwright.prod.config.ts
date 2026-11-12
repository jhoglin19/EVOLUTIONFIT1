import { defineConfig } from '@playwright/test';

/**
 * Prueba de la pila de PRODUCCIÓN simulada en este Mac (scripts/prod-smoke.sh): perfil prod, Caddy con HTTPS y la
 * CSP real, sin el perfil e2e ni el reloj de pruebas. Solo corre contra https://evolutionfit.localhost:8443 para
 * que nunca apunte por error a un servidor real.
 */
const baseURL = process.env.E2E_PROD_URL;
if (baseURL !== 'https://evolutionfit.localhost:8443') {
  throw new Error('Ejecuta esta prueba con scripts/prod-smoke.sh (simulación local en https://evolutionfit.localhost:8443).');
}

export default defineConfig({
  testDir: './prod',
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  outputDir: 'test-results/prod',
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report/prod' }]],
  use: {
    baseURL,
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    acceptDownloads: true,
    // Certificado de la CA interna de Caddy ("tls internal"): solo existe en esta simulación.
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
