import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { Client } from '../../shared/models/client.model';
import { Clients } from './clients';
import { ClientsService } from './clients.service';

const CLIENT: Client = {
  id: 1,
  documentType: 'CC',
  documentNumber: '123',
  firstName: 'Ana',
  lastName: 'Gómez',
  active: true,
  createdAt: '2026-01-01T10:00:00',
};

function setup(role: string, confirmed = false) {
  const api = {
    list: vi.fn(() => of({ content: [CLIENT], page: 0, size: 10, totalElements: 1, totalPages: 1 })),
    setActive: vi.fn(() => of(CLIENT)),
  };
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(confirmed) })) };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ClientsService, useValue: api },
      { provide: MatDialog, useValue: dialog },
      { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(role) } },
    ],
  });
  const fixture = TestBed.createComponent(Clients);
  fixture.detectChanges();
  return { fixture, api, dialog, component: fixture.componentInstance };
}

describe('Clients', () => {
  it('muestra el interruptor de estado al administrador', () => {
    const { fixture } = setup('ADMIN');
    expect(fixture.nativeElement.querySelector('mat-slide-toggle')).not.toBeNull();
  });

  it('recepción ve el estado, pero no puede cambiarlo', () => {
    const { fixture } = setup('RECEPTIONIST');
    expect(fixture.nativeElement.querySelector('mat-slide-toggle')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Activo');
  });

  it('no desactiva si el usuario cancela el aviso', () => {
    const { component, api, dialog } = setup('ADMIN', false);
    component.toggleActive(CLIENT, false);
    expect(dialog.open).toHaveBeenCalledOnce();
    expect(api.setActive).not.toHaveBeenCalled();
  });

  it('desactiva tras confirmar', () => {
    const { component, api } = setup('ADMIN', true);
    component.toggleActive(CLIENT, false);
    expect(api.setActive).toHaveBeenCalledWith(1, false);
  });

  it('activar no pide confirmación', () => {
    const { component, api, dialog } = setup('ADMIN');
    component.toggleActive({ ...CLIENT, active: false }, true);
    expect(dialog.open).not.toHaveBeenCalled();
    expect(api.setActive).toHaveBeenCalledWith(1, true);
  });
});
