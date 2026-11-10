import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ReasonDialog } from './reason-dialog';

function setup() {
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: { title: 'Anular', message: 'x', confirmLabel: 'Anular' } },
    ],
  });
  return { component: TestBed.createComponent(ReasonDialog).componentInstance, close };
}

describe('ReasonDialog', () => {
  it('no cierra sin motivo', () => {
    const { component, close } = setup();
    component.reason.setValue('   ');
    component.confirm();
    expect(close).not.toHaveBeenCalled();
  });

  it('devuelve el motivo sin espacios sobrantes', () => {
    const { component, close } = setup();
    component.reason.setValue('  Cambio de plan ');
    component.confirm();
    expect(close).toHaveBeenCalledWith('Cambio de plan');
  });
});
