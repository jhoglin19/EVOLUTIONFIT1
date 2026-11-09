import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ClientsService } from './clients.service';

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return { api: TestBed.inject(ClientsService), http: TestBed.inject(HttpTestingController) };
}

describe('ClientsService', () => {
  it('envía búsqueda, estado y paginación', () => {
    const { api, http } = setup();
    api.list(' 123 ', false, 2, 10).subscribe();
    const req = http.expectOne((r) => r.url === '/api/clients');
    expect(req.request.params.get('q')).toBe('123');
    expect(req.request.params.get('active')).toBe('false');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
  });

  it('omite los filtros vacíos', () => {
    const { api, http } = setup();
    api.list('  ', null, 0, 10).subscribe();
    const req = http.expectOne((r) => r.url === '/api/clients');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('active')).toBe(false);
  });

  it('cambia el estado con PATCH', () => {
    const { api, http } = setup();
    api.setActive(5, true).subscribe();
    const req = http.expectOne('/api/clients/5/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ active: true });
  });
});
