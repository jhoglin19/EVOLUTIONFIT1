import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { BusinessInfoService } from '../../core/services/business-info.service';
import { ExpirationAlert, renewLink } from '../../shared/models/dashboard.model';
import { expirationWhatsappUrl } from '../../shared/utils/whatsapp';

/**
 * Acciones de una alerta de vencimiento: renovar (o cobrar la renovación pendiente), recordar por WhatsApp
 * (enlace wa.me, sin envío automático; no aparece si el cliente no tiene celular) y ver la ficha.
 */
@Component({
  selector: 'app-alert-actions',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, RouterLink],
  template: `
    <div class="actions">
      <a mat-flat-button [routerLink]="link().path" [queryParams]="link().queryParams">{{ link().label }}</a>
      @if (whatsapp(); as url) {
        <a mat-stroked-button [href]="url" target="_blank" rel="noopener" data-whatsapp>
          <mat-icon>chat</mat-icon>
          Recordar por WhatsApp
        </a>
      }
      <a mat-icon-button [routerLink]="['/clients', alert().clientId]" matTooltip="Ver ficha" aria-label="Ver ficha">
        <mat-icon>person</mat-icon>
      </a>
    </div>
  `,
  styles: `
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
  `,
})
export class AlertActions {
  private readonly business = inject(BusinessInfoService);

  readonly alert = input.required<ExpirationAlert>();

  readonly link = computed(() => renewLink(this.alert()));
  readonly whatsapp = computed(() => expirationWhatsappUrl(this.alert(), this.business.tradeName()));

  constructor() {
    this.business.load();
  }
}
