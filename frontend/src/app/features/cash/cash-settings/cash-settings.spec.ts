import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';

import { CalendarDay, CashSettings, GymCalendar } from '../../../shared/models/cash.model';
import { CashService } from '../cash.service';
import { CashSettingsPage } from './cash-settings';

const SETTINGS: CashSettings = {
  openingBase: 50000,
  startDate: '2026-11-10',
  startDateLocked: true,
  openDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
  closedOnHolidays: true,
  closingTime: '22:00:00',
};

const day = (date: string, extra: Partial<CalendarDay> = {}): CalendarDay => ({
  date,
  open: false,
  closedReason: 'Festivo',
  holiday: 'Independencia de Cartagena',
  defaultOpen: false,
  overridden: false,
  note: null,
  locked: false,
  ...extra,
});

const CALENDAR: GymCalendar = {
  year: 2026,
  openDays: SETTINGS.openDays,
  closedOnHolidays: true,
  days: [
    day('2026-11-02', { holiday: 'Todos los Santos', locked: true }),
    day('2026-11-16'),
    day('2026-12-08', { holiday: 'Inmaculada Concepción', open: true, overridden: true, note: 'Horario especial' }),
  ],
};

function setup() {
  const api = {
    settings: vi.fn(() => of(SETTINGS)),
    updateSettings: vi.fn(() => of(SETTINGS)),
    calendar: vi.fn(() => of(CALENDAR)),
    setDay: vi.fn(() => of(day('2026-11-16', { open: true }))),
    clearDay: vi.fn(() => of(day('2026-12-08'))),
  };
  TestBed.configureTestingModule({
    providers: [
      { provide: CashService, useValue: api },
      { provide: MatSnackBar, useValue: { open: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(CashSettingsPage);
  fixture.detectChanges();
  return { c: fixture.componentInstance, api, el: fixture.nativeElement as HTMLElement };
}

describe('CashSettingsPage', () => {
  it('muestra lunes a sábado, festivos cerrados y la fecha de inicio bloqueada', () => {
    const { c } = setup();
    expect([...c.openDays()]).not.toContain('SUNDAY');
    expect(c.openDays().size).toBe(6);
    expect(c.form.controls.closedOnHolidays.value).toBe(true);
    expect(c.form.controls.startDate.disabled).toBe(true);
  });

  it('lista los festivos; un día ya cerrado en caja no se puede cambiar', () => {
    const { el } = setup();
    expect(el.textContent).toContain('Independencia de Cartagena');
    expect(el.textContent).toContain('Ya cerrado en caja');
    expect(el.querySelector('[aria-label="Abrir 2026-11-02"]')).toBeNull();
    expect(el.querySelector('[aria-label="Abrir 2026-11-16"]')).not.toBeNull();
    expect(el.textContent).toContain('excepción del administrador: Horario especial');
  });

  it('el ADMIN abre un festivo, quita una excepción o cierra una fecha concreta', () => {
    const { c, api } = setup();
    c.toggle(CALENDAR.days[1]);
    expect(api.setDay).toHaveBeenCalledWith('2026-11-16', true, null);

    c.clear(CALENDAR.days[2]);
    expect(api.clearDay).toHaveBeenCalledWith('2026-12-08');

    c.exception.setValue({ date: '2026-12-15', open: false, note: ' Mantenimiento ' });
    c.saveException();
    expect(api.setDay).toHaveBeenCalledWith('2026-12-15', false, 'Mantenimiento');
    expect(api.calendar).toHaveBeenCalledTimes(4);
  });

  it('guarda la configuración con los festivos cerrados', () => {
    const { c, api } = setup();
    c.save();
    expect(api.updateSettings).toHaveBeenCalledWith({
      openingBase: 50000,
      startDate: '2026-11-10',
      openDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
      closedOnHolidays: true,
    });
  });
});
