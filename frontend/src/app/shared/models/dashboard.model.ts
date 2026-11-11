import { MethodTotal } from './cash.model';
import { MembershipStatus } from './membership.model';
import { PaymentMethod, PaymentStatus } from './payment.model';

export type ExpirationAlertKind = 'EXPIRING' | 'EXPIRED';

/** Una alerta de vencimiento. daysToEnd: 0 vence hoy, 3 vence en tres días, −2 venció anteayer. */
export interface ExpirationAlert {
  membershipId: number;
  clientId: number;
  clientName: string;
  clientDocument: string;
  clientPhone?: string | null;
  planName: string;
  startDate: string;
  endDate: string;
  status: MembershipStatus;
  daysToEnd: number;
  /** Renovación registrada pero pendiente de pago: se cobra en vez de crear otra. */
  pendingRenewalId?: number | null;
  pendingRenewalPlanName?: string | null;
}

export interface AlertCounts {
  expiring: number;
  recentlyExpired: number;
  warningDays: number;
  recentlyExpiredDays: number;
}

/** Primera membresía del cliente (de cualquier tipo) = cliente nuevo; si no, renovación. */
export interface EnrollmentCounts {
  newClients: number;
  renewals: number;
  migrated: number;
  pendingPayment: number;
}

export interface TodayPayment {
  id: number;
  paidAt: string;
  clientId: number;
  clientName: string;
  planName: string;
  method: PaymentMethod;
  amount: number;
  status: PaymentStatus;
  receiptNumber?: string | null;
}

/** Estado de la caja con su propio día (corte de las 10 p.m.). pendingReviews solo llega al ADMIN. */
export interface CashCard {
  started: boolean;
  startDate?: string | null;
  cashDate?: string | null;
  cutoffAt?: string | null;
  openDay: boolean;
  closedReason?: string | null;
  pendingCounts: { closingId: number; cashDate: string }[];
  pendingReviews?: number | null;
}

export interface Dashboard {
  today: string;
  warningDays: number;
  recentlyExpiredDays: number;
  activeMemberships: number;
  frozenMemberships: number;
  expiringCount: number;
  expiring: ExpirationAlert[];
  recentlyExpiredCount: number;
  recentlyExpired: ExpirationAlert[];
  enrollments: EnrollmentCounts;
  paymentsCount: number;
  voidedPaymentsCount: number;
  payments: TodayPayment[];
  cash: CashCard;
}

/** Solo ADMIN: ingresos de hoy y del mes en curso, con la regla de Caja y días calendario. */
export interface AdminDashboard {
  today: string;
  monthStart: string;
  todayMethods: MethodTotal[];
  monthMethods: MethodTotal[];
  monthVoidedCount: number;
  monthVoidedAmount: number;
  monthDiscountCount: number;
  monthDiscountAmount: number;
}

/** Ruta a la que lleva la acción principal de una alerta: cobrar la renovación pendiente o renovar. */
export function renewLink(a: ExpirationAlert): { path: string[]; queryParams: Record<string, number>; label: string } {
  return a.pendingRenewalId
    ? { path: ['/enrollment'], queryParams: { membershipId: a.pendingRenewalId }, label: 'Cobrar renovación' }
    : { path: ['/enrollment'], queryParams: { clientId: a.clientId }, label: 'Renovar' };
}

/** "Vence hoy", "Vence en 3 días", "Venció ayer", "Venció hace 4 días". */
export function daysLabel(daysToEnd: number): string {
  if (daysToEnd === 0) {
    return 'Vence hoy';
  }
  if (daysToEnd === 1) {
    return 'Vence mañana';
  }
  if (daysToEnd > 1) {
    return `Vence en ${daysToEnd} días`;
  }
  return daysToEnd === -1 ? 'Venció ayer' : `Venció hace ${-daysToEnd} días`;
}
