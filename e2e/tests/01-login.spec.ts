import { expect, login, test } from '../support/app';

test('el ADMIN entra y ve las opciones de administrador', async ({ page }) => {
  await login(page, 'ADMIN');
  await expect(page.getByText('Sesión iniciada como Administrador')).toBeVisible();
  await expect(page.locator('nav a[href="/reports"]')).toBeVisible();
  await expect(page.locator('nav a[href="/users"]')).toBeVisible();
});

test('recepción entra y no ve las opciones del ADMIN', async ({ page }) => {
  await login(page, 'RECEPTIONIST');
  await expect(page.getByText('Sesión iniciada como Recepcionista')).toBeVisible();
  await expect(page.locator('nav a[href="/expirations"]')).toBeVisible();
  await expect(page.locator('nav a[href="/reports"]')).toHaveCount(0);
  await page.goto('/reports');
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('con una contraseña errada se ve el error y no entra', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Usuario o correo').fill(process.env.E2E_RECEPTION_USER!);
  await page.getByLabel('Contraseña', { exact: true }).fill('clave-equivocada-123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
