/**
 * Prefijo de la API. En desarrollo, `ng serve` redirige /api al backend (ver proxy.conf.json),
 * y en producción un proxy inverso (nginx) hace lo mismo. Así el frontend nunca depende de la URL del backend.
 */
export const API_URL = '/api';
