import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { API_URL } from '../config/api.config';

interface BusinessInfo {
  tradeName?: string | null;
}

/**
 * Nombre comercial del gimnasio para el mensaje de WhatsApp. Lo pueden leer recepción y ADMIN
 * (el resto de la configuración es solo de ADMIN). Se carga una vez por sesión.
 */
@Injectable({ providedIn: 'root' })
export class BusinessInfoService {
  private readonly http = inject(HttpClient);
  private readonly name = signal<string | null>(null);
  private requested = false;

  readonly tradeName = this.name.asReadonly();

  load(): void {
    if (this.requested) {
      return;
    }
    this.requested = true;
    this.http.get<BusinessInfo>(`${API_URL}/business-info`).subscribe({
      next: (info) => this.name.set(info.tradeName?.trim() || null),
      error: () => (this.requested = false), // se reintenta en la próxima pantalla
    });
  }

  /** La pantalla de configuración lo actualiza al guardar, sin volver a consultar. */
  set(tradeName: string | null | undefined): void {
    this.requested = true;
    this.name.set(tradeName?.trim() || null);
  }
}
