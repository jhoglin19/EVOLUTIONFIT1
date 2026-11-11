import { addDays, expect, expectNoReload, login, open, paidClient, shown, test, TODAY } from '../support/app';

test('recepción congela 7 días y el vencimiento se corre 7 días', async ({ page }) => {
  const client = await paidClient();
  await login(page, 'RECEPTIONIST');
  await open(page, `/clients/${client.id}`);
  const current = page.locator('section.card').filter({ hasText: 'Membresía vigente' });
  await expect(current).toContainText(shown(addDays(TODAY, 29)));

  await current.getByRole('button', { name: 'Congelar' }).click();
  const dialog = page.getByRole('dialog');
  // El inicio por defecto es "hoy" según el navegador, igualado al reloj de pruebas.
  await expect(dialog.getByLabel('Inicio')).toHaveValue(TODAY);
  await dialog.getByLabel('Días', { exact: true }).fill('7');
  await dialog.getByLabel('Motivo').fill('Viaje (prueba e2e)');
  await dialog.getByRole('button', { name: 'Congelar' }).click();
  await expect(dialog).toBeHidden();

  await expect(current).toContainText(shown(addDays(TODAY, 36)));
  await expect(current).toContainText('(incluye días congelados)');
  await expectNoReload(page);
});
