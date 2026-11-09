-- EvolutionFit - esquema base (V1)
-- Generado a partir del DDL de la base de datos evolution_fit existente.
-- En una BD que ya tiene estas tablas, Flyway la toma como línea base y NO ejecuta este archivo.
-- En una BD vacía (por ejemplo, Docker Compose la primera vez) crea todo el esquema.

CREATE TABLE `clients` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `document_type` varchar(20) NOT NULL,
  `document_number` varchar(30) NOT NULL,
  `first_name` varchar(80) NOT NULL,
  `last_name` varchar(80) NOT NULL,
  `email` varchar(150) DEFAULT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `address` varchar(200) DEFAULT NULL,
  `photo_url` varchar(500) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `notes` text,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_clients_document` (`document_type`,`document_number`),
  KEY `idx_clients_name` (`last_name`,`first_name`),
  KEY `idx_clients_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plans` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `duration_days` int unsigned NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_plans_name` (`name`),
  KEY `idx_plans_active` (`is_active`),
  CONSTRAINT `chk_plans_duration` CHECK ((`duration_days` > 0)),
  CONSTRAINT `chk_plans_price` CHECK ((`price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(40) NOT NULL,
  `description` varchar(150) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `role_id` bigint unsigned NOT NULL,
  `username` varchar(60) NOT NULL,
  `email` varchar(150) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `first_name` varchar(80) NOT NULL,
  `last_name` varchar(80) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `last_login_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `idx_users_role` (`role_id`),
  CONSTRAINT `fk_users_roles` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `entity_type` varchar(80) NOT NULL,
  `entity_id` bigint unsigned DEFAULT NULL,
  `action` varchar(40) NOT NULL,
  `before_data` json DEFAULT NULL,
  `after_data` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_audit_entity` (`entity_type`,`entity_id`),
  KEY `idx_audit_date` (`created_at`),
  KEY `idx_audit_user` (`user_id`),
  CONSTRAINT `fk_audit_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cash_closings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `total_income` decimal(12,2) NOT NULL DEFAULT '0.00',
  `total_expenses` decimal(12,2) NOT NULL DEFAULT '0.00',
  `total_adjustments` decimal(12,2) NOT NULL DEFAULT '0.00',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00',
  `notes` text,
  `closed_by` bigint unsigned DEFAULT NULL,
  `closed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_closings_period` (`period_start`,`period_end`),
  KEY `fk_closings_users` (`closed_by`),
  KEY `idx_closings_date` (`period_start`,`period_end`),
  CONSTRAINT `fk_closings_users` FOREIGN KEY (`closed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_closings_dates` CHECK ((`period_end` >= `period_start`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `memberships` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `client_id` bigint unsigned NOT NULL,
  `plan_id` bigint unsigned NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `price_at_purchase` decimal(12,2) NOT NULL,
  `notes` text,
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_memberships_plans` (`plan_id`),
  KEY `fk_memberships_users` (`created_by`),
  KEY `idx_memberships_client` (`client_id`),
  KEY `idx_memberships_dates` (`start_date`,`end_date`),
  KEY `idx_memberships_status` (`status`),
  CONSTRAINT `fk_memberships_clients` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_memberships_plans` FOREIGN KEY (`plan_id`) REFERENCES `plans` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_memberships_users` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_memberships_dates` CHECK ((`end_date` >= `start_date`)),
  CONSTRAINT `chk_memberships_price` CHECK ((`price_at_purchase` >= 0)),
  CONSTRAINT `chk_memberships_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'EXPIRED',_utf8mb4'FROZEN',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `membership_id` bigint unsigned NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `payment_method` varchar(30) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'COMPLETED',
  `reference` varchar(120) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `paid_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `received_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_payments_users` (`received_by`),
  KEY `idx_payments_membership` (`membership_id`),
  KEY `idx_payments_date` (`paid_at`),
  KEY `idx_payments_status` (`status`),
  CONSTRAINT `fk_payments_memberships` FOREIGN KEY (`membership_id`) REFERENCES `memberships` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_payments_users` FOREIGN KEY (`received_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_payments_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_payments_method` CHECK ((`payment_method` in (_utf8mb4'CASH',_utf8mb4'BANK_TRANSFER',_utf8mb4'NEQUI',_utf8mb4'DAVIPLATA',_utf8mb4'CARD',_utf8mb4'OTHER'))),
  CONSTRAINT `chk_payments_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'COMPLETED',_utf8mb4'VOIDED',_utf8mb4'REFUNDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `access_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `client_id` bigint unsigned DEFAULT NULL,
  `membership_id` bigint unsigned DEFAULT NULL,
  `document_number` varchar(30) NOT NULL,
  `decision` varchar(20) NOT NULL,
  `reason` varchar(150) DEFAULT NULL,
  `access_source` varchar(30) NOT NULL DEFAULT 'RECEPTION',
  `recorded_by` bigint unsigned DEFAULT NULL,
  `attempted_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_access_memberships` (`membership_id`),
  KEY `fk_access_users` (`recorded_by`),
  KEY `idx_access_document` (`document_number`),
  KEY `idx_access_attempted` (`attempted_at`),
  KEY `idx_access_client_date` (`client_id`,`attempted_at`),
  CONSTRAINT `fk_access_clients` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_access_memberships` FOREIGN KEY (`membership_id`) REFERENCES `memberships` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_access_users` FOREIGN KEY (`recorded_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_access_decision` CHECK ((`decision` in (_utf8mb4'ALLOWED',_utf8mb4'DENIED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cash_movements` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `payment_id` bigint unsigned DEFAULT NULL,
  `cash_closing_id` bigint unsigned DEFAULT NULL,
  `user_id` bigint unsigned DEFAULT NULL,
  `movement_type` varchar(20) NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `description` varchar(255) NOT NULL,
  `occurred_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `payment_id` (`payment_id`),
  KEY `fk_movements_users` (`user_id`),
  KEY `idx_movements_date` (`occurred_at`),
  KEY `idx_movements_closing` (`cash_closing_id`),
  KEY `idx_movements_type` (`movement_type`),
  CONSTRAINT `fk_movements_closings` FOREIGN KEY (`cash_closing_id`) REFERENCES `cash_closings` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_movements_payments` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_movements_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_movements_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_movements_type` CHECK ((`movement_type` in (_utf8mb4'INCOME',_utf8mb4'EXPENSE',_utf8mb4'ADJUSTMENT_IN',_utf8mb4'ADJUSTMENT_OUT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `membership_freezes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `membership_id` bigint unsigned NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `applied_days` int unsigned NOT NULL DEFAULT '0',
  `reason` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_freezes_users` (`created_by`),
  KEY `idx_freezes_membership` (`membership_id`),
  KEY `idx_freezes_status_dates` (`status`,`start_date`,`end_date`),
  CONSTRAINT `fk_freezes_memberships` FOREIGN KEY (`membership_id`) REFERENCES `memberships` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_freezes_users` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_freezes_dates` CHECK (((`end_date` is null) or (`end_date` >= `start_date`))),
  CONSTRAINT `chk_freezes_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'ACTIVE',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
