import { CurrencyPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

import { MethodTotal, summarizeMethods } from '../../../shared/models/cash.model';
import { methodLabel } from '../../../shared/models/payment.model';

/**
 * Informe por método de pago con sus totales: efectivo, otros métodos y total general recaudado.
 * Lo usan la caja del día, el detalle de un cierre y el reporte mensual.
 */
@Component({
  selector: 'app-method-totals',
  imports: [CurrencyPipe],
  template: `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Método</th>
            <th>Pagos</th>
            <th>Recibido</th>
            <th>Anulado</th>
            <th>Neto</th>
          </tr>
        </thead>
        <tbody>
          @for (m of methods(); track m.method) {
            <tr>
              <td>{{ methodName(m.method) }}</td>
              <td>{{ m.receivedCount }}</td>
              <td>{{ m.receivedAmount | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
              <td>{{ m.voidedAmount | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
              <td>{{ m.receivedAmount - m.voidedAmount | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="5" class="empty">Sin pagos en {{ periodLabel() }}.</td>
            </tr>
          }
        </tbody>
        <tfoot>
          @for (row of rows(); track row.key) {
            <tr [class]="row.key" [attr.data-total]="row.key">
              <th scope="row">{{ row.label }}</th>
              <td>{{ row.totals.count }}</td>
              <td>{{ row.totals.received | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
              <td>{{ row.totals.voided | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
              <td>{{ row.totals.net | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
            </tr>
          }
        </tfoot>
      </table>
    </div>
    <p class="hint">
      <b>Total general recaudado</b>: lo cobrado en {{ periodLabel() }} con todos los métodos, neto de anulaciones.
      No descuenta los retiros ni incluye la base.
      @if (cashHint()) {
        <b>Efectivo esperado</b>: lo que debe haber en el cajón (base + efectivo − anulaciones en efectivo − retiros).
        Solo se cuadra el efectivo; los demás métodos son informativos.
      }
    </p>
  `,
  styles: `
    :host {
      display: block;
      max-width: 100%;
      min-width: 0;
    }
    .table-wrap {
      max-width: 100%;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 12px;
      background: var(--ef-surface);
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      padding: 10px 16px;
      text-align: left;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      white-space: nowrap;
    }
    thead th {
      font-weight: 500;
      color: var(--mat-sys-on-surface-variant);
    }
    tfoot th,
    tfoot td {
      font-weight: 600;
      background: var(--mat-sys-surface-container);
    }
    tfoot tr.all th,
    tfoot tr.all td {
      border-bottom: 0;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .empty {
      color: var(--mat-sys-on-surface-variant);
    }
    .hint {
      overflow-wrap: anywhere;
      margin: 8px 0 16px;
      font-size: 0.8rem;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class MethodTotals {
  readonly methods = input.required<MethodTotal[]>();
  /** Texto del periodo en las explicaciones: "el día", "el mes"… */
  readonly periodLabel = input('el día');
  /** Explicación del efectivo esperado de la caja. Los reportes y el panel no la muestran. */
  readonly cashHint = input(true);

  readonly methodName = methodLabel;

  readonly rows = computed(() => {
    const s = summarizeMethods(this.methods());
    return [
      { key: 'cash', label: 'Total efectivo', totals: s.cash },
      { key: 'other', label: 'Total otros métodos', totals: s.other },
      { key: 'all', label: 'Total general recaudado', totals: s.all },
    ];
  });
}
