/**
 * Fechas de calendario como texto ISO (yyyy-MM-dd), igual que las envía el backend.
 * Se calcula en UTC para que el cambio de hora o la zona del navegador no muevan el día.
 */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** Hoy en la zona del gimnasio (Bogotá), sin depender de la zona del equipo. */
export function todayInGym(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

/** Vencimiento de un plan: inicio + duración − 1 (el primer día cuenta). */
export function planEndDate(start: string, durationDays: number): string {
  return addDays(start, durationDays - 1);
}
