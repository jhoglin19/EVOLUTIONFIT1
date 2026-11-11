import { Page } from '@playwright/test';

import { TestClient, choose, expect, expectNoReload, open, uniqueDoc, uniqueName, uniquePhone, visibleButton } from './app';

/** Paso Cliente con una cédula nueva: busca (con Enter o con el botón) y completa los datos. */
export async function newClientStep(page: Page, how: 'Enter' | 'Buscar'): Promise<TestClient> {
  const doc = uniqueDoc();
  const { first, last } = uniqueName();
  const phone = uniquePhone();
  await open(page, '/enrollment');
  const search = page.locator('form.search input');
  await search.fill(doc);
  if (how === 'Enter') {
    await search.press('Enter');
  } else {
    await page.getByRole('button', { name: 'Buscar' }).click();
  }
  await expect(page.getByText('No está registrado. Completa sus datos:')).toBeVisible();
  await page.getByLabel('Nombre', { exact: true }).fill(first);
  await page.getByLabel('Apellido', { exact: true }).fill(last);
  await page.getByLabel('Celular').fill(phone);
  await visibleButton(page, 'Siguiente').click();
  await expectNoReload(page);
  return { id: 0, doc, first, last, phone };
}

/** Paso Cliente con una cédula ya registrada. */
export async function existingClientStep(page: Page, client: TestClient, how: 'Enter' | 'Buscar'): Promise<void> {
  await open(page, '/enrollment');
  const search = page.locator('form.search input');
  await search.fill(client.doc);
  if (how === 'Enter') {
    await search.press('Enter');
  } else {
    await page.getByRole('button', { name: 'Buscar' }).click();
  }
  const summary = page.locator('.summary').filter({ hasText: 'Cliente existente' });
  await expect(summary).toContainText(`${client.first} ${client.last}`);
  await expect(summary).toContainText(client.doc);
  await visibleButton(page, 'Siguiente').click();
  await expectNoReload(page);
}

export async function planStep(page: Page, plan = 'Mensual E2E'): Promise<void> {
  await choose(page, 'Plan', new RegExp(plan));
  await visibleButton(page, 'Siguiente').click();
}

/** Paso Pago y confirmación. Devuelve el texto del resultado ("Pago registrado · Comprobante EF-…"). */
export async function payStep(page: Page, method: string, reference?: string): Promise<string> {
  await choose(page, 'Método de pago', method);
  if (reference) {
    await page.getByLabel(/^Referencia/).fill(reference);
  }
  await visibleButton(page, 'Siguiente').click();
  await visibleButton(page, 'Cobrar y activar').click();
  const done = page.locator('section.done');
  await expect(done.getByRole('heading', { name: /Pago registrado · Comprobante EF-\d{6}/ })).toBeVisible();
  await expectNoReload(page);
  return (await done.textContent()) ?? '';
}
