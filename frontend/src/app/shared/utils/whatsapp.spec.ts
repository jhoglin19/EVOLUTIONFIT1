import { ExpirationAlert } from '../models/dashboard.model';
import { Payment } from '../models/payment.model';
import {
  expirationMessage,
  expirationWhatsappUrl,
  receiptMessage,
  receiptWhatsappUrl,
  whatsappNumber,
} from './whatsapp';

const PAYMENT = {
  id: 7,
  clientName: 'Ana Pérez',
  clientPhone: '300 123 4567',
  planName: 'Mensual',
  amount: 90000,
  membershipStartDate: '2026-11-10',
  membershipEndDate: '2026-12-09',
  receiptNumber: 'EF-000001',
} as Payment;

describe('whatsapp', () => {
  it('agrega +57 si el número no trae indicativo', () => {
    expect(whatsappNumber('300 123 4567')).toBe('573001234567');
    expect(whatsappNumber('(300) 123-4567')).toBe('573001234567');
    expect(whatsappNumber('601 234 5678')).toBe('576012345678');
  });

  it('respeta el indicativo cuando ya viene', () => {
    expect(whatsappNumber('+57 300 123 4567')).toBe('573001234567');
    expect(whatsappNumber('573001234567')).toBe('573001234567');
    expect(whatsappNumber('+1 305 555 0100')).toBe('13055550100');
    expect(whatsappNumber('00 34 612 345 678')).toBe('34612345678');
  });

  it('sin teléfono usable no hay número', () => {
    expect(whatsappNumber(null)).toBeNull();
    expect(whatsappNumber('')).toBeNull();
    expect(whatsappNumber('  ')).toBeNull();
    expect(whatsappNumber('123')).toBeNull();
  });

  it('arma el enlace wa.me con el mensaje precargado', () => {
    const url = receiptWhatsappUrl(PAYMENT)!;
    expect(url.startsWith('https://wa.me/573001234567?text=')).toBe(true);
    const text = decodeURIComponent(url.split('?text=')[1]);
    expect(text).toBe(receiptMessage(PAYMENT));
    expect(text).toContain('Hola Ana Pérez, te compartimos');
    expect(text).toContain('EF-000001');
    expect(text).toContain('$ 90.000');
    expect(text).toContain('del 10/11/2026 al 09/12/2026');
  });

  it('sin teléfono o sin comprobante no hay enlace', () => {
    expect(receiptWhatsappUrl({ ...PAYMENT, clientPhone: null })).toBeNull();
    expect(receiptWhatsappUrl({ ...PAYMENT, receiptNumber: null })).toBeNull();
  });

  it('incluye el nombre comercial del gimnasio cuando está configurado', () => {
    expect(receiptMessage(PAYMENT, ' Gimnasio Ficticio ')).toMatch(
      /^Hola Ana Pérez, de parte de Gimnasio Ficticio, te compartimos el comprobante EF-000001/,
    );
    expect(receiptMessage(PAYMENT, '  ')).toMatch(/^Hola Ana Pérez, te compartimos/);
    const text = decodeURIComponent(
      receiptWhatsappUrl(PAYMENT, 'Gimnasio Ficticio')!.split('?text=')[1],
    );
    expect(text).toContain('de parte de Gimnasio Ficticio');
  });

  describe('recordatorio de vencimiento', () => {
    const ALERT = {
      clientName: 'Ana Pérez',
      clientPhone: '+57 300 123 4567',
      planName: 'Mensual',
      endDate: '2026-11-15',
      daysToEnd: 5,
    } as ExpirationAlert;

    it('usa el mensaje del dueño con el nombre comercial', () => {
      expect(expirationMessage(ALERT, 'Gimnasio Ficticio')).toBe(
        'Hola Ana Pérez, de parte de Gimnasio Ficticio, tu plan Mensual vence el 15/11/2026. ' +
          'Si quieres renovarlo, te esperamos.',
      );
    });

    it('si ya venció dice "venció" y sin nombre comercial omite "de parte de"', () => {
      expect(expirationMessage({ ...ALERT, daysToEnd: -2 }, null)).toBe(
        'Hola Ana Pérez, tu plan Mensual venció el 15/11/2026. Si quieres renovarlo, te esperamos.',
      );
    });

    it('normaliza el número como el comprobante y sin teléfono no hay enlace', () => {
      const url = expirationWhatsappUrl(ALERT, 'Gimnasio Ficticio')!;
      expect(url.startsWith('https://wa.me/573001234567?text=')).toBe(true);
      expect(expirationWhatsappUrl({ ...ALERT, clientPhone: '3001234567' })).toContain('wa.me/573001234567?');
      expect(expirationWhatsappUrl({ ...ALERT, clientPhone: null })).toBeNull();
      expect(expirationWhatsappUrl({ ...ALERT, clientPhone: '123' })).toBeNull();
    });
  });
});
