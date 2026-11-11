import { NOV_10 } from '../../features/cash/cash.fixtures';
import { summarizeMethods } from './cash.model';

describe('summarizeMethods', () => {
  it('suma efectivo, otros métodos y el total general con los datos actuales', () => {
    const s = summarizeMethods(NOV_10);
    expect(s.cash).toEqual({ count: 2, received: 180000, voided: 0, net: 180000 });
    expect(s.other).toEqual({ count: 8, received: 2100000, voided: 0, net: 2100000 });
    expect(s.all).toEqual({ count: 10, received: 2280000, voided: 0, net: 2280000 });
  });

  it('descuenta las anulaciones del neto; tarjeta y otro cuentan como otros métodos', () => {
    const s = summarizeMethods([
      { method: 'CASH', receivedCount: 2, receivedAmount: 180000, voidedCount: 1, voidedAmount: 90000 },
      { method: 'CARD', receivedCount: 1, receivedAmount: 50000, voidedCount: 0, voidedAmount: 0 },
      { method: 'OTHER', receivedCount: 0, receivedAmount: 0, voidedCount: 1, voidedAmount: 20000 },
    ]);
    expect(s.cash.net).toBe(90000);
    expect(s.other).toEqual({ count: 1, received: 50000, voided: 20000, net: 30000 });
    expect(s.all).toEqual({ count: 3, received: 230000, voided: 110000, net: 120000 });
  });

  it('acepta montos con centavos (llegan como números del backend) y listas vacías', () => {
    expect(summarizeMethods([
      { method: 'NEQUI', receivedCount: 1, receivedAmount: 0.1, voidedCount: 0, voidedAmount: 0 },
      { method: 'CARD', receivedCount: 1, receivedAmount: 0.2, voidedCount: 0, voidedAmount: 0 },
    ]).other.net).toBe(0.3);
    expect(summarizeMethods([]).all).toEqual({ count: 0, received: 0, voided: 0, net: 0 });
  });
});
