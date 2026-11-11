import { addDays, todayInGym } from '../../shared/utils/dates';

export interface Range {
  from: string;
  to: string;
}

export type Preset = 'today' | 'week' | 'month' | 'lastMonth';

export const PRESETS: { value: Preset; label: string }[] = [
  { value: 'today', label: 'Hoy' },
  { value: 'week', label: 'Esta semana' },
  { value: 'month', label: 'Este mes' },
  { value: 'lastMonth', label: 'Mes anterior' },
];

/** Atajos de fechas en hora de Bogotá. La semana va de lunes a domingo, como en el reporte. */
export function presetRange(preset: Preset, today: string = todayInGym()): Range {
  const [y, m, d] = today.split('-').map(Number);
  switch (preset) {
    case 'today':
      return { from: today, to: today };
    case 'week': {
      const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = domingo
      const monday = addDays(today, -((weekday + 6) % 7));
      return { from: monday, to: today };
    }
    case 'month':
      return { from: today.slice(0, 8) + '01', to: today };
    case 'lastMonth': {
      const firstOfMonth = today.slice(0, 8) + '01';
      const lastDay = addDays(firstOfMonth, -1);
      return { from: lastDay.slice(0, 8) + '01', to: lastDay };
    }
  }
}
