import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatPaginatorIntl, MatPaginatorModule } from '@angular/material/paginator';
import { MatStepperIntl } from '@angular/material/stepper';

import { EsPaginatorIntl, provideSpanishMaterialIntl } from './material-intl';

@Component({
  imports: [MatPaginatorModule],
  template: `<mat-paginator [length]="1" [pageSize]="20" [pageSizeOptions]="[20, 50]" />`,
})
class OnePage {}

describe('Textos de Angular Material en español', () => {
  it('el rango del paginador usa "de" y separador de miles de Colombia', () => {
    const intl = new EsPaginatorIntl();
    expect(intl.getRangeLabel(0, 20, 1)).toBe('1 – 1 de 1');
    expect(intl.getRangeLabel(1, 20, 45)).toBe('21 – 40 de 45');
    expect(intl.getRangeLabel(62, 20, 1250)).toBe('1.241 – 1.250 de 1.250');
    expect(intl.getRangeLabel(0, 20, 0)).toBe('0 de 0');
    expect(intl.itemsPerPageLabel).toBe('Elementos por página');
    expect(intl.nextPageLabel).toBe('Página siguiente');
    expect(intl.previousPageLabel).toBe('Página anterior');
  });

  it('el paginador del sistema se muestra en español', () => {
    TestBed.configureTestingModule({ providers: provideSpanishMaterialIntl() });
    const fixture = TestBed.createComponent(OnePage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Elementos por página');
    expect(el.textContent).toContain('1 – 1 de 1');
    expect(el.textContent).not.toMatch(/Items per page|\bof\b/);
    expect(el.querySelector('[aria-label="Página siguiente"]')).not.toBeNull();
    expect(el.querySelector('[aria-label="Página anterior"]')).not.toBeNull();
    expect(TestBed.inject(MatPaginatorIntl)).toBeInstanceOf(EsPaginatorIntl);
    expect(TestBed.inject(MatStepperIntl).optionalLabel).toBe('Opcional');
  });
});
