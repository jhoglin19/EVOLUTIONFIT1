import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/** Exige sesión iniciada; si no, manda al login recordando a dónde quería ir. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Para el login: si ya hay sesión, no tiene sentido mostrarlo. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};

/**
 * Exige uno de los roles indicados. Es solo comodidad de navegación:
 * la seguridad real está en el backend (@PreAuthorize), que valida cada petición.
 */
export const roleGuard =
  (...roles: string[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.hasRole(...roles) ? true : router.createUrlTree(['/dashboard']);
  };
