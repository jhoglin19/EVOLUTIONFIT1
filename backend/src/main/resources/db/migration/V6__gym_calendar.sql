-- Días de atención (docs/REGLAS_NEGOCIO.md, sección 4): el gimnasio abre de lunes a sábado; domingos y
-- festivos de Colombia está cerrado. Los festivos se calculan en el código (ColombianHolidays).
-- No borra filas ni cambia cierres guardados: cash_closings.was_open_day de días ya cerrados queda igual.

ALTER TABLE `cash_settings`
  ADD COLUMN `closed_on_holidays` tinyint(1) NOT NULL DEFAULT 1 AFTER `open_days`;

-- Respuesta del dueño: de lunes a sábado.
UPDATE `cash_settings` SET `open_days` = 'MON,TUE,WED,THU,FRI,SAT' WHERE `id` = 1;

-- Excepciones del ADMIN para una fecha concreta: abrir un festivo o domingo, o cerrar un día normal.
CREATE TABLE `gym_calendar_overrides` (
  `day` date NOT NULL,
  `is_open` tinyint(1) NOT NULL,
  `note` varchar(255) DEFAULT NULL,
  `updated_by` bigint unsigned DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`day`),
  KEY `fk_calendar_overrides_users` (`updated_by`),
  CONSTRAINT `fk_calendar_overrides_users` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
      ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
