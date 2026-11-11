import { ClientRequest } from './client.model';
import { MembershipStatus } from './membership.model';

export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'NEQUI' | 'DAVIPLATA' | 'CARD' | 'OTHER';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'VOIDED' | 'REFUNDED';

export const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'CASH', label: 'Efectivo' },
  { value: 'BANK_TRANSFER', label: 'Transferencia bancaria' },
  { value: 'NEQUI', label: 'Nequi' },
  { value: 'DAVIPLATA', label: 'Daviplata' },
  { value: 'CARD', label: 'Tarjeta' },
  { value: 'OTHER', label: 'Otro' },
];

/** Igual que en el backend (docs/REGLAS_NEGOCIO.md, sección 3). El backend lo vuelve a validar. */
export const REFERENCE_REQUIRED: PaymentMethod[] = ['BANK_TRANSFER', 'NEQUI', 'DAVIPLATA'];

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Pendiente',
  COMPLETED: 'Pagado',
  VOIDED: 'Anulado',
  REFUNDED: 'Devuelto',
};

export function methodLabel(method: PaymentMethod): string {
  return PAYMENT_METHODS.find((m) => m.value === method)?.label ?? method;
}

export interface Payment {
  id: number;
  membershipId: number;
  clientId: number;
  clientName: string;
  clientDocument: string;
  /** Para el enlace de WhatsApp. Null si el cliente no tiene teléfono. */
  clientPhone?: string | null;
  planName: string;
  membershipStartDate: string;
  membershipEndDate: string;
  membershipStatus: MembershipStatus;
  listPrice: number;
  discountAmount: number;
  discountReason?: string | null;
  amount: number;
  method: PaymentMethod;
  reference?: string | null;
  notes?: string | null;
  status: PaymentStatus;
  paidAt: string;
  voidedAt?: string | null;
  voidReason?: string | null;
  /** Null en pagos registrados antes del sistema de comprobantes. */
  receiptNumber?: string | null;
}

/** `requestId` identifica el intento de cobro: si llega dos veces, el backend devuelve el mismo pago. */
export interface PaymentRequest {
  requestId: string;
  method: PaymentMethod;
  reference: string | null;
  notes: string | null;
  expectedAmount: number;
  discountAmount: number | null;
  discountReason: string | null;
}

export interface EnrollmentRequest {
  clientId: number | null;
  client: ClientRequest | null;
  planId: number;
  startDate: string | null;
  notes: string | null;
  payment: PaymentRequest;
}

export interface PaymentFilters {
  from: string | null;
  to: string | null;
  method: PaymentMethod | null;
  status: PaymentStatus | null;
  query: string;
}
