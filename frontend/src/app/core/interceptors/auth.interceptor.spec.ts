import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LoginResponse } from '../../shared/models/user.model';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

function setup() {
  sessionStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'login', children: [] }]),
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  return {
    auth: TestBed.inject(AuthService),
    client: TestBed.inject(HttpClient),
    http: TestBed.inject(HttpTestingController),
  };
}

const session: LoginResponse = {
  token: 'jwt-de-prueba',
  tokenType: 'Bearer',
  expiresInSeconds: 3600,
  user: {
    id: 1,
    username: 'admin',
    email: null,
    firstName: 'Ana',
    lastName: 'Gómez',
    role: 'ADMIN',
    active: true,
    lastLoginAt: null,
    createdAt: null,
  },
};

function logIn(ctx: ReturnType<typeof setup>): void {
  ctx.auth.login('admin', 'x').subscribe();
  ctx.http.expectOne('/api/auth/login').flush(session);
}

describe('authInterceptor', () => {
  it('agrega el token a las peticiones de la API', () => {
    const ctx = setup();
    logIn(ctx);

    ctx.client.get('/api/users').subscribe();

    const req = ctx.http.expectOne('/api/users');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-de-prueba');
    req.flush({});
  });

  it('no manda el token al endpoint de login', () => {
    const ctx = setup();
    logIn(ctx);

    ctx.client.post('/api/auth/login', {}).subscribe({ error: () => undefined });

    const req = ctx.http.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(ctx.auth.isAuthenticated()).toBe(true);
  });

  it('no manda el token a URLs que no son de la API', () => {
    const ctx = setup();
    logIn(ctx);

    ctx.client.get('https://otro-sitio.com/datos').subscribe();

    const req = ctx.http.expectOne('https://otro-sitio.com/datos');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('cierra la sesión cuando la API responde 401', () => {
    const ctx = setup();
    logIn(ctx);

    ctx.client.get('/api/users').subscribe({ error: () => undefined });
    ctx.http.expectOne('/api/users').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(ctx.auth.isAuthenticated()).toBe(false);
  });

  it('un 403 no cierra la sesión', () => {
    const ctx = setup();
    logIn(ctx);

    ctx.client.get('/api/users').subscribe({ error: () => undefined });
    ctx.http.expectOne('/api/users').flush({}, { status: 403, statusText: 'Forbidden' });

    expect(ctx.auth.isAuthenticated()).toBe(true);
  });
});
