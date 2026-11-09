import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { AuthService } from '../services/auth.service';
import { authGuard, guestGuard, roleGuard } from './auth.guards';

function setup() {
  sessionStorage.clear();
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController) };
}

function run(guard: CanActivateFn, url = '/users') {
  return TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
  );
}

function logInAs(ctx: ReturnType<typeof setup>, role: string): void {
  ctx.auth.login('u', 'x').subscribe();
  ctx.http.expectOne('/api/auth/login').flush({
    token: 't',
    tokenType: 'Bearer',
    expiresInSeconds: 3600,
    user: {
      id: 1,
      username: 'u',
      email: null,
      firstName: 'A',
      lastName: 'B',
      role,
      active: true,
      lastLoginAt: null,
      createdAt: null,
    },
  });
}

describe('guards', () => {
  it('authGuard redirige al login recordando la URL si no hay sesión', () => {
    setup();
    const result = run(authGuard, '/users') as UrlTree;
    expect(result.toString()).toBe('/login?returnUrl=%2Fusers');
  });

  it('authGuard deja pasar con sesión', () => {
    const ctx = setup();
    logInAs(ctx, 'ADMIN');
    expect(run(authGuard)).toBe(true);
  });

  it('guestGuard manda al dashboard si ya hay sesión', () => {
    const ctx = setup();
    logInAs(ctx, 'ADMIN');
    expect((run(guestGuard) as UrlTree).toString()).toBe('/dashboard');
  });

  it('guestGuard deja ver el login sin sesión', () => {
    setup();
    expect(run(guestGuard)).toBe(true);
  });

  it('roleGuard permite al rol indicado', () => {
    const ctx = setup();
    logInAs(ctx, 'ADMIN');
    expect(run(roleGuard('ADMIN'))).toBe(true);
  });

  it('roleGuard bloquea a otro rol y lo manda al dashboard', () => {
    const ctx = setup();
    logInAs(ctx, 'RECEPTIONIST');
    expect((run(roleGuard('ADMIN')) as UrlTree).toString()).toBe('/dashboard');
  });
});
