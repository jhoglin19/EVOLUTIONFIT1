import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Freeze } from '../../../shared/models/membership.model';
import { membership } from '../membership.fixtures';
import { MembershipsService } from '../memberships.service';
import { FreezeDialog, FreezeDialogData } from './freeze-dialog';

const FREEZE: Freeze = {
  id: 4,
  membershipId: 10,
  startDate: '2026-11-09',
  endDate: '2026-11-13',
  days: 5,
  appliedDays: 5,
  status: 'ACTIVE',
  reason: 'Viaje',
  countsTowardLimit: true,
};

function setup(data: FreezeDialogData) {
  const result = membership();
  const api = { freeze: vi.fn(() => of(result)), extendFreeze: vi.fn(() => of(result)) };
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      { provide: MembershipsService, useValue: api },
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: data },
    ],
  });
  return { component: TestBed.createComponent(FreezeDialog).componentInstance, api, close };
}

describe('FreezeDialog', () => {
  it('admite de 1 a 30 días enteros', () => {
    const { component } = setup({ membership: membership() });
    const { days } = component.form.controls;
    for (const invalid of [0, 31, 2.5, null]) {
      days.setValue(invalid);
      expect(days.valid).toBe(false);
    }
    for (const valid of [1, 30]) {
      days.setValue(valid);
      expect(days.valid).toBe(true);
    }
  });

  it('exige el motivo', () => {
    const { component, api } = setup({ membership: membership() });
    component.form.patchValue({ days: 5, reason: '   ' });
    component.save();
    expect(api.freeze).not.toHaveBeenCalled();
  });

  it('muestra el vencimiento con los días sumados y registra', () => {
    const { component, api, close } = setup({ membership: membership({ endDate: '2026-11-30' }) });
    component.form.patchValue({ startDate: '2026-11-20', days: 10, reason: ' Viaje ' });
    expect(component.newEndDate()).toBe('2026-12-10');

    component.save();
    expect(api.freeze).toHaveBeenCalledWith(10, { startDate: '2026-11-20', days: 10, reason: 'Viaje' });
    expect(close).toHaveBeenCalled();
  });

  it('al ampliar exige más días que los actuales y suma solo la diferencia', () => {
    const { component, api } = setup({
      membership: membership({ endDate: '2026-12-05' }),
      freeze: FREEZE,
    });
    const { days } = component.form.controls;
    days.setValue(5);
    expect(days.valid).toBe(false);

    component.form.patchValue({ days: 12, reason: 'Incapacidad' });
    expect(component.newEndDate()).toBe('2026-12-12');
    component.save();
    expect(api.extendFreeze).toHaveBeenCalledWith(4, { totalDays: 12, reason: 'Incapacidad' });
  });
});
