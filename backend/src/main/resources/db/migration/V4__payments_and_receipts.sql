-- Fase 5: pagos y comprobantes PDF (docs/REGLAS_NEGOCIO.md, secciones 3 y 6).
-- No borra filas. El pago existente queda COMPLETED, con precio de lista = valor pagado y sin descuento,
-- y no recibe comprobante (los comprobantes empiezan con el primer pago registrado en esta fase).
-- Ningún dato real del gimnasio va aquí: el encabezado se carga después desde la pantalla de configuración.

-- ---------------------------------------------------------------------------
-- 1. memberships: fecha de inicio pedida al vender y fecha de activación por pago
-- ---------------------------------------------------------------------------
ALTER TABLE `memberships`
  ADD COLUMN `requested_start_date` date NULL AFTER `start_date`,
  ADD COLUMN `activated_at` datetime(6) NULL AFTER `requested_start_date`;

-- ---------------------------------------------------------------------------
-- 2. payments: precio de lista, descuento, identificador de solicitud y anulación
-- ---------------------------------------------------------------------------
ALTER TABLE `payments`
  ADD COLUMN `list_price` decimal(12,2) NULL AFTER `amount`,
  ADD COLUMN `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00' AFTER `list_price`,
  ADD COLUMN `discount_reason` varchar(255) NULL AFTER `discount_amount`,
  ADD COLUMN `discounted_by` bigint unsigned NULL AFTER `discount_reason`,
  ADD COLUMN `request_id` varchar(36) NULL AFTER `notes`,
  ADD COLUMN `voided_at` datetime(6) NULL AFTER `received_by`,
  ADD COLUMN `voided_by` bigint unsigned NULL AFTER `voided_at`,
  ADD COLUMN `void_reason` varchar(255) NULL AFTER `voided_by`;

-- Pagos anteriores: sin descuento, el precio de lista es lo pagado.
UPDATE `payments` SET `list_price` = `amount` + `discount_amount`;

-- V1 no tenía motivo de anulación: los ya anulados reciben un texto fijo.
UPDATE `payments`
   SET `void_reason` = 'Anulado antes de V4 (sin motivo registrado)'
 WHERE `status` = 'VOIDED'
   AND `void_reason` IS NULL;

-- Se permite total $0 (descuento total de un ADMIN o plan de precio 0).
ALTER TABLE `payments` DROP CHECK `chk_payments_amount`;

ALTER TABLE `payments`
  MODIFY COLUMN `list_price` decimal(12,2) NOT NULL,
  -- Solo para la restricción "un pago completado por membresía" (MySQL no tiene índices parciales).
  ADD COLUMN `completed_membership_id` bigint unsigned
      GENERATED ALWAYS AS (if((`status` = _utf8mb4'COMPLETED'), `membership_id`, NULL)) VIRTUAL,
  ADD UNIQUE KEY `uq_payments_request_id` (`request_id`),
  ADD UNIQUE KEY `uq_payments_completed_membership` (`completed_membership_id`),
  ADD KEY `fk_payments_discounted_by` (`discounted_by`),
  ADD KEY `fk_payments_voided_by` (`voided_by`),
  ADD CONSTRAINT `fk_payments_discounted_by` FOREIGN KEY (`discounted_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_payments_voided_by` FOREIGN KEY (`voided_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `chk_payments_amount` CHECK ((`amount` >= 0)),
  ADD CONSTRAINT `chk_payments_discount` CHECK (((`discount_amount` >= 0) and (`discount_amount` <= `list_price`))),
  ADD CONSTRAINT `chk_payments_total` CHECK ((`amount` = (`list_price` - `discount_amount`))),
  ADD CONSTRAINT `chk_payments_discount_reason` CHECK (((`discount_amount` = 0) or (`discount_reason` is not null))),
  ADD CONSTRAINT `chk_payments_void_reason` CHECK (((`status` <> _utf8mb4'VOIDED') or (`void_reason` is not null)));

-- ---------------------------------------------------------------------------
-- 3. Datos del encabezado del comprobante (una sola fila, editable por el ADMIN)
-- ---------------------------------------------------------------------------
CREATE TABLE `business_settings` (
  `id` tinyint unsigned NOT NULL,
  `trade_name` varchar(150) DEFAULT NULL,
  `legal_name` varchar(150) DEFAULT NULL,
  `tax_id` varchar(30) DEFAULT NULL,
  `commercial_registration` varchar(50) DEFAULT NULL,
  `address` varchar(200) DEFAULT NULL,
  `city` varchar(80) DEFAULT NULL,
  `phone` varchar(50) DEFAULT NULL,
  `email` varchar(150) DEFAULT NULL,
  `logo` mediumblob,
  `logo_content_type` varchar(30) DEFAULT NULL,
  `receipt_prefix` varchar(10) NOT NULL DEFAULT 'EF-',
  `updated_by` bigint unsigned DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_business_settings_users` (`updated_by`),
  CONSTRAINT `fk_business_settings_users` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_business_settings_single` CHECK ((`id` = 1)),
  CONSTRAINT `chk_business_settings_logo` CHECK (((`logo` is null) or (length(`logo`) <= 512000)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Fila vacía: los datos reales se cargan desde la pantalla de configuración.
INSERT INTO `business_settings` (`id`) VALUES (1);

-- ---------------------------------------------------------------------------
-- 4. Consecutivo de comprobantes: una fila que se bloquea dentro de la transacción del pago.
--    Si el pago falla, el número no se consume: la numeración queda continua y sin huecos.
-- ---------------------------------------------------------------------------
CREATE TABLE `receipt_sequence` (
  `id` tinyint unsigned NOT NULL,
  `next_value` bigint unsigned NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_receipt_sequence_single` CHECK ((`id` = 1)),
  CONSTRAINT `chk_receipt_sequence_value` CHECK ((`next_value` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `receipt_sequence` (`id`, `next_value`) VALUES (1, 1);

-- ---------------------------------------------------------------------------
-- 5. Comprobantes internos (no son factura DIAN). Uno por pago.
--    snapshot guarda todo lo impreso para que una reimpresión sea idéntica.
-- ---------------------------------------------------------------------------
CREATE TABLE `payment_receipts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `payment_id` bigint unsigned NOT NULL,
  `sequence_number` bigint unsigned NOT NULL,
  `receipt_number` varchar(30) NOT NULL,
  `snapshot` json NOT NULL,
  `issued_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `issued_by` bigint unsigned DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_receipts_payment` (`payment_id`),
  UNIQUE KEY `uq_receipts_sequence` (`sequence_number`),
  UNIQUE KEY `uq_receipts_number` (`receipt_number`),
  KEY `fk_receipts_users` (`issued_by`),
  CONSTRAINT `fk_receipts_payments` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`)
      ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_receipts_users` FOREIGN KEY (`issued_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
