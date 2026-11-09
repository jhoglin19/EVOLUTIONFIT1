import { HttpErrorResponse } from '@angular/common/http';

import { ApiError } from '../../shared/models/page.model';

/** Convierte cualquier error HTTP en un texto legible para el usuario. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se pudo conectar con el servidor. Verifica tu conexión.';
    }
    const body = error.error as Partial<ApiError> | null;
    if (body?.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0];
      if (first) {
        return first;
      }
    }
    if (body?.message) {
      return body.message;
    }
  }
  return 'Ocurrió un error inesperado. Intenta nuevamente.';
}
