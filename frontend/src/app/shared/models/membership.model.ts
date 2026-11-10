/**
 * El backend omite los campos nulos (Jackson non_null), por eso casi todo es opcional.
 * `status` siempre es el calculado para hoy por el backend.
 */
export type MembershipStatus = 'PENDING' | 'SCHEDULED' | 'ACTIVE' | 'FROZEN' | 'EXPIRED' | 'CANCELLED';
export type FreezeStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface Freeze {
  id: number;
  membershipId: number;
  startDate: string;
  endDate: string;
  days: number;
  appliedDays: number;
  status: FreezeStatus;
  reason: string;
  countsTowardLimit: boolean;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  createdAt?: string | null;
}

export interface Membership {
  id: number;
  clientId: number;
  clientName: string;
  clientDocument: string;
  planId: number;
  planName: string;
  planDurationDays: number;
  startDate: string;
  endDate: string;
  baseEndDate: string;
  status: MembershipStatus;
  priceAtPurchase: number;
  notes?: string | null;
  migrated: boolean;
  migrationNote?: string | null;
  freezeUsedInPreviousSystem: boolean;
  canFreeze: boolean;
  freezes: Freeze[];
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  createdAt?: string | null;
}

export interface CurrentMembership {
  current?: Membership | null;
  next?: Membership | null;
}

export interface MembershipPreview {
  planName: string;
  price: number;
  startDate: string;
  endDate: string;
  scheduled: boolean;
  currentEndDate?: string | null;
}

export interface CreateMembershipRequest {
  clientId: number;
  planId: number;
  startDate: string | null;
  notes: string | null;
}

export interface MigratedMembershipRequest {
  clientId: number;
  planId: number;
  startDate: string;
  endDate: string;
  price: number | null;
  migrationNote: string;
  freezeUsedInPreviousSystem: boolean;
  notes: string | null;
}

export interface CreateFreezeRequest {
  startDate: string;
  days: number;
  reason: string;
}

export interface ExtendFreezeRequest {
  totalDays: number;
  reason: string;
}

export const MEMBERSHIP_STATUS_LABELS: Record<MembershipStatus, string> = {
  PENDING: 'Pendiente de pago',
  SCHEDULED: 'Programada',
  ACTIVE: 'Activa',
  FROZEN: 'Congelada',
  EXPIRED: 'Vencida',
  CANCELLED: 'Anulada',
};

export const FREEZE_STATUS_LABELS: Record<FreezeStatus, string> = {
  PENDING: 'Programada',
  ACTIVE: 'En curso',
  COMPLETED: 'Terminada',
  CANCELLED: 'Cancelada',
};

/** Igual que en el backend (app.memberships). Solo se usa para validar y mostrar; el backend decide. */
export const MAX_FREEZE_DAYS = 30;
export const EXPIRY_WARNING_DAYS = 5;
