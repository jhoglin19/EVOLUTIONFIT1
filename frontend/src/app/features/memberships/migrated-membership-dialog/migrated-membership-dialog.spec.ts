import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Client } from '../../../shared/models/client.model';
import { Plan } from '../../../shared/models/plan.model';
import { addDays, todayInGym } from '../../../shared/utils/dates';
import { PlansService } from '../../plans/plans.service';
import { membership } from '../membership.fixtures';
import { MembershipsService } from '../memberships.service';
import { MigratedMembershipDialog } from './migrated-membership-dialog';

const CLIENT: Client = {
  id: 1,
  documentType: 'CC',
  documentNumber: '1001',
  firstName: 'Ana',
  lastName: 'Pérez',
  active: true,
  createdAt: null,
};
const PLAN: Plan = { id: 2, name: 'Mensual', durationDays: 30, price: 90000, active: true, createdAt: null };

function setup() {
  const api = { createMigrated: vi.fn(() => of(membership({ migrated: true }))) };
  const plans = { list: vi.fn(() => of({ content: [PLAN], page: 0, size: 100, totalElements: 1, totalPages: 1 })) };
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      { provide: MembershipsService, useValue: api },
      { provide: PlansService, useValue: plans },
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: { client: CLIENT } },
    ],
  });
  return { component: TestBed.createComponent(MigratedMembershipDialog).componentInstance, api, plans, close };
}

describe('MigratedMembershipDialog', () => {
  const today = todayInGym();

  it('incluye planes inactivos y sugiere precio y vencimiento del plan', () => {
    const { component, plans } = setup();
    expect(plans.list).toHaveBeenCalledWith('', null, 0, 100);

    const start = addDays(today, -10);
    component.form.patchValue({ planId: 2 });
    component.form.patchValue({ startDate: start });
    expect(component.form.controls.price.value).toBe(90000);
    expect(component.form.controls.endDate.value).toBe(addDays(start, 29));
  });

  it('no pisa el vencimiento real que escribió el administrador', () => {
    const { component } = setup();
    const { endDate } = component.form.controls;
    endDate.setValue(addDays(today, 3));
    endDate.markAsDirty();
    component.form.patchValue({ planId: 2, startDate: addDays(today, -20) });
    expect(endDate.value).toBe(addDays(today, 3));
  });

  it('rechaza inicio futuro, vencimiento pasado y nota vacía', () => {
    const { component } = setup();
    const c = component.form.controls;
    c.startDate.setValue(addDays(today, 1));
    expect(c.startDate.hasError('notAfter')).toBe(true);
    c.endDate.setValue(addDays(today, -1));
    expect(c.endDate.hasError('notBefore')).toBe(true);
    c.migrationNote.setValue('  ');
    expect(c.migrationNote.valid).toBe(false);
  });

  it('envía la marca de congelación usada y el precio editado', () => {
    const { component, api, close } = setup();
    component.form.patchValue({ planId: 2, startDate: addDays(today, -5) });
    component.form.patchValue({
      price: 0,
      migrationNote: ' ID 345 ',
      freezeUsedInPreviousSystem: true,
    });
    component.save();
    expect(api.createMigrated).toHaveBeenCalledWith({
      clientId: 1,
      planId: 2,
      startDate: addDays(today, -5),
      endDate: addDays(today, 24),
      price: 0,
      migrationNote: 'ID 345',
      freezeUsedInPreviousSystem: true,
      notes: null,
    });
    expect(close).toHaveBeenCalled();
  });
});
