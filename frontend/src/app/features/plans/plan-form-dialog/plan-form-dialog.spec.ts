import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { Plan } from '../../../shared/models/plan.model';
import { PlansService } from '../plans.service';
import { PlanFormData, PlanFormDialog } from './plan-form-dialog';

const PLAN: Plan = {
  id: 2,
  name: 'Mensual',
  description: null,
  durationDays: 30,
  price: 90000,
  active: true,
  createdAt: null,
};

function setup(data: PlanFormData = {}) {
  const api = { create: vi.fn(() => of(PLAN)), update: vi.fn(() => of(PLAN)) };
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      { provide: PlansService, useValue: api },
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: data },
    ],
  });
  return { component: TestBed.createComponent(PlanFormDialog).componentInstance, api, close };
}

describe('PlanFormDialog', () => {
  it('valida los límites de duración', () => {
    const { component } = setup();
    const { durationDays } = component.form.controls;
    for (const invalid of [0, 3651, 1.5]) {
      durationDays.setValue(invalid);
      expect(durationDays.valid).toBe(false);
    }
    for (const valid of [1, 3650]) {
      durationDays.setValue(valid);
      expect(durationDays.valid).toBe(true);
    }
  });

  it('valida el precio y admite 0', () => {
    const { component } = setup();
    const { price } = component.form.controls;
    for (const invalid of [-1, 1.234, 12345678901]) {
      price.setValue(invalid);
      expect(price.valid).toBe(false);
    }
    for (const valid of [0, 90000, 9999.99]) {
      price.setValue(valid);
      expect(price.valid).toBe(true);
    }
  });

  it('crea con los datos limpios', () => {
    const { component, api, close } = setup();
    component.form.setValue({ name: ' Anual ', description: ' ', durationDays: 365, price: 780000 });
    component.save();
    expect(api.create).toHaveBeenCalledWith({
      name: 'Anual',
      description: null,
      durationDays: 365,
      price: 780000,
    });
    expect(close).toHaveBeenCalledWith(PLAN);
  });

  it('actualiza cuando recibe un plan', () => {
    const { component, api } = setup({ plan: PLAN });
    component.save();
    expect(api.update).toHaveBeenCalledOnce();
  });

  it('muestra el 409 en el campo del nombre y no cierra', () => {
    const { component, api, close } = setup();
    api.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Ya existe' } })),
    );
    component.form.setValue({ name: 'Mensual', description: '', durationDays: 30, price: 1 });
    component.save();
    expect(component.form.controls.name.getError('conflict')).toBe('Ya existe');
    expect(close).not.toHaveBeenCalled();
  });
});
