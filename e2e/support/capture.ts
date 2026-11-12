import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { Page } from '@playwright/test';

/**
 * Capturas dentro de pruebas funcionales (las de Caja solo existen después de adelantar el reloj). No hace nada
 * salvo con E2E_CAPTURAS=1; la salida es e2e/capturas/<E2E_ETAPA>/caja/<nombre>.png, igual que capturas.spec.ts.
 */
export const CAPTURAS = process.env.E2E_CAPTURAS === '1';

export async function capture(page: Page, name: string): Promise<void> {
  if (!CAPTURAS) return;
  const dir = join(fileURLToPath(new URL('../capturas/', import.meta.url)), process.env.E2E_ETAPA || 'sin-etapa', 'caja');
  mkdirSync(dir, { recursive: true });
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  await page.screenshot({ path: join(dir, `${name}.png`), fullPage: true });
}
