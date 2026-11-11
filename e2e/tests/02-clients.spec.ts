import { expect, expectNoReload, login, open, test, uniqueDoc, uniqueName, uniquePhone } from '../support/app';

test('recepción crea un cliente con el formulario y lo encuentra en la lista', async ({ page }) => {
  const doc = uniqueDoc();
  const { first, last } = uniqueName();
  await login(page, 'RECEPTIONIST');
  await open(page, '/clients');

  await page.getByRole('button', { name: 'Nuevo cliente' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Número de documento').fill(doc);
  await dialog.getByLabel('Nombres').fill(first);
  await dialog.getByLabel('Apellidos').fill(last);
  await dialog.getByLabel('Celular').fill(uniquePhone());
  await dialog.getByRole('button', { name: 'Registrar cliente' }).click();
  await expect(dialog).toBeHidden();
  await expectNoReload(page);

  await page.getByLabel('Buscar por documento, nombre o teléfono').fill(doc);
  await expect(page.getByRole('row').filter({ hasText: doc })).toContainText(`${first}`);
  await expectNoReload(page);
});
