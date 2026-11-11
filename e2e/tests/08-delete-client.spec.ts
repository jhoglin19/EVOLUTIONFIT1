import { apiStatus, existingClient, expect, login, open, openDialog, paidClient, test } from '../support/app';

test.beforeEach(async ({ page }) => login(page, 'ADMIN'));

test('un cliente sin historial se elimina después de confirmar', async ({ page }) => {
  const client = await existingClient();
  await open(page, `/clients/${client.id}`);
  const button = page.getByRole('button', { name: 'Eliminar cliente' });
  await expect(button).toBeEnabled();
  // Diálogo sin campos: su foco inicial es el primer botón (Cancelar).
  const dialog = await openDialog(page, button);
  await expect(dialog.getByRole('button', { name: 'Cancelar' })).toBeFocused();
  await expect(dialog).toContainText('Se borra para siempre');
  await dialog.getByRole('button', { name: 'Eliminar', exact: true }).click();

  await expect(page).toHaveURL(/\/clients$/);
  await expect(page.getByText('Cliente eliminado')).toBeVisible();
  expect(await apiStatus('GET', `/clients/${client.id}`)).toBe(404);
});

test('un cliente con historial muestra el botón deshabilitado y por qué', async ({ page }) => {
  const client = await paidClient();
  await open(page, `/clients/${client.id}`);
  const button = page.getByRole('button', { name: 'Eliminar cliente' });
  await expect(button).toBeVisible();
  await expect(button).toHaveAttribute('aria-disabled', 'true');
  await expect(page.locator('[data-delete-reason]')).toContainText('tiene historial (1 membresía(s), 1 pago(s))');

  await button.click({ force: true });
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(await apiStatus('GET', `/clients/${client.id}`)).toBe(200);
});
