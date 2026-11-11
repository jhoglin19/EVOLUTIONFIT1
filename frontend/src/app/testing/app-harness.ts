import { provideHttpClientTesting, HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';

import { appProviders } from '../app.config';
import { User } from '../shared/models/user.model';

/**
 * Monta la aplicación real para pruebas de integración: los mismos proveedores de app.config, las rutas
 * reales (con el bloque perezoso del shell y sus proveedores), MatDialog, formularios y el interceptor.
 * Solo el backend HTTP es simulado. Las pruebas hacen clic y escriben en el DOM, como un usuario.
 */
export interface AppHarness {
  harness: RouterTestingHarness;
  http: HttpTestingController;
  /** Raíz del documento: los diálogos y snackbars se pintan fuera del componente de la ruta. */
  page: () => HTMLElement;
  /**
   * Espera (hasta 2 s) a que estén pendientes todas las peticiones indicadas ("MÉTODO /ruta", sin query) y
   * las responde; las demás pendientes reciben una respuesta vacía. Los diálogos avisan su cierre al terminar
   * la animación, por eso hay que esperar en vez de responder de inmediato.
   */
  respond: (routes: Record<string, unknown>) => Promise<TestRequest[]>;
  /** Espera (hasta 2 s) una petición que cumpla la condición. */
  request: (match: (req: TestRequest['request']) => boolean) => Promise<TestRequest>;
  settle: () => Promise<void>;
}

const TIMEOUT_MS = 2000;

/** Respuestas por defecto (como las daría el backend real sin datos) para lo que una prueba no define. */
const DEFAULTS: Record<string, unknown> = {
  'GET /api/business-info': { tradeName: 'Gimnasio Ficticio' },
  'GET /api/alerts/count': { expiring: 0, recentlyExpired: 0, warningDays: 5, recentlyExpiredDays: 15 },
  'GET /api/plans': { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
};
const pause = () => new Promise((resolve) => setTimeout(resolve, 20));

export async function startApp(role: 'ADMIN' | 'RECEPTIONIST', url: string): Promise<AppHarness> {
  const user = { id: 1, username: 'usuario_prueba', firstName: 'Sara', lastName: 'Prueba', role } as unknown as User;
  sessionStorage.setItem('evolutionfit.token', 'token-ficticio');
  sessionStorage.setItem('evolutionfit.expiresAt', String(Date.now() + 3_600_000));
  sessionStorage.setItem('evolutionfit.user', JSON.stringify(user));

  TestBed.configureTestingModule({ providers: [...appProviders, provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);

  const settle = async () => {
    for (let i = 0; i < 3; i++) {
      harness.fixture.detectChanges();
      await harness.fixture.whenStable();
    }
  };

  const key = (r: TestRequest['request']) => `${r.method} ${r.url}`;

  const request = async (match: (req: TestRequest['request']) => boolean) => {
    const until = Date.now() + TIMEOUT_MS;
    while (Date.now() < until) {
      await settle();
      const found = http.match(match);
      if (found.length) {
        return found[0];
      }
      await pause();
    }
    throw new Error('No llegó la petición esperada');
  };

  const respond = async (routes: Record<string, unknown>) => {
    const until = Date.now() + TIMEOUT_MS;
    const wanted = Object.keys(routes);
    const done: TestRequest[] = [];
    const pending: TestRequest[] = [];
    while (true) {
      await settle();
      pending.push(...http.match(() => true));
      const seen = new Set(pending.map((r) => key(r.request)));
      if (wanted.every((k) => seen.has(k)) || Date.now() > until) {
        const missing = wanted.filter((k) => !seen.has(k));
        if (missing.length) {
          throw new Error('No llegaron las peticiones: ' + missing.join(', '));
        }
        break;
      }
      await pause();
    }
    for (const req of pending) {
      const k = key(req.request);
      if (k in routes) {
        const body = routes[k];
        if (body instanceof Error) {
          req.flush({ message: body.message }, { status: 500, statusText: 'Error' });
        } else {
          req.flush(body as never);
        }
        done.push(req);
      } else if (k in DEFAULTS) {
        req.flush(DEFAULTS[k] as never);
      } else {
        throw new Error('Petición sin respuesta definida en la prueba: ' + k);
      }
    }
    await settle();
    return done;
  };

  await settle();
  return { harness, http, page: () => document.body, respond, request, settle };
}

/** Escribe en un input como un usuario. */
export function type(input: HTMLInputElement | HTMLTextAreaElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

/** Botón por su texto visible. */
export function button(root: HTMLElement, text: string): HTMLButtonElement {
  const found = Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find(
    (b) => b.textContent?.trim().includes(text),
  );
  if (!found) {
    throw new Error(`No hay un botón "${text}"`);
  }
  return found;
}

/**
 * Registra si algún envío de formulario quedó sin manejar por Angular: en un navegador, ese envío recarga la
 * página y se pierde lo que el usuario hizo (es lo que pasaba con los formularios sin [formGroup]).
 */
export function watchNativeSubmits(): { unhandled: () => number; stop: () => void } {
  let unhandled = 0;
  const listener = (e: Event) => {
    // Se evalúa después de los manejadores de Angular (este se registra en la fase de burbuja del documento).
    if (!e.defaultPrevented) {
      unhandled++;
      e.preventDefault(); // jsdom no navega, pero así no imprime "Not implemented: form submit".
    }
  };
  document.addEventListener('submit', listener);
  return { unhandled: () => unhandled, stop: () => document.removeEventListener('submit', listener) };
}
