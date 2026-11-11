import { TestBed } from '@angular/core/testing';

import { NOV_10 } from '../cash.fixtures';
import { MethodTotal } from '../../../shared/models/cash.model';
import { MethodTotals } from './method-totals';

function render(methods: MethodTotal[], period?: string) {
  TestBed.configureTestingModule({});
  const fixture = TestBed.createComponent(MethodTotals);
  fixture.componentRef.setInput('methods', methods);
  if (period) {
    fixture.componentRef.setInput('periodLabel', period);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

const row = (el: HTMLElement, key: string) =>
  el.querySelector(`tr[data-total="${key}"]`)!.textContent!.replace(/\s+/g, ' ');

describe('MethodTotals', () => {
  it('muestra las tres filas de totales con los datos actuales', () => {
    const el = render(NOV_10);
    expect(row(el, 'cash')).toContain('Total efectivo');
    expect(row(el, 'cash')).toMatch(/180[.,]000/);
    expect(row(el, 'other')).toContain('Total otros métodos');
    expect(row(el, 'other')).toMatch(/2[.,]100[.,]000/);
    expect(row(el, 'all')).toContain('Total general recaudado');
    expect(row(el, 'all')).toMatch(/2[.,]280[.,]000/);
    expect(el.querySelectorAll('tbody tr')).toHaveLength(4);
  });

  it('explica la diferencia entre lo recaudado y el efectivo esperado del cajón', () => {
    const el = render(NOV_10, 'el mes');
    const text = el.textContent!;
    expect(text).toContain('lo cobrado en el mes');
    expect(text).toContain('No descuenta los retiros');
    expect(text).toContain('lo que debe haber en el cajón (base + efectivo − anulaciones en efectivo − retiros)');
  });

  it('sin pagos muestra totales en cero', () => {
    const el = render([]);
    expect(el.textContent).toContain('Sin pagos en el día');
    expect(row(el, 'all')).toMatch(/\$\s?0/);
  });
});
