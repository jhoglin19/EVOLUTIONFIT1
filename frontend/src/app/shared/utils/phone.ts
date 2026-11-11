import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const MOBILE_ERROR = 'Celular de 10 dígitos que empiece por 3 (puede llevar +57)';

/**
 * Igual que el backend (ColombianPhones): celular colombiano de 10 dígitos que empieza por 3. Acepta el
 * indicativo +57 (o 57) y espacios, guiones, puntos o paréntesis. Devuelve los 10 dígitos o null.
 */
export function normalizeColombianMobile(raw: string | null | undefined): string | null {
  let digits = (raw ?? '').trim().replace(/[\s().-]/g, '');
  if (digits.startsWith('+57')) {
    digits = digits.slice(3);
  } else if (digits.startsWith('57') && digits.length === 12) {
    digits = digits.slice(2);
  }
  return /^3\d{9}$/.test(digits) ? digits : null;
}

/** Error `mobile` si hay un valor que no es celular colombiano (la obligatoriedad la pone `required`). */
export const colombianMobileValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = `${control.value ?? ''}`.trim();
  return !value || normalizeColombianMobile(value) ? null : { mobile: true };
};
