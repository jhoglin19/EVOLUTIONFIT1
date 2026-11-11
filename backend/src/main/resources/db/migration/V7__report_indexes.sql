-- Fase 7: panel de inicio, alertas de vencimiento y reportes.
-- Solo agrega índices para las consultas por rango de fechas. No cambia ni borra datos.

-- Reporte de ingresos y de pagos anulados: lo anulado se cuenta por la fecha de anulación.
-- (idx_payments_void_cash_closing tiene voided_at en segunda posición y no sirve para un rango).
ALTER TABLE `payments`
  ADD KEY `idx_payments_voided_at` (`voided_at`);

-- Inscripciones del día y clientes nuevos: una membresía cuenta desde que el pago la activa
-- (activated_at) o, si es migrada o anterior a la fase 5, desde que se registró (created_at).
ALTER TABLE `memberships`
  ADD KEY `idx_memberships_activated_at` (`activated_at`),
  ADD KEY `idx_memberships_created_at` (`created_at`);
