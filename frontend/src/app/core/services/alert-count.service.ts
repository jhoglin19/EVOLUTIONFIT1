import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

import { AlertCounts } from '../../shared/models/dashboard.model';
import { API_URL } from '../config/api.config';

/**
 * Número de alertas de vencimiento del menú (por vencer + vencidas recientes). Es solo un aviso: los
 * listados vienen siempre del backend. Se actualiza al cambiar de pantalla, como máximo cada minuto.
 */
@Injectable({ providedIn: 'root' })
export class AlertCountService {
  private readonly http = inject(HttpClient);
  private readonly counts = signal<AlertCounts | null>(null);
  private lastLoad = 0;

  static readonly MIN_INTERVAL_MS = 60_000;

  readonly value = this.counts.asReadonly();
  readonly total = computed(() => {
    const c = this.counts();
    return c ? c.expiring + c.recentlyExpired : 0;
  });

  /** force: después de cobrar una renovación, o al abrir Vencimientos. */
  refresh(force = false, now = Date.now()): void {
    if (!force && now - this.lastLoad < AlertCountService.MIN_INTERVAL_MS) {
      return;
    }
    this.lastLoad = now;
    this.http.get<AlertCounts>(`${API_URL}/alerts/count`).subscribe({
      next: (c) => this.counts.set(c),
      error: () => (this.lastLoad = 0), // se reintenta en la próxima navegación
    });
  }

  set(counts: AlertCounts): void {
    this.counts.set(counts);
    this.lastLoad = Date.now();
  }
}
