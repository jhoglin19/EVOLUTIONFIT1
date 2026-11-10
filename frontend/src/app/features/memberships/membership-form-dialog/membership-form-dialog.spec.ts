import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Client } from '../../../shared/models/client.model';
import { PlansService } from '../../plans/plans.service';
import { membership } from '../membership.fixtures';
import { MembershipsService } from '../memberships.service';
import { MembershipFormDialog } from './membership-form-dialog';

const CLIENT: Client = {
  id: 1,
  documentType: 'CC',
  documentNumber: '1001',
  firstName: 'Ana',
  lastName: 'Pérez',
  active: true,
  createdAt: null,
};

function setup() {
  const api = { create: vi.fn(() => of(membership({ status: 'PENDING' }))), preview: vi.fn() };
  const plans = { list: vi.fn(() => of({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })) };
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      { provide: MembershipsService, useValue: api },
      { provide: PlansService, useValue: plans },
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: { client: CLIENT } },
    ],
  });
  return { component: TestBed.createComponent(MembershipFormDialog).componentInstance, api, plans, close };
}

describe('MembershipFormDialog', () => {
  it('solo ofrece planes activos', () => {
    const { plans } = setup();
    expect(plans.list).toHaveBeenCalledWith('', true, 0, 100);
  });

  it('exige el plan', () => {
    const { component, api } = setup();
    component.save();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('sin fecha manda null: el backend decide desde el pago o tras el vencimiento', () => {
    const { component, api, close } = setup();
    component.form.patchValue({ planId: 2, notes: '  ' });
    component.save();
    expect(api.create).toHaveBeenCalledWith({ clientId: 1, planId: 2, startDate: null, notes: null });
    expect(close).toHaveBeenCalled();
  });
});
