import { Dashboard, ExpirationAlert } from '../../shared/models/dashboard.model';

/** Datos ficticios para las pruebas. */
export function alert(overrides: Partial<ExpirationAlert> = {}): ExpirationAlert {
  return {
    membershipId: 11,
    clientId: 5,
    clientName: 'Ana Pérez',
    clientDocument: '1020304050',
    clientPhone: '3001234567',
    planName: 'Mensual',
    startDate: '2026-10-14',
    endDate: '2026-11-12',
    status: 'ACTIVE',
    daysToEnd: 2,
    pendingRenewalId: null,
    ...overrides,
  };
}

export function dashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    today: '2026-11-10',
    warningDays: 5,
    recentlyExpiredDays: 15,
    activeMemberships: 40,
    frozenMemberships: 2,
    expiringCount: 2,
    expiring: [alert(), alert({ membershipId: 12, clientId: 6, clientName: 'Luis Gómez', clientPhone: null, pendingRenewalId: 30 })],
    recentlyExpiredCount: 1,
    recentlyExpired: [alert({ membershipId: 13, clientId: 7, clientName: 'Marta Ruiz', endDate: '2026-11-08', daysToEnd: -2, status: 'EXPIRED' })],
    enrollments: { newClients: 1, renewals: 3, migrated: 0, pendingPayment: 1 },
    paymentsCount: 4,
    voidedPaymentsCount: 1,
    payments: [
      {
        id: 90,
        paidAt: '2026-11-10T08:30:00',
        clientId: 5,
        clientName: 'Ana Pérez',
        planName: 'Mensual',
        method: 'CASH',
        amount: 90000,
        status: 'COMPLETED',
        receiptNumber: 'EF-000010',
      },
    ],
    cash: {
      started: true,
      startDate: '2026-11-01',
      cashDate: '2026-11-10',
      cutoffAt: '2026-11-10T22:00:00',
      openDay: true,
      pendingCounts: [{ closingId: 3, cashDate: '2026-11-09' }],
      pendingReviews: null,
    },
    ...overrides,
  };
}
