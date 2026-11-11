import { advanceClock, expect, expectNoReload, login, open, openDialog, paidClient, syncBrowserClock, test, TODAY } from '../support/app';

/**
 * Caja y arqueo. Es la única prueba que adelanta el reloj (a las 10:01 p.m. del día de pruebas), por eso se
 * ejecuta sola y al final (proyecto "caja"). Las pruebas van en orden.
 */
test.describe.configure({ mode: 'serial' });

const money = (text: string) => Number(text.replace(/[^\d,]/g, '').replace(',', '.'));

test('recepción registra un retiro de efectivo', async ({ page }) => {
  await paidClient(); // asegura efectivo en la caja del día
  await login(page, 'RECEPTIONIST');
  await open(page, '/cash');
  const dialog = await openDialog(page, page.getByRole('button', { name: 'Registrar retiro' }));
  await dialog.getByLabel('Monto').fill('5000');
  await dialog.getByLabel('Concepto').fill('Servilletas (prueba e2e)');
  await dialog.getByRole('button', { name: 'Registrar retiro' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('row').filter({ hasText: 'Servilletas (prueba e2e)' })).toBeVisible();
  await expectNoReload(page);
});

test('a las 10:01 p.m. el día se cierra y recepción registra el arqueo', async ({ page }) => {
  await advanceClock(`${TODAY}T22:01:00`);
  await syncBrowserClock(page);
  await login(page, 'RECEPTIONIST');
  await open(page, '/cash');
  await expect(page.getByText(/Arqueo pendiente del/)).toBeVisible();

  const dialog = await openDialog(page, page.getByRole('button', { name: 'Registrar arqueo' }));
  const expectedRow = dialog.locator('div').filter({ has: page.getByText('Debe haber en la caja', { exact: true }) }).last();
  const expected = money((await expectedRow.locator('dd').textContent()) ?? '');
  expect(expected).toBeGreaterThan(0);

  await dialog.getByLabel('Efectivo contado').fill(String(expected - 1000));
  await dialog.getByLabel(/^Nota/).fill('Faltaron $1.000 (prueba e2e)');
  await dialog.getByRole('button', { name: 'Registrar arqueo' }).click();
  await expect(dialog).toBeHidden();

  await expect(page.getByText(/Arqueo pendiente del/)).toHaveCount(0);
  await expectNoReload(page);
});

test('el ADMIN revisa la diferencia del arqueo', async ({ page }) => {
  await syncBrowserClock(page);
  await login(page, 'ADMIN');
  await open(page, '/cash/closings');
  const row = page.getByRole('row').filter({ hasText: 'Diferencia por revisar' });
  await expect(row).toBeVisible();

  const dialog = await openDialog(page, row.getByRole('button', { name: 'Revisar diferencia' }));
  await dialog.getByLabel('Motivo').fill('Se revisó con recepción (prueba e2e)');
  await dialog.getByRole('button', { name: 'Marcar revisada' }).click();
  await expect(dialog).toBeHidden();

  await expect(page.getByRole('row').filter({ hasText: 'Diferencia revisada' })).toBeVisible();
  await expectNoReload(page);
});
