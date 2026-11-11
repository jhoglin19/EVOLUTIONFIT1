// Datos iniciales de las pruebas, por la API real (nada de SQL a mano). El ADMIN lo crea el arranque del
// backend (BOOTSTRAP_ADMIN_*); aquí se crean recepción, los planes y la caja. Días: los del reloj de pruebas.

async function call(api, method, path, token, body) {
  const res = await fetch(api + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`Datos iniciales: ${method} ${path} respondió ${res.status}: ${text.slice(0, 300)}`);
  }
  return text ? JSON.parse(text) : null;
}

export async function login(api, user) {
  return (await call(api, 'POST', '/auth/login', null, { identifier: user.username, password: user.password })).token;
}

const addDays = (iso, days) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
};

export async function seed({ api, admin, reception, nextDay }) {
  const token = await login(api, admin);
  let clock = await call(api, 'GET', '/e2e/clock', token);
  if (nextDay) {
    clock = await call(api, 'POST', '/e2e/clock/advance', token, { to: `${addDays(clock.now.slice(0, 10), 1)}T08:00:00` });
  }
  const today = clock.now.slice(0, 10);

  await call(api, 'POST', '/users', token, {
    username: reception.username,
    email: 'e2e.recepcion@example.test',
    password: reception.password,
    firstName: 'Recepción',
    lastName: 'Pruebas',
    role: 'RECEPTIONIST',
  });
  for (const plan of [
    { name: 'Mensual E2E', description: 'Plan de pruebas', durationDays: 30, price: 90000 },
    { name: 'Diario E2E', description: 'Plan de pruebas', durationDays: 1, price: 10000 },
    { name: 'Trimestral E2E', description: 'Plan de pruebas', durationDays: 90, price: 240000 },
  ]) {
    await call(api, 'POST', '/plans', token, plan);
  }
  // La caja empieza el día de pruebas, y ese día se marca abierto aunque sea domingo o festivo.
  await call(api, 'PUT', '/cash/settings', token, {
    openingBase: 50000,
    startDate: today,
    openDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
    closedOnHolidays: true,
  });
  await call(api, 'PUT', `/cash/calendar/${today}`, token, { open: true, note: 'Pruebas e2e: día abierto' });
  await login(api, reception); // recepción puede entrar
  return { today, clockNow: clock.now, nextDay };
}
