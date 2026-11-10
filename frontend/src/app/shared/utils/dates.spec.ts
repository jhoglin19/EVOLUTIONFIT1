import { addDays, planEndDate, todayInGym } from './dates';

describe('dates', () => {
  it('suma días cruzando mes y año', () => {
    expect(addDays('2026-12-07', 5)).toBe('2026-12-12');
    expect(addDays('2026-12-30', 3)).toBe('2027-01-02');
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
  });

  it('el vencimiento del plan incluye el primer día', () => {
    expect(planEndDate('2026-11-08', 30)).toBe('2026-12-07');
    expect(planEndDate('2026-11-09', 1)).toBe('2026-11-09');
  });

  it('hoy se calcula en la hora de Bogotá', () => {
    // 03:00 UTC del 10 = 22:00 del 9 en Bogotá.
    expect(todayInGym(new Date('2026-11-10T03:00:00Z'))).toBe('2026-11-09');
  });
});
