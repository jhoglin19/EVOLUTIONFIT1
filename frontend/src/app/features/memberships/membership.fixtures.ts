import { Membership } from '../../shared/models/membership.model';

/** Datos de prueba compartidos por las pruebas de membresías. */
export function membership(overrides: Partial<Membership> = {}): Membership {
  return {
    id: 10,
    clientId: 1,
    clientName: 'Ana Pérez',
    clientDocument: '1001',
    planId: 2,
    planName: 'Mensual',
    planDurationDays: 30,
    startDate: '2026-11-01',
    endDate: '2026-11-30',
    baseEndDate: '2026-11-30',
    status: 'ACTIVE',
    priceAtPurchase: 90000,
    migrated: false,
    freezeUsedInPreviousSystem: false,
    canFreeze: true,
    freezes: [],
    ...overrides,
  };
}
