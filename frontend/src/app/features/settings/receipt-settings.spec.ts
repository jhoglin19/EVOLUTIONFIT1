import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ReceiptSettings } from '../../shared/models/receipt-settings.model';
import { ReceiptSettingsPage } from './receipt-settings';
import { ReceiptSettingsService } from './receipt-settings.service';

const EMPTY: ReceiptSettings = {
  receiptPrefix: 'EF-',
  nextNumber: 1,
  nextReceiptNumber: 'EF-000001',
  initialNumberLocked: false,
  hasLogo: false,
  logoSizeBytes: 0,
  maxLogoSizeBytes: 512000,
};

function setup(settings: ReceiptSettings = EMPTY) {
  const api = {
    get: vi.fn(() => of(settings)),
    update: vi.fn(() => of(settings)),
    uploadLogo: vi.fn(() => of(settings)),
    logo: vi.fn(),
    preview: vi.fn(),
  };
  TestBed.configureTestingModule({ providers: [{ provide: ReceiptSettingsService, useValue: api }] });
  const fixture = TestBed.createComponent(ReceiptSettingsPage);
  fixture.detectChanges();
  return { c: fixture.componentInstance, api, el: fixture.nativeElement as HTMLElement };
}

function fileEvent(size: number, type: string): Event {
  const file = new File([new Uint8Array(size)], 'logo', { type });
  return { target: { files: [file], value: 'x' } } as unknown as Event;
}

describe('ReceiptSettingsPage', () => {
  it('sin datos avisa y muestra el próximo número', () => {
    const { el } = setup();
    expect(el.textContent).toContain('Aún no hay datos del gimnasio');
    expect(el.textContent).toContain('EF-000001');
  });

  it('rechaza un logo de más de 500 KB con el tamaño máximo en el mensaje', () => {
    const { c, api } = setup();
    c.onLogoSelected(fileEvent(500 * 1024 + 1, 'image/png'));
    expect(c.logoError()).toContain('tamaño máximo permitido de 500 KB');
    expect(api.uploadLogo).not.toHaveBeenCalled();

    c.onLogoSelected(fileEvent(1000, 'image/gif'));
    expect(c.logoError()).toContain('PNG o JPG');

    c.onLogoSelected(fileEvent(1000, 'image/png'));
    expect(api.uploadLogo).toHaveBeenCalledOnce();
  });

  it('bloquea el número inicial después del primer comprobante y no lo envía', () => {
    const { c, api } = setup({ ...EMPTY, initialNumberLocked: true, nextNumber: 8, nextReceiptNumber: 'EF-000008' });
    expect(c.form.controls.initialNumber.disabled).toBe(true);
    c.save();
    expect(api.update).toHaveBeenCalledWith(expect.objectContaining({ initialNumber: null, receiptPrefix: 'EF-' }));
  });

  it('guarda los datos del encabezado limpios', () => {
    const { c, api } = setup();
    c.form.patchValue({ tradeName: ' Gimnasio Ficticio ', taxId: '000.000.000-0', initialNumber: 100 });
    c.save();
    expect(api.update).toHaveBeenCalledWith(
      expect.objectContaining({ tradeName: 'Gimnasio Ficticio', taxId: '000.000.000-0', legalName: null, initialNumber: 100 }),
    );
  });
});
