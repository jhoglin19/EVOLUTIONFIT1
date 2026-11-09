import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { LoginResponse, User } from '../../shared/models/user.model';
import { API_URL } from '../config/api.config';

const TOKEN_KEY = 'evolutionfit.token';
const USER_KEY = 'evolutionfit.user';
const EXPIRES_KEY = 'evolutionfit.expiresAt';

/**
 * La sesión vive en sessionStorage: se borra al cerrar la pestaña, lo que reduce el riesgo en equipos
 * compartidos de recepción. Para que sobreviva al cierre del navegador, cambia a localStorage aquí.
 */
const storage = (): Storage | null => {
  try {
    return globalThis.sessionStorage ?? null;
  } catch {
    return null;
  }
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(this.read(TOKEN_KEY));
  private readonly userSignal = signal<User | null>(this.readUser());
  private readonly expiresAtSignal = signal<number>(Number(this.read(EXPIRES_KEY) ?? 0));

  readonly user = this.userSignal.asReadonly();
  readonly isAdmin = computed(() => this.userSignal()?.role?.toUpperCase() === 'ADMIN');

  /** True si hay token y aún no ha expirado. Se evalúa en cada navegación protegida. */
  isAuthenticated(): boolean {
    return !!this.tokenSignal() && this.expiresAtSignal() > Date.now();
  }

  token(): string | null {
    return this.isAuthenticated() ? this.tokenSignal() : null;
  }

  hasRole(...roles: string[]): boolean {
    const role = this.userSignal()?.role?.toUpperCase();
    return !!role && roles.some((r) => r.toUpperCase() === role);
  }

  login(identifier: string, password: string): Observable<User> {
    return this.http.post<LoginResponse>(`${API_URL}/auth/login`, { identifier, password }).pipe(
      tap((res) => this.setSession(res)),
      map((res) => res.user),
    );
  }

  /** Cierra la sesión local. El token es stateless: basta con descartarlo. */
  logout(expired = false): void {
    this.clearSession();
    void this.router.navigate(['/login'], expired ? { queryParams: { expired: 1 } } : {});
  }

  /**
   * Al abrir la app con una sesión guardada, valida el token contra el backend y refresca el perfil
   * (por si el usuario fue desactivado o cambió de rol). Nunca rechaza: si falla, simplemente no hay sesión.
   */
  restoreSession(): Observable<User | null> {
    if (!this.isAuthenticated()) {
      this.clearSession();
      return of(null);
    }
    return this.http.get<User>(`${API_URL}/auth/me`).pipe(
      tap((user) => this.persistUser(user)),
      catchError(() => of(null)),
    );
  }

  private setSession(res: LoginResponse): void {
    const expiresAt = Date.now() + res.expiresInSeconds * 1000;
    this.write(TOKEN_KEY, res.token);
    this.write(EXPIRES_KEY, String(expiresAt));
    this.tokenSignal.set(res.token);
    this.expiresAtSignal.set(expiresAt);
    this.persistUser(res.user);
  }

  private persistUser(user: User): void {
    this.write(USER_KEY, JSON.stringify(user));
    this.userSignal.set(user);
  }

  private clearSession(): void {
    [TOKEN_KEY, USER_KEY, EXPIRES_KEY].forEach((key) => storage()?.removeItem(key));
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    this.expiresAtSignal.set(0);
  }

  private read(key: string): string | null {
    return storage()?.getItem(key) ?? null;
  }

  private write(key: string, value: string): void {
    storage()?.setItem(key, value);
  }

  private readUser(): User | null {
    const raw = this.read(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }
}
