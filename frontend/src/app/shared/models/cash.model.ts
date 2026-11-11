import { PaymentMethod } from './payment.model';

export type CashClosingStatus = 'PENDING_COUNT' | 'NO_ACTIVITY' | 'COUNTED' | 'PENDING_REVIEW' | 'REVIEWED';
export type WithdrawalCategory = 'SUPPLIES' | 'CLEANING' | 'OTHER';
export type MovementType = 'PAYMENT' | 'PAYMENT_VOID' | 'WITHDRAWAL';
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export const WITHDRAWAL_CATEGORIES: { value: WithdrawalCategory; label: string }[] = [
  { value: 'SUPPLIES', label: 'Insumos' },
  { value: 'CLEANING', label: 'Aseo' },
  { value: 'OTHER', label: 'Otros' },
];

export const CLOSING_STATUS_LABELS: Record<CashClosingStatus, string> = {
  PENDING_COUNT: 'Pendiente de arqueo',
  NO_ACTIVITY: 'Sin movimientos',
  COUNTED: 'Cuadrado',
  PENDING_REVIEW: 'Diferencia por revisar',
  REVIEWED: 'Diferencia revisada',
};

export const MOVEMENT_LABELS: Record<MovementType, string> = {
  PAYMENT: 'Pago',
  PAYMENT_VOID: 'Anulación',
  WITHDRAWAL: 'Retiro',
};

export const DAYS: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Lunes' },
  { value: 'TUESDAY', label: 'Martes' },
  { value: 'WEDNESDAY', label: 'Miércoles' },
  { value: 'THURSDAY', label: 'Jueves' },
  { value: 'FRIDAY', label: 'Viernes' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
];

export function categoryLabel(category: WithdrawalCategory | null | undefined): string {
  return WITHDRAWAL_CATEGORIES.find((c) => c.value === category)?.label ?? '';
}

export interface MethodTotal {
  method: PaymentMethod;
  receivedCount: number;
  receivedAmount: number;
  voidedCount: number;
  voidedAmount: number;
}

export interface CashSummary {
  openingBase: number;
  cashIncome: number;
  cashVoided: number;
  cashWithdrawals: number;
  expectedCash: number;
  hasCashActivity: boolean;
  methods: MethodTotal[];
}

/** cashEffect: lo que suma o resta al efectivo de la caja (0 para otros métodos o retiros anulados). */
export interface CashMovement {
  type: MovementType;
  id: number;
  at: string;
  method: PaymentMethod;
  amount: number;
  cashEffect: number;
  description: string | null;
  receiptNumber: string | null;
  category: WithdrawalCategory | null;
  voided: boolean;
}

export interface CashToday {
  started: boolean;
  startDate: string | null;
  cashDate: string | null;
  windowStart: string | null;
  cutoffAt: string | null;
  openDay: boolean;
  summary: CashSummary | null;
  movements: CashMovement[];
  pendingCounts: number;
  /** Solo llega para el ADMIN. */
  pendingReviews?: number | null;
}

export interface CashClosing {
  id: number;
  cashDate: string;
  windowStart: string;
  windowEnd: string;
  status: CashClosingStatus;
  closingMode: 'ON_TIME' | 'CATCH_UP';
  wasOpenDay: boolean;
  hasCashActivity: boolean;
  openingBase: number;
  cashIncome: number;
  cashVoided: number;
  cashWithdrawals: number;
  expectedCash: number;
  closedAt: string;
  postCutIncome?: number | null;
  postCutOutflow?: number | null;
  adjustedExpectedCash?: number | null;
  countedCash?: number | null;
  difference?: number | null;
  countNote?: string | null;
  countedBy?: string | null;
  countedAt?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
}

export interface CashClosingDetail {
  closing: CashClosing;
  methods: MethodTotal[];
  movements: CashMovement[];
  postCutMovements: CashMovement[];
}

export interface CountPreview {
  closing: CashClosing;
  postCutIncome: number;
  postCutOutflow: number;
  expectedAdjustedCash: number;
  postCutMovements: CashMovement[];
}

/** expectedAdjustedCash: el esperado ajustado que vio recepción; si cambió, el backend pide revisarlo. */
export interface CountRequest {
  countedCash: number;
  note: string | null;
  expectedAdjustedCash: number;
}

export interface WithdrawalRequest {
  requestId: string;
  amount: number;
  category: WithdrawalCategory;
  concept: string;
}

export interface Withdrawal {
  id: number;
  amount: number;
  category: WithdrawalCategory;
  categoryLabel: string;
  concept: string;
  occurredAt: string;
  cashDate: string;
  registeredBy: string | null;
  voided: boolean;
  voidedAt?: string | null;
  voidReason?: string | null;
}

export interface CashSettings {
  openingBase: number;
  startDate: string | null;
  startDateLocked: boolean;
  openDays: DayOfWeek[];
  closingTime: string;
}

export interface CashSettingsRequest {
  openingBase: number;
  startDate: string | null;
  openDays: DayOfWeek[];
}

export interface CategoryTotal {
  category: WithdrawalCategory;
  label: string;
  count: number;
  amount: number;
}

export interface MonthlyReport {
  month: string;
  closedDays: number;
  cashIncome: number;
  cashVoided: number;
  cashWithdrawals: number;
  countedDifference: number;
  pendingCounts: number;
  pendingReviews: number;
  methods: MethodTotal[];
  withdrawalsByCategory: CategoryTotal[];
  days: CashClosing[];
}

export interface ClosingFilters {
  from: string | null;
  to: string | null;
  status: CashClosingStatus | null;
}

/** Diferencia del arqueo: contado − esperado ajustado. */
export function countDifference(counted: number, expectedAdjusted: number): number {
  return Math.round((counted - expectedAdjusted) * 100) / 100;
}

export interface AmountTotals {
  count: number;
  received: number;
  voided: number;
  /** Recibido − anulado. */
  net: number;
}

/**
 * Totales del informe por método. `all` es lo recaudado (todos los métodos, neto de anulaciones); no
 * descuenta retiros ni incluye la base, así que no es el efectivo esperado del cajón.
 */
export interface MethodTotalsSummary {
  cash: AmountTotals;
  other: AmountTotals;
  all: AmountTotals;
}

export function summarizeMethods(methods: MethodTotal[]): MethodTotalsSummary {
  const sum = (rows: MethodTotal[]): AmountTotals => {
    const received = cents(rows.reduce((s, m) => s + Number(m.receivedAmount), 0));
    const voided = cents(rows.reduce((s, m) => s + Number(m.voidedAmount), 0));
    return { count: rows.reduce((s, m) => s + m.receivedCount, 0), received, voided, net: cents(received - voided) };
  };
  return {
    cash: sum(methods.filter((m) => m.method === 'CASH')),
    other: sum(methods.filter((m) => m.method !== 'CASH')),
    all: sum(methods),
  };
}

function cents(value: number): number {
  return Math.round(value * 100) / 100;
}
