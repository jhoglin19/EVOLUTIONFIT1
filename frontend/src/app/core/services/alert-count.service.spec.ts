import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AlertCountService } from './alert-count.service';

describe('AlertCountService', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    return { service: TestBed.inject(AlertCountService), http: TestBed.inject(HttpTestingController) };
  }

  it('suma por vencer y vencidas recientes, y consulta como máximo una vez por minuto', () => {
    const { service, http } = setup();
    service.refresh(false, 1_000_000);
    http.expectOne('/api/alerts/count').flush({ expiring: 2, recentlyExpired: 3, warningDays: 5, recentlyExpiredDays: 15 });
    expect(service.total()).toBe(5);

    service.refresh(false, 1_030_000);
    http.expectNone('/api/alerts/count');
    service.refresh(true, 1_030_000);
    http.expectOne('/api/alerts/count').flush({ expiring: 1, recentlyExpired: 0, warningDays: 5, recentlyExpiredDays: 15 });
    expect(service.total()).toBe(1);
    http.verify();
  });
});
