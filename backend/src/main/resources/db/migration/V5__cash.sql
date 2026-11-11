-- Fase 6: caja (docs/REGLAS_NEGOCIO.md, sección 4).
-- No borra ni modifica filas existentes.
-- * Los ingresos se leen de payments (única fuente). cash_movements guarda solo retiros (EXPENSE).
-- * Cada fila se marca con el cierre que la contó (cash_closing_id / void_cash_closing_id): un cierre solo
--   marca filas sin marcar, así cada peso se cuenta una vez y los días cerrados no cambian.
-- * El movimiento INCOME del pago de prueba anterior a la fase 5 queda tal cual y fuera de la caja
--   (la caja solo lee EXPENSE de cash_movements y empieza en la fecha que fije el ADMIN).

-- ---------------------------------------------------------------------------
-- 1. Configuración de caja (una sola fila, la edita el ADMIN)
--    start_date NULL: la caja no ha empezado y el cierre automático no hace nada.
-- ---------------------------------------------------------------------------
CREATE TABLE `cash_settings` (
  `id` tinyint unsigned NOT NULL,
  `opening_base` decimal(12,2) NOT NULL DEFAULT '50000.00',
  `start_date` date DEFAULT NULL,
  `open_days` varchar(40) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI,SAT,SUN',
  `updated_by` bigint unsigned DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_cash_settings_users` (`updated_by`),
  CONSTRAINT `fk_cash_settings_users` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_cash_settings_single` CHECK ((`id` = 1)),
  CONSTRAINT `chk_cash_settings_base` CHECK ((`opening_base` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `cash_settings` (`id`) VALUES (1);

-- ---------------------------------------------------------------------------
-- 2. Cierres diarios. La tabla de V1 está vacía; se completa con lo que congela el cierre automático
--    y con el arqueo. period_start = period_end = cash_date. closed_by NULL = lo cerró el sistema.
--    Columnas de V1 que se siguen llenando: total_income = efectivo recibido, total_expenses = retiros,
--    total_adjustments = efectivo anulado, balance = efectivo esperado.
-- ---------------------------------------------------------------------------
ALTER TABLE `cash_closings`
  ADD COLUMN `cash_date` date NOT NULL AFTER `id`,
  ADD COLUMN `window_start` datetime(6) NOT NULL AFTER `period_end`,
  ADD COLUMN `window_end` datetime(6) NOT NULL AFTER `window_start`,
  ADD COLUMN `status` varchar(20) NOT NULL AFTER `window_end`,
  ADD COLUMN `closing_mode` varchar(20) NOT NULL AFTER `status`,
  ADD COLUMN `was_open_day` tinyint(1) NOT NULL AFTER `closing_mode`,
  ADD COLUMN `has_cash_activity` tinyint(1) NOT NULL AFTER `was_open_day`,
  ADD COLUMN `opening_base` decimal(12,2) NOT NULL AFTER `has_cash_activity`,
  ADD COLUMN `cash_income` decimal(12,2) NOT NULL AFTER `opening_base`,
  ADD COLUMN `cash_voided` decimal(12,2) NOT NULL AFTER `cash_income`,
  ADD COLUMN `cash_withdrawals` decimal(12,2) NOT NULL AFTER `cash_voided`,
  ADD COLUMN `expected_cash` decimal(12,2) NOT NULL AFTER `cash_withdrawals`,
  -- Arqueo: lo contado se compara con el esperado ajustado por el efectivo que entró o salió después del corte.
  ADD COLUMN `post_cut_income` decimal(12,2) DEFAULT NULL AFTER `notes`,
  ADD COLUMN `post_cut_outflow` decimal(12,2) DEFAULT NULL AFTER `post_cut_income`,
  ADD COLUMN `adjusted_expected_cash` decimal(12,2) DEFAULT NULL AFTER `post_cut_outflow`,
  ADD COLUMN `counted_cash` decimal(12,2) DEFAULT NULL AFTER `adjusted_expected_cash`,
  ADD COLUMN `difference` decimal(12,2) DEFAULT NULL AFTER `counted_cash`,
  ADD COLUMN `post_cut_detail` json DEFAULT NULL AFTER `difference`,
  ADD COLUMN `count_note` varchar(500) DEFAULT NULL AFTER `post_cut_detail`,
  ADD COLUMN `counted_by` bigint unsigned DEFAULT NULL AFTER `count_note`,
  ADD COLUMN `counted_at` datetime(6) DEFAULT NULL AFTER `counted_by`,
  ADD COLUMN `reviewed_by` bigint unsigned DEFAULT NULL AFTER `counted_at`,
  ADD COLUMN `reviewed_at` datetime(6) DEFAULT NULL AFTER `reviewed_by`,
  ADD COLUMN `review_note` varchar(500) DEFAULT NULL AFTER `reviewed_at`,
  ADD UNIQUE KEY `uq_closings_cash_date` (`cash_date`),
  ADD KEY `idx_closings_status` (`status`),
  ADD KEY `fk_closings_counted_by` (`counted_by`),
  ADD KEY `fk_closings_reviewed_by` (`reviewed_by`),
  ADD CONSTRAINT `fk_closings_counted_by` FOREIGN KEY (`counted_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_closings_reviewed_by` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `chk_closings_daily` CHECK (((`period_start` = `cash_date`) and (`period_end` = `cash_date`))),
  ADD CONSTRAINT `chk_closings_window` CHECK ((`window_end` > `window_start`)),
  ADD CONSTRAINT `chk_closings_status` CHECK ((`status` in (_utf8mb4'PENDING_COUNT',_utf8mb4'NO_ACTIVITY',
      _utf8mb4'COUNTED',_utf8mb4'PENDING_REVIEW',_utf8mb4'REVIEWED'))),
  ADD CONSTRAINT `chk_closings_mode` CHECK ((`closing_mode` in (_utf8mb4'ON_TIME',_utf8mb4'CATCH_UP'))),
  ADD CONSTRAINT `chk_closings_amounts` CHECK (((`opening_base` >= 0) and (`cash_income` >= 0)
      and (`cash_voided` >= 0) and (`cash_withdrawals` >= 0))),
  ADD CONSTRAINT `chk_closings_expected` CHECK ((`expected_cash`
      = ((`opening_base` + `cash_income`) - `cash_voided` - `cash_withdrawals`))),
  -- Sin arqueo: nada contado. Con arqueo: diferencia = contado − esperado ajustado.
  ADD CONSTRAINT `chk_closings_count` CHECK (
      ((`status` in (_utf8mb4'PENDING_COUNT',_utf8mb4'NO_ACTIVITY')) and (`counted_cash` is null)
          and (`difference` is null) and (`adjusted_expected_cash` is null) and (`counted_at` is null))
      or ((`status` in (_utf8mb4'COUNTED',_utf8mb4'PENDING_REVIEW',_utf8mb4'REVIEWED'))
          and (`counted_cash` is not null) and (`counted_at` is not null)
          and (`post_cut_income` >= 0) and (`post_cut_outflow` >= 0)
          and (`adjusted_expected_cash` = ((`expected_cash` + `post_cut_income`) - `post_cut_outflow`))
          and (`difference` = (`counted_cash` - `adjusted_expected_cash`)))),
  -- Solo los arqueos con diferencia quedan por revisar; la revisión lleva nota.
  ADD CONSTRAINT `chk_closings_review` CHECK (
      ((`status` <> _utf8mb4'COUNTED') or (`difference` = 0))
      and ((`status` not in (_utf8mb4'PENDING_REVIEW',_utf8mb4'REVIEWED')) or (`difference` <> 0))
      and ((`status` <> _utf8mb4'REVIEWED') or ((`reviewed_at` is not null) and (`review_note` is not null)))
      and ((`status` = _utf8mb4'REVIEWED') or (`reviewed_at` is null)));

-- ---------------------------------------------------------------------------
-- 3. Informe por método de pago de cada cierre (base del reporte mensual)
-- ---------------------------------------------------------------------------
CREATE TABLE `cash_closing_totals` (
  `cash_closing_id` bigint unsigned NOT NULL,
  `payment_method` varchar(30) NOT NULL,
  `received_count` int unsigned NOT NULL DEFAULT '0',
  `received_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `voided_count` int unsigned NOT NULL DEFAULT '0',
  `voided_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`cash_closing_id`,`payment_method`),
  CONSTRAINT `fk_closing_totals_closings` FOREIGN KEY (`cash_closing_id`) REFERENCES `cash_closings` (`id`)
      ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_closing_totals_method` CHECK ((`payment_method` in (_utf8mb4'CASH',_utf8mb4'BANK_TRANSFER',
      _utf8mb4'NEQUI',_utf8mb4'DAVIPLATA',_utf8mb4'CARD',_utf8mb4'OTHER'))),
  CONSTRAINT `chk_closing_totals_amounts` CHECK (((`received_amount` >= 0) and (`voided_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 4. payments: cierre que contó el ingreso y cierre que descontó la anulación
-- ---------------------------------------------------------------------------
ALTER TABLE `payments`
  ADD COLUMN `cash_closing_id` bigint unsigned NULL AFTER `void_reason`,
  ADD COLUMN `void_cash_closing_id` bigint unsigned NULL AFTER `cash_closing_id`,
  ADD KEY `idx_payments_cash_closing` (`cash_closing_id`,`paid_at`),
  ADD KEY `idx_payments_void_cash_closing` (`void_cash_closing_id`,`voided_at`),
  ADD CONSTRAINT `fk_payments_cash_closing` FOREIGN KEY (`cash_closing_id`) REFERENCES `cash_closings` (`id`)
      ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD CONSTRAINT `fk_payments_void_cash_closing` FOREIGN KEY (`void_cash_closing_id`) REFERENCES `cash_closings` (`id`)
      ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD CONSTRAINT `chk_payments_void_cash_closing` CHECK (((`void_cash_closing_id` is null) or (`status` = _utf8mb4'VOIDED')));

-- ---------------------------------------------------------------------------
-- 5. cash_movements: retiros de efectivo con categoría, idempotencia y anulación (solo ADMIN, antes del corte)
-- ---------------------------------------------------------------------------
ALTER TABLE `cash_movements`
  ADD COLUMN `category` varchar(20) NULL AFTER `movement_type`,
  ADD COLUMN `request_id` varchar(36) NULL AFTER `description`,
  ADD COLUMN `voided_at` datetime(6) NULL AFTER `occurred_at`,
  ADD COLUMN `voided_by` bigint unsigned NULL AFTER `voided_at`,
  ADD COLUMN `void_reason` varchar(255) NULL AFTER `voided_by`,
  ADD UNIQUE KEY `uq_movements_request_id` (`request_id`),
  ADD KEY `idx_movements_closing_date` (`cash_closing_id`,`occurred_at`),
  ADD KEY `fk_movements_voided_by` (`voided_by`),
  ADD CONSTRAINT `fk_movements_voided_by` FOREIGN KEY (`voided_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  -- payment_id no puede ir en un CHECK (su FK de V1 tiene ON UPDATE CASCADE): que un retiro no tenga pago lo valida el servicio.
  ADD CONSTRAINT `chk_movements_expense_category` CHECK (((`movement_type` <> _utf8mb4'EXPENSE') or (`category` is not null))),
  ADD CONSTRAINT `chk_movements_void_reason` CHECK (((`voided_at` is null) or (`void_reason` is not null)));
