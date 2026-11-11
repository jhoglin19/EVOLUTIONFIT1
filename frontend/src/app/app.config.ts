import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsCo from '@angular/common/locales/es-CO';
import {
  ApplicationConfig,
  EnvironmentProviders,
  ErrorHandler,
  LOCALE_ID,
  Provider,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { UserErrorHandler } from './core/errors/user-error-handler';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';

registerLocaleData(localeEsCo);

/**
 * Proveedores de la aplicación. Las pruebas de integración (src/app/testing/app-harness.ts) montan esta
 * misma lista con las rutas reales; solo cambian el backend HTTP por uno simulado.
 */
export const appProviders: (Provider | EnvironmentProviders)[] = [
  { provide: LOCALE_ID, useValue: 'es-CO' },
  provideBrowserGlobalErrorListeners(),
  // Ningún error en silencio: lo que no maneje una pantalla se muestra en un aviso.
  { provide: ErrorHandler, useClass: UserErrorHandler },
  provideRouter(routes, withComponentInputBinding()),
  provideHttpClient(withInterceptors([authInterceptor])),
];

export const appConfig: ApplicationConfig = {
  providers: [
    ...appProviders,
    // Antes de mostrar nada, valida la sesión guardada (si existe) contra el backend.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restoreSession())),
  ],
};
