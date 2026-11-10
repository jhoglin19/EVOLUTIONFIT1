import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { membership } from './membership.fixtures';
import { Memberships } from './memberships';
import { MembershipsService } from './memberships.service';

function setup() {
  const api = {
    list: vi.fn(() =>
      of({
        content: [membership({ migrated: true, status: 'FROZEN' })],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
  };
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: MembershipsService, useValue: api }] });
  const fixture = TestBed.createComponent(Memberships);
  fixture.detectChanges();
  return { fixture, api, el: fixture.nativeElement as HTMLElement, component: fixture.componentInstance };
}

describe('Memberships', () => {
  it('lista con el estado calculado y la marca de migrada', () => {
    const { el } = setup();
    expect(el.textContent).toContain('Ana Pérez');
    expect(el.textContent).toContain('Congelada');
    expect(el.textContent).toContain('Migrada del sistema anterior');
  });

  it('el filtro "por vencer" vuelve a la primera página y consulta al backend', () => {
    const { component, api } = setup();
    component.pageIndex.set(3);
    component.expiringSoon.setValue(true);
    expect(component.pageIndex()).toBe(0);
    expect(api.list).toHaveBeenLastCalledWith({ state: null, expiringSoon: true, query: '' }, 0, 10);
  });
});
