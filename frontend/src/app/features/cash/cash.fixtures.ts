import { MethodTotal } from '../../shared/models/cash.model';

/** El informe del 10-nov-2026 en la base actual: 2 pagos en efectivo y 8 por otros métodos. */
export const NOV_10: MethodTotal[] = [
  { method: 'BANK_TRANSFER', receivedCount: 1, receivedAmount: 170000, voidedCount: 0, voidedAmount: 0 },
  { method: 'CASH', receivedCount: 2, receivedAmount: 180000, voidedCount: 0, voidedAmount: 0 },
  { method: 'DAVIPLATA', receivedCount: 1, receivedAmount: 780000, voidedCount: 0, voidedAmount: 0 },
  { method: 'NEQUI', receivedCount: 6, receivedAmount: 1150000, voidedCount: 0, voidedAmount: 0 },
];
