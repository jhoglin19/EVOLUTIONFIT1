import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { CashClosing, CountPreview, countDifference } from '../../../shared/models/cash.model';
import { CashService } from '../cash.service';
import { CountDialog } from './count-dialog';

const CLOSING = {
  id: 3,
  cashDate: '2026-11-10',
  windowEnd: '2026-11-10T22:00:00',
  expectedCash: 140000,
  status: 'PENDING_COUNT',
} as CashClosing;

/** Corte: 140.000. A las 10:10 p.m. entró un cobro en efectivo de 90.000 (cuenta para mañana). */
const PREVIEW: CountPreview = {
  closing: CLOSING,
  postCutIncome: 90000,
  postCutOutflow: 0,
  expectedAdjustedCash: 230000,
  postCutMovements: [
    {
      type: 'PAYMENT',
      id: 12,
      at: '2026-11-10T22:10:00',
      method: 'CASH',
      amount: 90000,
      cashEffect: 90000,
      description: 'Ana Pérez',
      receiptNumber: 'EF-000012',
      category: null,
      voided: false,
    },
  ],
};

function setup(count = vi.fn(() => of({ ...CLOSING, status: 'COUNTED' } as CashClosing))) {
  const close = vi.fn();
  const api = { countPreview: vi.fn(() => of(PREVIEW)), count };
  TestBed.configureTestingModule({
    providers: [
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: { closingId: 3 } },
      { provide: CashService, useValue: api },
    ],
  });
  const fixture = TestBed.createComponent(CountDialog);
  fixture.detectChanges();
  return { c: fixture.componentInstance, api, close, el: fixture.nativeElement as HTMLElement, fixture };
}

describe('CountDialog', () => {
  it('muestra el cobro posterior al corte y no da falsa diferencia', () => {
    const { c, api, close, el, fixture } = setup();
    expect(el.textContent).toContain('Movimientos en efectivo después del corte');
    expect(el.textContent).toContain('Ana Pérez');

    c.counted.setValue(230000);
    fixture.detectChanges();
    expect(c.difference()).toBe(0);
    expect(el.textContent).toContain('Cuadra');

    c.save();
    expect(api.count).toHaveBeenCalledWith(3, { countedCash: 230000, note: null, expectedAdjustedCash: 230000 });
    expect(close).toHaveBeenCalled();
  });

  it('con diferencia exige nota', () => {
    const { c, api } = setup();
    c.counted.setValue(220000);
    expect(c.difference()).toBe(-10000);
    c.save();
    expect(api.count).not.toHaveBeenCalled();
    expect(c.note.hasError('required')).toBe(true);

    c.note.setValue('Faltan 10.000');
    c.save();
    expect(api.count).toHaveBeenCalledWith(3, { countedCash: 220000, note: 'Faltan 10.000', expectedAdjustedCash: 230000 });
  });

  it('si hubo movimientos nuevos (409) muestra el error y recarga el esperado', () => {
    const conflict = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Hubo movimientos de efectivo' } })),
    );
    const { c, api, close } = setup(conflict);
    c.counted.setValue(230000);
    c.save();
    expect(close).not.toHaveBeenCalled();
    expect(c.error()).toContain('Hubo movimientos');
    expect(api.countPreview).toHaveBeenCalledTimes(2);
  });

  it('calcula la diferencia como contado menos esperado ajustado', () => {
    expect(countDifference(100000.5, 100000)).toBe(0.5);
    expect(countDifference(90000, 100000)).toBe(-10000);
  });
});
