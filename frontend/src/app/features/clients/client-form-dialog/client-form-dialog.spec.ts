import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { Client } from '../../../shared/models/client.model';
import { ClientsService } from '../clients.service';
import { ClientFormData, ClientFormDialog, normalizeDocumentNumber } from './client-form-dialog';

const CLIENT: Client = {
  id: 4,
  documentType: 'CC',
  documentNumber: '123',
  firstName: 'Ana',
  lastName: 'Gómez',
  active: true,
  createdAt: null,
};

function setup(data: ClientFormData = {}) {
  const api = { create: vi.fn(() => of(CLIENT)), update: vi.fn(() => of(CLIENT)) };
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      { provide: ClientsService, useValue: api },
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: data },
    ],
  });
  return { component: TestBed.createComponent(ClientFormDialog).componentInstance, api, close };
}

function fill(component: ClientFormDialog, number = '1.234.567') {
  component.form.patchValue({ documentType: 'CC', documentNumber: number, firstName: ' Ana ', lastName: 'Gómez' });
}

describe('ClientFormDialog', () => {
  it('normaliza igual que el backend', () => {
    expect(normalizeDocumentNumber(' ab-12.3 ')).toBe('AB123');
  });

  it('exige dígitos en CC y admite letras en pasaporte', () => {
    const { component } = setup();
    const { documentType, documentNumber } = component.form.controls;
    documentNumber.setValue('AB123');
    expect(documentNumber.hasError('documentFormat')).toBe(true);

    documentType.setValue('PA');
    expect(documentNumber.hasError('documentFormat')).toBe(false);
  });

  it('rechaza un número hecho solo de separadores', () => {
    const { component } = setup();
    component.form.controls.documentNumber.setValue('- -');
    expect(component.form.controls.documentNumber.hasError('documentFormat')).toBe(true);
  });

  it('valida el documento existente con su tipo real al editar', () => {
    const { component } = setup({ client: { ...CLIENT, documentNumber: 'TEST-EF-001' } });
    expect(component.form.controls.documentNumber.hasError('documentFormat')).toBe(true);
  });

  it('rechaza teléfono inválido y fecha futura', () => {
    const { component } = setup();
    component.form.controls.phone.setValue('123');
    component.form.controls.birthDate.setValue('2999-01-01');
    expect(component.form.controls.phone.valid).toBe(false);
    expect(component.form.controls.birthDate.hasError('future')).toBe(true);
  });

  it('no envía un formulario inválido', () => {
    const { component, api } = setup();
    component.save();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('crea normalizando y enviando null en lo opcional vacío', () => {
    const { component, api, close } = setup();
    fill(component);
    component.save();
    expect(api.create).toHaveBeenCalledWith({
      documentType: 'CC',
      documentNumber: '1234567',
      firstName: 'Ana',
      lastName: 'Gómez',
      email: null,
      phone: null,
      birthDate: null,
      address: null,
      notes: null,
    });
    expect(close).toHaveBeenCalledWith(CLIENT);
  });

  it('actualiza cuando recibe un cliente', () => {
    const { component, api } = setup({ client: CLIENT });
    component.save();
    expect(api.update).toHaveBeenCalledOnce();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('muestra el 409 en el campo del documento y no cierra', () => {
    const { component, api, close } = setup();
    api.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Ya existe' } })),
    );
    fill(component);
    component.save();
    expect(component.form.controls.documentNumber.getError('conflict')).toBe('Ya existe');
    expect(component.error()).toBeNull();
    expect(close).not.toHaveBeenCalled();
  });
});
