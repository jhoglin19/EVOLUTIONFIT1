import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LoginResponse, User } from '../../shared/models/user.model';
import { AuthService } from './auth.service';

const ADMIN: User = {
  id: 1,
  username: 'admin',
  email: null,
  firstName: 'Ana',
  lastName: 'Gómez',
  role: 'ADMIN',
  active: true,
  lastLoginAt: null,
  createdAt: null,
};

const loginResponse = (expiresInSeconds = 3600): LoginResponse => ({
  token: 'jwt-de-prueba',
  tokenType: 'Bearer',
  expiresInSeconds,
  user: ADMIN,
});

function setup(): { auth: AuthService; http: HttpTestingController } {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'login', children: [] }]),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });
  return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController) };
}

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());

  it('no hay sesión al iniciar sin datos guardados', () => {
    const { auth } = setup();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('el login guarda la sesión y habilita roles', () => {
    const { auth, http } = setup();

    auth.login('admin', 'Clave1234').subscribe();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ identifier: 'admin', password: 'Clave1234' });
    req.flush(loginResponse());

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.token()).toBe('jwt-de-prueba');
    expect(auth.isAdmin()).toBe(true);
    expect(auth.hasRole('ADMIN')).toBe(true);
    expect(auth.hasRole('RECEPTIONIST')).toBe(false);
  });

  it('un token vencido no cuenta como sesión', () => {
    const { auth, http } = setup();

    auth.login('admin', 'Clave1234').subscribe();
    http.expectOne('/api/auth/login').flush(loginResponse(-10));

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('logout borra la sesión', () => {
    const { auth, http } = setup();

    auth.login('admin', 'Clave1234').subscribe();
    http.expectOne('/api/auth/login').flush(loginResponse());
    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.user()).toBeNull();
    expect(sessionStorage.getItem('evolutionfit.token')).toBeNull();
  });

  it('restoreSession sin sesión no llama al backend', () => {
    const { auth, http } = setup();

    auth.restoreSession().subscribe((user) => expect(user).toBeNull());

    http.expectNone('/api/auth/me');
  });
});
