import { ErrorHandler, Injectable, Injector, NgZone, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

import { errorMessage } from '../utils/error-message';

/**
 * Red de seguridad: ningún error queda en silencio. Cada acción maneja sus errores y los muestra en su
 * pantalla; si alguno se escapa (una suscripción sin manejo de error, una excepción en un componente), el
 * usuario ve un aviso en vez de una pantalla que "no hace nada". El error también queda en la consola.
 */
@Injectable()
export class UserErrorHandler extends ErrorHandler {
  private readonly injector = inject(Injector);
  private readonly zone = inject(NgZone, { optional: true });
  private lastMessage = '';
  private lastAt = 0;

  override handleError(error: unknown): void {
    super.handleError(error); // consola, como siempre
    const message = errorMessage(unwrap(error));
    const now = Date.now();
    if (message === this.lastMessage && now - this.lastAt < 3000) {
      return; // el mismo error repetido en cadena: un solo aviso
    }
    this.lastMessage = message;
    this.lastAt = now;
    const show = () => this.injector.get(MatSnackBar).open(message, 'Cerrar', { duration: 8000 });
    try {
      if (this.zone) {
        this.zone.run(show);
      } else {
        show();
      }
    } catch {
      // Si ni siquiera se puede mostrar el aviso, el error ya quedó en la consola.
    }
  }
}

/** Los errores de las promesas llegan envueltos ({ rejection }). */
function unwrap(error: unknown): unknown {
  return error && typeof error === 'object' && 'rejection' in error ? (error as { rejection: unknown }).rejection : error;
}
