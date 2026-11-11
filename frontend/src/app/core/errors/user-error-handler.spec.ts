import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { UserErrorHandler } from './user-error-handler';

describe('UserErrorHandler', () => {
  function setup() {
    const open = vi.fn();
    TestBed.configureTestingModule({
      providers: [{ provide: ErrorHandler, useClass: UserErrorHandler }, { provide: MatSnackBar, useValue: { open } }],
    });
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    return { handler: TestBed.inject(ErrorHandler), open };
  }

  it('muestra el mensaje del backend de un error HTTP que nadie manejó', () => {
    const { handler, open } = setup();
    handler.handleError(new HttpErrorResponse({ status: 409, error: { message: 'El arqueo ya fue registrado' } }));
    expect(open).toHaveBeenCalledWith('El arqueo ya fue registrado', 'Cerrar', { duration: 8000 });
  });

  it('un error de programación muestra un aviso genérico, una sola vez si se repite', () => {
    const { handler, open } = setup();
    handler.handleError(new TypeError('x is undefined'));
    handler.handleError(new TypeError('x is undefined'));
    expect(open).toHaveBeenCalledTimes(1);
    expect(open.mock.calls[0][0]).toBe('Ocurrió un error inesperado. Intenta nuevamente.');
  });

  it('desenvuelve los rechazos de promesas', () => {
    const { handler, open } = setup();
    handler.handleError({ rejection: new HttpErrorResponse({ status: 0 }) });
    expect(open.mock.calls[0][0]).toContain('No se pudo conectar con el servidor');
  });
});
