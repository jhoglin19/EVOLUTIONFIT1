import { MethodTotal } from './cash.model';
import { PaymentMethod, PaymentStatus } from './payment.model';

export type IncomeGrouping = 'DAY' | 'WEEK' | 'MONTH';
export type ReportName = 'income' | 'new-clients' | 'plans' | 'voided-payments' | 'discounts' | 'expirations';

export interface IncomePeriod {
  start: string;
  end: string;
  receivedCount: number;
  receivedAmount: number;
  voidedCount: number;
  voidedAmount: number;
  netAmount: number;
  cashNet: number;
  otherNet: number;
}

export interface IncomeReport {
  from: string;
  to: string;
  grouping: IncomeGrouping;
  methods: MethodTotal[];
  periods: IncomePeriod[];
}

export interface NewClientRow {
  type: 'NEW' | 'MIGRATED';
  clientId: number;
  clientName: string;
  clientDocument: string;
  clientPhone?: string | null;
  clientActive: boolean;
  membershipId: number;
  planName: string;
  at: string;
  paidAmount?: number | null;
}

export interface NewClientsReport {
  from: string;
  to: string;
  newClients: number;
  migratedClients: number;
  rows: NewClientRow[];
}

export interface PlanSalesRow {
  planId: number;
  planNames: string;
  sold: number;
  newClients: number;
  renewals: number;
  revenue: number;
  discounts: number;
}

export interface PlanSalesReport {
  from: string;
  to: string;
  totalSold: number;
  totalRevenue: number;
  rows: PlanSalesRow[];
}

export interface VoidedPaymentRow {
  paymentId: number;
  receiptNumber?: string | null;
  clientId: number;
  clientName: string;
  clientDocument: string;
  planName: string;
  method: PaymentMethod;
  amount: number;
  paidAt: string;
  voidedAt: string;
  voidedBy?: string | null;
  voidReason?: string | null;
}

export interface VoidedPaymentsReport {
  from: string;
  to: string;
  count: number;
  amount: number;
  rows: VoidedPaymentRow[];
}

export interface DiscountRow {
  paymentId: number;
  receiptNumber?: string | null;
  clientId: number;
  clientName: string;
  clientDocument: string;
  planName: string;
  method: PaymentMethod;
  status: PaymentStatus;
  listPrice: number;
  discountAmount: number;
  amount: number;
  discountReason?: string | null;
  discountedBy?: string | null;
  paidAt: string;
}

export interface DiscountsReport {
  from: string;
  to: string;
  count: number;
  discountAmount: number;
  voidedCount: number;
  rows: DiscountRow[];
}

export type ExpirationState = 'IN_FORCE' | 'EXPIRED';
export type RenewalState = 'RENEWED' | 'PENDING_PAYMENT' | 'NOT_RENEWED';

export interface ExpirationRow {
  membershipId: number;
  clientId: number;
  clientName: string;
  clientDocument: string;
  clientPhone?: string | null;
  clientActive: boolean;
  planName: string;
  startDate: string;
  endDate: string;
  state: ExpirationState;
  renewal: RenewalState;
  renewedAt?: string | null;
  pendingRenewalId?: number | null;
}

export interface ExpirationsReport {
  from: string;
  to: string;
  total: number;
  expired: number;
  renewed: number;
  pendingPayment: number;
  notRenewed: number;
  /** Porcentaje de las ya vencidas que renovaron; null si no hay vencidas. */
  renewalRate?: number | null;
  rows: ExpirationRow[];
}

export const RENEWAL_LABELS: Record<RenewalState, string> = {
  RENEWED: 'Renovó',
  PENDING_PAYMENT: 'Renovación pendiente de pago',
  NOT_RENEWED: 'Sin renovar',
};

export const GROUPING_LABELS: Record<IncomeGrouping, string> = {
  DAY: 'Por día',
  WEEK: 'Por semana',
  MONTH: 'Por mes',
};
