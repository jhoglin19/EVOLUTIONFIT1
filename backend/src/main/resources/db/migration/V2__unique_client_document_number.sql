-- El número de documento identifica por sí solo a un cliente (documento funcional, sección 7.2).
-- El control de acceso busca únicamente por número, así que este índice además agiliza esa consulta.
-- No modifica filas: solo reemplaza el índice único compuesto (tipo + número) por uno sobre el número.
ALTER TABLE `clients`
  DROP INDEX `uq_clients_document`,
  ADD UNIQUE KEY `uq_clients_document_number` (`document_number`);
