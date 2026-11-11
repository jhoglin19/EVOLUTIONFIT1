import { Injectable, Provider } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatStepperIntl } from '@angular/material/stepper';

const numberFormat = new Intl.NumberFormat('es-CO');

/** Textos del paginador de Angular Material en español de Colombia ("1 – 20 de 1.250"). */
@Injectable()
export class EsPaginatorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Elementos por página';
  override nextPageLabel = 'Página siguiente';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primera página';
  override lastPageLabel = 'Última página';

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return `0 de ${numberFormat.format(length)}`;
    }
    const start = page * pageSize;
    const end = start < length ? Math.min(start + pageSize, length) : start + pageSize;
    return `${numberFormat.format(start + 1)} – ${numberFormat.format(end)} de ${numberFormat.format(length)}`;
  };
}

/** Textos del asistente por pasos (los leen los lectores de pantalla). */
@Injectable()
export class EsStepperIntl extends MatStepperIntl {
  override optionalLabel = 'Opcional';
  override completedLabel = 'Completado';
  override editableLabel = 'Editable';
}

/** Textos de Angular Material en español para todo el sistema. */
export function provideSpanishMaterialIntl(): Provider[] {
  return [
    { provide: MatPaginatorIntl, useClass: EsPaginatorIntl },
    { provide: MatStepperIntl, useClass: EsStepperIntl },
  ];
}
