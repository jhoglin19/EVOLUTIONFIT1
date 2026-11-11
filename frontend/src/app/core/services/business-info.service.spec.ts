import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BusinessInfoService } from './business-info.service';

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return {
    service: TestBed.inject(BusinessInfoService),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('BusinessInfoService', () => {
  it('carga el nombre comercial una sola vez', () => {
    const { service, http } = setup();
    service.load();
    service.load();
    http.expectOne('/api/business-info').flush({ tradeName: ' Gimnasio Ficticio ' });
    expect(service.tradeName()).toBe('Gimnasio Ficticio');
    http.verify();
  });

  it('sin nombre configurado queda en null', () => {
    const { service, http } = setup();
    service.load();
    http.expectOne('/api/business-info').flush({});
    expect(service.tradeName()).toBeNull();
  });

  it('la configuración lo actualiza al guardar', () => {
    const { service, http } = setup();
    service.set('Otro Nombre Ficticio');
    service.load();
    http.expectNone('/api/business-info');
    expect(service.tradeName()).toBe('Otro Nombre Ficticio');
  });
});
