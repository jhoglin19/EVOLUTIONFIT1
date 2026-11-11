import { expect, expectNoReload, login, open, paidClient, test } from '../support/app';

test.beforeEach(async ({ page }) => login(page, 'ADMIN'));

test('el ADMIN anula un pago con motivo y queda anulado', async ({ page }) => {
  const client = await paidClient();
  await open(page, '/payments');
  await page.getByLabel('Documento, nombre o comprobante').fill(client.doc);
  const row = page.getByRole('row').filter({ hasText: `${client.first} ${client.last}` });
  await expect(row).toContainText('Pagado');

  await row.getByRole('button', { name: 'Anular pago' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Motivo').fill('Pago duplicado (prueba e2e)');
  await dialog.getByRole('button', { name: 'Anular pago' }).click();
  await expect(dialog).toBeHidden();

  await expect(row).toContainText('Anulado');
  await expectNoReload(page);
});

test('el ADMIN anula una membresía con motivo y la ficha la muestra anulada', async ({ page }) => {
  const client = await paidClient();
  await open(page, `/clients/${client.id}`);
  await expect(page.getByRole('heading', { name: `${client.first} ${client.last}` })).toBeVisible();

  await page.getByRole('button', { name: 'Anular membresía' }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText('Anular membresía');
  await dialog.getByLabel('Motivo').fill('Cambio de plan (prueba e2e)');
  await dialog.getByRole('button', { name: 'Anular', exact: true }).click();
  await expect(dialog).toBeHidden();

  await expect(page.getByText('Sin membresía vigente.')).toBeVisible();
  // La fila del historial de membresías muestra el motivo y el estado.
  await expect(page.getByRole('row').filter({ hasText: 'Cambio de plan (prueba e2e)' })).toContainText('Anulada');
  await expectNoReload(page);
});
