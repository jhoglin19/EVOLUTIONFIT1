-- V8: fecha del último cambio de contraseña. Un token emitido antes de esa fecha deja de servir
-- (JwtAuthenticationFilter), así que cambiar o restablecer la contraseña cierra todas las sesiones abiertas.
-- NULL = nunca se ha cambiado desde V8: los tokens vigentes de esos usuarios siguen sirviendo hasta que expiren.
ALTER TABLE `users`
  ADD COLUMN `password_changed_at` datetime(6) DEFAULT NULL AFTER `password_hash`;
