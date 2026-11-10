-- Fase 4: membresías y congelaciones (docs/REGLAS_NEGOCIO.md, secciones 1 y 2).
-- No borra filas. Solo agrega columnas, rellena las nuevas a partir de los datos existentes
-- y recalcula el vencimiento con la regla "los días congelados se suman al vencimiento".

-- ---------------------------------------------------------------------------
-- 1. memberships: copia del plan al venderse, vencimiento base, migración y anulación
-- ---------------------------------------------------------------------------
ALTER TABLE `memberships`
  ADD COLUMN `plan_name` varchar(80) NULL AFTER `plan_id`,
  ADD COLUMN `plan_duration_days` int unsigned NULL AFTER `plan_name`,
  ADD COLUMN `base_end_date` date NULL AFTER `end_date`,
  ADD COLUMN `is_migrated` tinyint(1) NOT NULL DEFAULT '0' AFTER `notes`,
  ADD COLUMN `migration_note` varchar(255) NULL AFTER `is_migrated`,
  ADD COLUMN `freeze_used_in_previous_system` tinyint(1) NOT NULL DEFAULT '0' AFTER `migration_note`,
  ADD COLUMN `cancelled_at` datetime(6) NULL AFTER `created_by`,
  ADD COLUMN `cancelled_by` bigint unsigned NULL AFTER `cancelled_at`,
  ADD COLUMN `cancellation_reason` varchar(255) NULL AFTER `cancelled_by`;

-- Las membresías existentes toman el nombre y la duración actuales de su plan.
UPDATE `memberships` m
  JOIN `plans` p ON p.`id` = m.`plan_id`
   SET m.`plan_name` = p.`name`,
       m.`plan_duration_days` = p.`duration_days`;

-- El vencimiento base (sin congelaciones) parte del end_date actual.
UPDATE `memberships` SET `base_end_date` = `end_date`;

-- ---------------------------------------------------------------------------
-- 2. membership_freezes: motivo y fin obligatorios, datos de cancelación
-- ---------------------------------------------------------------------------
-- applied_days = días que se suman al vencimiento. Para una congelación no cancelada son todos sus días.
-- Una cancelada conserva los que tenga registrados.
-- V1 permitía congelaciones sin fin ni motivo (la base actual no tiene ninguna). Para poder exigirlos:
-- sin fin -> termina según sus días aplicados (mínimo 1); sin motivo -> texto fijo.
UPDATE `membership_freezes`
   SET `end_date` = DATE_ADD(`start_date`, INTERVAL (GREATEST(`applied_days`, 1) - 1) DAY)
 WHERE `end_date` IS NULL;

UPDATE `membership_freezes`
   SET `reason` = 'Sin motivo registrado (antes de V3)'
 WHERE `reason` IS NULL OR TRIM(`reason`) = '';

UPDATE `membership_freezes`
   SET `applied_days` = DATEDIFF(`end_date`, `start_date`) + 1
 WHERE `status` <> 'CANCELLED';

ALTER TABLE `membership_freezes`
  MODIFY COLUMN `end_date` date NOT NULL,
  MODIFY COLUMN `reason` varchar(255) NOT NULL,
  ADD COLUMN `cancelled_at` datetime(6) NULL AFTER `created_by`,
  ADD COLUMN `cancelled_by` bigint unsigned NULL AFTER `cancelled_at`,
  ADD COLUMN `cancellation_reason` varchar(255) NULL AFTER `cancelled_by`;

-- Antes de V3 no existía el motivo de cancelación: las ya canceladas reciben un texto fijo.
UPDATE `membership_freezes`
   SET `cancellation_reason` = 'Cancelada antes de V3 (sin motivo registrado)'
 WHERE `status` = 'CANCELLED'
   AND `cancellation_reason` IS NULL;

ALTER TABLE `membership_freezes`
  ADD KEY `fk_freezes_cancelled_by` (`cancelled_by`),
  ADD CONSTRAINT `fk_freezes_cancelled_by` FOREIGN KEY (`cancelled_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `chk_freezes_applied_days` CHECK ((`applied_days` <= (DATEDIFF(`end_date`, `start_date`) + 1))),
  ADD CONSTRAINT `chk_freezes_cancel_reason` CHECK (((`status` <> _utf8mb4'CANCELLED') or (`cancellation_reason` is not null)));

-- ---------------------------------------------------------------------------
-- 3. Recalcular el vencimiento: end_date = base_end_date + días congelados aplicados
-- ---------------------------------------------------------------------------
UPDATE `memberships` m
  JOIN (SELECT `membership_id`, SUM(`applied_days`) AS `days`
          FROM `membership_freezes`
         GROUP BY `membership_id`) f ON f.`membership_id` = m.`id`
   SET m.`end_date` = DATE_ADD(m.`base_end_date`, INTERVAL f.`days` DAY);

-- ---------------------------------------------------------------------------
-- 4. memberships: columnas obligatorias, nuevos estados y restricciones
-- ---------------------------------------------------------------------------
-- PENDING   = registrada, pendiente de pago (el pago total la activa, fase 5).
-- SCHEDULED = pagada (o migrada) con inicio futuro: renovación antes de vencer o fecha futura elegida.
ALTER TABLE `memberships` DROP CHECK `chk_memberships_status`;

-- Igual que en congelaciones: las anuladas antes de V3 reciben un motivo fijo.
UPDATE `memberships`
   SET `cancellation_reason` = 'Anulada antes de V3 (sin motivo registrado)'
 WHERE `status` = 'CANCELLED'
   AND `cancellation_reason` IS NULL;

ALTER TABLE `memberships`
  MODIFY COLUMN `plan_name` varchar(80) NOT NULL,
  MODIFY COLUMN `plan_duration_days` int unsigned NOT NULL,
  MODIFY COLUMN `base_end_date` date NOT NULL,
  ADD KEY `idx_memberships_end_date` (`end_date`),
  ADD KEY `fk_memberships_cancelled_by` (`cancelled_by`),
  ADD CONSTRAINT `fk_memberships_cancelled_by` FOREIGN KEY (`cancelled_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `chk_memberships_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'SCHEDULED',
      _utf8mb4'ACTIVE',_utf8mb4'FROZEN',_utf8mb4'EXPIRED',_utf8mb4'CANCELLED'))),
  ADD CONSTRAINT `chk_memberships_duration` CHECK ((`plan_duration_days` > 0)),
  ADD CONSTRAINT `chk_memberships_base_end` CHECK (((`base_end_date` >= `start_date`) and (`end_date` >= `base_end_date`))),
  ADD CONSTRAINT `chk_memberships_cancel_reason` CHECK (((`status` <> _utf8mb4'CANCELLED') or (`cancellation_reason` is not null)));
