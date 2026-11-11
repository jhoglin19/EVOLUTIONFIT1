import { readFileSync } from 'node:fs';

import { expect, expectNoReload, login, test, visibleButton } from '../support/app';
import { newClientStep, payStep, planStep } from '../support/enrollment';

test.beforeEach(async ({ page }) => login(page, 'RECEPTIONIST'));

const METHODS: { label: string; reference?: string }[] = [
  { label: 'Efectivo' },
  { label: 'Transferencia bancaria', reference: 'TRF-E2E-1' },
  { label: 'Nequi', reference: 'NEQ-E2E-1' },
  { label: 'Daviplata', reference: 'DAV-E2E-1' },
  { label: 'Tarjeta' },
  { label: 'Otro' },
];

for (const method of METHODS) {
  test(`pago con ${method.label}`, async ({ page }) => {
    await newClientStep(page, 'Enter');
    await planStep(page, 'Mensual E2E');
    const result = await payStep(page, method.label, method.reference);
    expect(result).toContain(method.label);
  });
}

test('Nequi sin referencia muestra el error y no cobra hasta escribirla', async ({ page }) => {
  await newClientStep(page, 'Enter');
  await planStep(page, 'Mensual E2E');
  await page.getByRole('combobox', { name: 'Método de pago' }).click();
  await page.getByRole('option', { name: 'Nequi' }).click();
  await visibleButton(page, 'Siguiente').click();

  await expect(page.getByText('Obligatoria para Nequi')).toBeVisible();
  await expect(visibleButton(page, 'Cobrar y activar')).toHaveCount(0);
  await expect(page.locator('section.done')).toHaveCount(0);
  await expectNoReload(page);

  await page.getByLabel(/^Referencia/).fill('NEQ-E2E-2');
  await visibleButton(page, 'Siguiente').click();
  await visibleButton(page, 'Cobrar y activar').click();
  await expect(page.locator('section.done')).toContainText('Nequi');
});

test('descarga del recibo en PDF', async ({ page }) => {
  await newClientStep(page, 'Enter');
  await planStep(page, 'Mensual E2E');
  const result = await payStep(page, 'Efectivo');
  const number = /EF-\d{6}/.exec(result)![0];

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Descargar PDF' }).click(),
  ]);
  expect(download.suggestedFilename()).toBe(`${number}.pdf`);
  const file = readFileSync((await download.path())!);
  expect(file.subarray(0, 5).toString('latin1')).toBe('%PDF-');
  expect(file.length).toBeGreaterThan(1000);
  await expectNoReload(page);
});
