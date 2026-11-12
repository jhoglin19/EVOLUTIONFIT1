import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { API_URL } from '../config/api.config';
import { AuthService } from '../services/auth.service';

const LOGIN_URL = `${API_URL}/auth/login`;

/**
 * Agrega el token JWT a las peticiones a la API y cierra la sesión si el backend responde 401
 * (token vencido o usuario desactivado). El login queda excluido: allí un 401 solo significa
 * "credenciales incorrectas" y debe mostrarse en el formulario.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isApi = req.url.startsWith(API_URL);
  const isLogin = req.url === LOGIN_URL;
  const token = auth.token();

  const request =
    isApi && !isLogin && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && isApi && !isLogin) {
        auth.logout('expired');
      }
      return throwError(() => error);
    }),
  );
};
