import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PlansService } from './plans.service';

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return { api: TestBed.inject(PlansService), http: TestBed.inject(HttpTestingController) };
}

describe('PlansService', () => {
  it('filtra por estado para el selector de planes activos', () => {
    const { api, http } = setup();
    api.list('', true, 0, 100).subscribe();
    const req = http.expectOne((r) => r.url === '/api/plans');
    expect(req.request.params.get('active')).toBe('true');
    expect(req.request.params.has('q')).toBe(false);
  });

  it('actualiza con PUT', () => {
    const { api, http } = setup();
    const body = { name: 'Mensual', description: null, durationDays: 30, price: 90000 };
    api.update(2, body).subscribe();
    const req = http.expectOne('/api/plans/2');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
  });
});
