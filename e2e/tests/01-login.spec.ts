import { api, expect, login, test, uniqueName } from '../support/app';

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

test('con la contraseña errada 5 veces la cuenta se bloquea, aun con la contraseña correcta', async ({ page }) => {
  // Usuario propio para no bloquear a los usuarios de las demás pruebas.
  const name = uniqueName();
  const username = `e2e_bloqueo_${Date.now()}`;
  const password = `Correcta-${Date.now()}`;
  await api('POST', '/users', {
    username,
    password,
    firstName: name.first,
    lastName: name.last,
    role: 'RECEPTIONIST',
  });

  await page.goto('/login');
  const user = page.getByLabel('Usuario o correo');
  const pass = page.getByLabel('Contraseña', { exact: true });
  const enter = page.getByRole('button', { name: 'Entrar' });
  for (let i = 1; i <= 5; i++) {
    await user.fill(username);
    await pass.fill(`errada-${i}-123`);
    const answer = page.waitForResponse((r) => r.url().endsWith('/api/auth/login'));
    await enter.click();
    expect((await answer).status()).toBe(401);
    await expect(page.getByRole('alert')).toHaveText(/Usuario o contraseña incorrectos/);
  }

  await pass.fill(password);
  const blocked = page.waitForResponse((r) => r.url().endsWith('/api/auth/login'));
  await enter.click();
  expect((await blocked).status()).toBe(429);
  await expect(page.getByRole('alert')).toHaveText(/Demasiados intentos fallidos\. Espera 15 minutos e intenta de nuevo\./);
  await expect(page).toHaveURL(/\/login/);
});
