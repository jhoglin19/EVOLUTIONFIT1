import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MembershipsService } from './memberships.service';

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return { api: TestBed.inject(MembershipsService), http: TestBed.inject(HttpTestingController) };
}

describe('MembershipsService', () => {
  it('envía los filtros de estado y "por vencer" solo si están activos', () => {
    const { api, http } = setup();
    api.list({ state: 'FROZEN', expiringSoon: true, query: ' ana ' }, 1, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/memberships');
    expect(req.request.params.get('state')).toBe('FROZEN');
    expect(req.request.params.get('expiringSoon')).toBe('true');
    expect(req.request.params.get('q')).toBe('ana');
    expect(req.request.params.get('page')).toBe('1');

    api.list({ state: null, expiringSoon: false, query: '' }, 0, 10).subscribe();
    const plain = http.expectOne((r) => r.url === '/api/memberships');
    expect(plain.request.params.keys()).toEqual(['page', 'size']);
  });

  it('usa las rutas de cliente y de congelación', () => {
    const { api, http } = setup();
    api.history(3).subscribe();
    http.expectOne('/api/clients/3/memberships');
    api.current(3).subscribe();
    http.expectOne('/api/clients/3/memberships/current');

    api.extendFreeze(5, { totalDays: 12, reason: 'Incapacidad' }).subscribe();
    const extend = http.expectOne('/api/freezes/5/extend');
    expect(extend.request.method).toBe('PATCH');

    api.cancelFreeze(5, 'Volvió').subscribe();
    const cancel = http.expectOne('/api/freezes/5/cancel');
    expect(cancel.request.method).toBe('POST');
    expect(cancel.request.body).toEqual({ reason: 'Volvió' });
  });

  it('la vista previa solo manda la fecha si se eligió', () => {
    const { api, http } = setup();
    api.preview(1, 2, null).subscribe();
    const req = http.expectOne((r) => r.url === '/api/memberships/preview');
    expect(req.request.params.has('startDate')).toBe(false);
    expect(req.request.params.get('planId')).toBe('2');
  });
});
