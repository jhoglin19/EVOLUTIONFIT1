import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { Plan } from '../../shared/models/plan.model';
import { Plans } from './plans';
import { PlansService } from './plans.service';

const PLAN: Plan = {
  id: 2,
  name: 'Mensual',
  description: null,
  durationDays: 30,
  price: 90000,
  active: true,
  createdAt: null,
};

function setup(role: string) {
  const api = {
    list: vi.fn(() => of({ content: [PLAN], page: 0, size: 10, totalElements: 1, totalPages: 1 })),
  };
  TestBed.configureTestingModule({
    providers: [
      { provide: PlansService, useValue: api },
      { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(role) } },
    ],
  });
  const fixture = TestBed.createComponent(Plans);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Plans', () => {
  it('el administrador ve las acciones de edición', () => {
    const el = setup('ADMIN');
    expect(el.textContent).toContain('Nuevo plan');
    expect(el.querySelector('mat-slide-toggle')).not.toBeNull();
    expect(el.querySelector('[aria-label="Editar plan"]')).not.toBeNull();
  });

  it('recepción solo consulta', () => {
    const el = setup('RECEPTIONIST');
    expect(el.textContent).toContain('Mensual');
    expect(el.textContent).not.toContain('Nuevo plan');
    expect(el.querySelector('mat-slide-toggle')).toBeNull();
    expect(el.querySelector('[aria-label="Editar plan"]')).toBeNull();
  });
});
