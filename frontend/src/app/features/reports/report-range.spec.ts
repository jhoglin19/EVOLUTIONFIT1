import { presetRange } from './report-range';

describe('presetRange', () => {
  // Martes 10 de noviembre de 2026.
  const TODAY = '2026-11-10';

  it('hoy, esta semana (desde el lunes) y este mes', () => {
    expect(presetRange('today', TODAY)).toEqual({ from: TODAY, to: TODAY });
    expect(presetRange('week', TODAY)).toEqual({ from: '2026-11-09', to: TODAY });
    expect(presetRange('week', '2026-11-15')).toEqual({ from: '2026-11-09', to: '2026-11-15' }); // domingo
    expect(presetRange('month', TODAY)).toEqual({ from: '2026-11-01', to: TODAY });
  });

  it('mes anterior completo, también en enero', () => {
    expect(presetRange('lastMonth', TODAY)).toEqual({ from: '2026-10-01', to: '2026-10-31' });
    expect(presetRange('lastMonth', '2027-01-05')).toEqual({ from: '2026-12-01', to: '2026-12-31' });
    expect(presetRange('lastMonth', '2028-03-01')).toEqual({ from: '2028-02-01', to: '2028-02-29' });
  });
});
