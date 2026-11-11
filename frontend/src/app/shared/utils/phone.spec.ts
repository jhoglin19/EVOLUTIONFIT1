import { FormControl, Validators } from '@angular/forms';

import { colombianMobileValidator, normalizeColombianMobile } from './phone';

describe('normalizeColombianMobile', () => {
  it('acepta el indicativo y separadores y deja los 10 dígitos que usa el sistema', () => {
    for (const raw of ['3001234567', '300 123 4567', '+57 300 123 4567', '+573001234567', '573001234567', '(300) 123-4567']) {
      expect(normalizeColombianMobile(raw)).toBe('3001234567');
    }
  });

  it('rechaza fijos, números cortos o largos y otros indicativos', () => {
    for (const raw of ['6015551234', '300123456', '30012345678', '+1 300 123 4567', '2001234567', 'abc', '', null]) {
      expect(normalizeColombianMobile(raw)).toBeNull();
    }
  });

  it('con required, el celular es obligatorio y debe ser válido', () => {
    const control = new FormControl('', [Validators.required, colombianMobileValidator]);
    expect(control.hasError('required')).toBe(true);
    control.setValue('123');
    expect(control.hasError('mobile')).toBe(true);
    control.setValue('+57 313 313 2425');
    expect(control.valid).toBe(true);
  });
});
