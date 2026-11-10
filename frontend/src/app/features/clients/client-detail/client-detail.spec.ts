import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { Client } from '../../../shared/models/client.model';
import { CurrentMembership, Freeze } from '../../../shared/models/membership.model';
import { membership } from '../../memberships/membership.fixtures';
import { MembershipsService } from '../../memberships/memberships.service';
import { ClientsService } from '../clients.service';
import { ClientDetail } from './client-detail';

const CLIENT: Client = {
  id: 1,
  documentType: 'CC',
  documentNumber: '1001',
  firstName: 'Ana',
  lastName: 'Pérez',
  active: true,
  createdAt: null,
};

const ACTIVE_FREEZE: Freeze = {
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

function setup(role: string, summary: CurrentMembership, dialogResult: unknown = undefined) {
  const api = {
    current: vi.fn(() => of(summary)),
    history: vi.fn(() => of([summary.current, summary.next].filter((m) => !!m))),
    cancel: vi.fn(() => of(membership({ status: 'CANCELLED' }))),
    cancelFreeze: vi.fn(() => of(membership())),
  };
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(dialogResult) })) };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } } },
      { provide: ClientsService, useValue: { get: vi.fn(() => of(CLIENT)) } },
      { provide: MembershipsService, useValue: api },
      { provide: MatDialog, useValue: dialog },
      { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(role) } },
    ],
  });
  const fixture = TestBed.createComponent(ClientDetail);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, dialog, el: fixture.nativeElement as HTMLElement };
}

describe('ClientDetail', () => {
  it('recepción puede vender y congelar, pero no anular ni migrar', () => {
    const { el } = setup('RECEPTIONIST', { current: membership() });
    expect(el.textContent).toContain('Ana Pérez');
    expect(el.textContent).toContain('Nueva membresía');
    expect(el.textContent).toContain('Congelar');
    expect(el.textContent).not.toContain('Anular');
    expect(el.textContent).not.toContain('Membresía migrada');
  });

  it('el administrador ve anular y, con una congelación en curso, ampliar y cancelar', () => {
    const { el } = setup('ADMIN', {
      current: membership({ status: 'FROZEN', canFreeze: false, freezes: [ACTIVE_FREEZE] }),
    });
    expect(el.textContent).toContain('Anular');
    expect(el.textContent).toContain('Ampliar congelación');
    expect(el.textContent).toContain('Cancelar congelación');
    expect(el.textContent).not.toContain('Membresía migrada'); // ya tiene una vigente
  });

  it('el administrador puede migrar si el cliente no tiene vigente ni siguiente', () => {
    const { el, component } = setup('ADMIN', {});
    expect(el.textContent).toContain('Membresía migrada');
    expect(component.canSell()).toBe(true);
  });

  it('con una siguiente registrada no deja vender otra', () => {
    const { component } = setup('ADMIN', {
      current: membership(),
      next: membership({ id: 11, status: 'PENDING', startDate: '2026-12-01' }),
    });
    expect(component.canSell()).toBe(false);
  });

  it('anular pide motivo y lo envía', () => {
    const m = membership();
    const { component, api } = setup('ADMIN', { current: m }, 'Cambio de plan');
    component.cancelMembership(m);
    expect(api.cancel).toHaveBeenCalledWith(10, 'Cambio de plan');
  });

  it('si se cierra el diálogo sin motivo no anula', () => {
    const m = membership();
    const { component, api } = setup('ADMIN', { current: m }, undefined);
    component.cancelMembership(m);
    expect(api.cancel).not.toHaveBeenCalled();
  });
});
