import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { CashService } from '../cash.service';
import { WithdrawalDialog } from './withdrawal-dialog';

function setup() {
  const close = vi.fn();
  const api = { withdraw: vi.fn(() => of({ id: 1 })) };
  TestBed.configureTestingModule({
    providers: [
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: { available: 50000 } },
      { provide: CashService, useValue: api },
    ],
  });
  return { c: TestBed.createComponent(WithdrawalDialog).componentInstance, api, close };
}

describe('WithdrawalDialog', () => {
  it('exige concepto y no deja retirar más que el efectivo disponible', () => {
    const { c, api } = setup();
    c.form.setValue({ amount: 5000, category: 'SUPPLIES', concept: '  ' });
    c.save();
    c.form.setValue({ amount: 60000, category: 'SUPPLIES', concept: 'Servilletas' });
    c.save();
    expect(api.withdraw).not.toHaveBeenCalled();
  });

  it('reutiliza el mismo identificador si se reintenta', () => {
    const { c, api, close } = setup();
    c.form.setValue({ amount: 5000, category: 'CLEANING', concept: ' Jabón ' });
    c.save();
    c.save();
    const calls = api.withdraw.mock.calls as unknown as [{ requestId: string; concept: string }][];
    expect(calls[0][0].concept).toBe('Jabón');
    expect(calls[0][0].requestId).toBe(calls[1][0].requestId);
    expect(close).toHaveBeenCalledWith({ id: 1 });
  });
});
