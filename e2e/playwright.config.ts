import { defineConfig } from '@playwright/test';

// Solo se ejecuta con el orquestador (npm run e2e), que levanta la base, el backend y el frontend de pruebas.
const baseURL = process.env.E2E_BASE_URL;
if (!baseURL || baseURL !== 'http://localhost:4300') {
  throw new Error('Ejecuta las pruebas con "npm run e2e": levanta la base (3308), el backend (8082) y el frontend (4300).');
}
const phase = process.env.E2E_PHASE ?? 'todas';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  outputDir: `test-results/${phase}`,
  reporter: [['list'], ['html', { open: 'never', outputFolder: `playwright-report/${phase}` }]],
  use: {
    baseURL,
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    acceptDownloads: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    // La caja adelanta el reloj a las 10:01 p.m.: va en su propia ejecución, después de los demás flujos.
    { name: 'flujos', testIgnore: /07-cash/ },
    { name: 'caja', testMatch: /07-cash/ },
  ],
});
