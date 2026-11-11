// Orquestador de las pruebas de punta a punta (npm run e2e, desde e2e/).
//
// 1. Revisa que todo esté aislado: sin e2e/.env, puertos 3308, 8082 y 4300 libres, nada que apunte a la base
//    de desarrollo (3306) ni a la temporal (3307).
// 2. Genera contraseñas aleatorias en memoria (nunca se escriben ni se imprimen).
// 3. Levanta la MySQL de pruebas (localhost:3308, datos en memoria).
// 4. Compila y arranca el backend con el perfil e2e en el puerto 8082. El directorio de trabajo es e2e/.run/:
//    el backend busca .env en ./ y ../ (e2e/.run/ y e2e/), donde no existe, así que nunca lee el .env real.
//    El entorno del proceso se arma desde cero: no hereda DB_URL ni contraseñas de la terminal.
// 5. Sirve el frontend en el puerto 4300 con un proxy hacia el 8082.
// 6. Carga los datos iniciales por la API real y ejecuta Playwright: primero los flujos, luego la caja (que
//    adelanta el reloj).
// 7. Siempre, aunque algo falle: apaga todo, borra la base de pruebas y comprueba que los puertos quedan libres.
//
// Opciones: --headed (navegador visible); E2E_SIMULATE_NEXT_DAY=1 adelanta el reloj al día siguiente a las
// 8:00 a.m. antes de cargar datos (lo mismo que pasa si se ejecuta después de las 8:00 p.m.).
import { spawn, spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, openSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import net from 'node:net';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { seed } from './seed.mjs';

const E2E = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ROOT = resolve(E2E, '..');
const RUN = join(E2E, '.run');
const PORTS = { db: 3308, backend: 8082, frontend: 4300 };
const DB_URL =
  `jdbc:mysql://localhost:${PORTS.db}/evolution_fit_e2e?useSSL=false&allowPublicKeyRetrieval=true` +
  '&serverTimezone=America/Bogota&useUnicode=true&characterEncoding=UTF-8';
const COMPOSE = ['compose', '-f', join(E2E, 'docker-compose.e2e.yml'), '--project-directory', E2E];

const headed = process.argv.includes('--headed');
const children = [];
let secrets;

const log = (msg) => console.log(`[e2e] ${msg}`);
const secret = (bytes) => randomBytes(bytes).toString('hex');

function fail(msg) {
  throw new Error(msg);
}

function portInUse(port) {
  return new Promise((done) => {
    const socket = net.connect({ port, host: '127.0.0.1' });
    socket.once('connect', () => (socket.destroy(), done(true)));
    socket.once('error', () => done(false));
  });
}

async function preflight() {
  if (existsSync(join(E2E, '.env')) || existsSync(join(RUN, '.env'))) {
    fail('Existe e2e/.env o e2e/.run/.env: las pruebas no usan archivos .env. Bórralo y vuelve a intentar.');
  }
  if (!DB_URL.includes(':3308/evolution_fit_e2e') || /:330[67]\//.test(DB_URL)) {
    fail('La URL de la base de pruebas no es localhost:3308/evolution_fit_e2e.');
  }
  if (spawnSync('docker', ['info'], { stdio: 'ignore' }).status !== 0) {
    fail('Docker no está encendido.');
  }
  // Restos de una ejecución interrumpida: solo se toca el proyecto de pruebas (evolutionfit-e2e).
  spawnSync('docker', [...COMPOSE, 'down', '-v', '--remove-orphans'], { stdio: 'ignore', env: composeEnv('x', 'x') });
  for (const [name, port] of Object.entries(PORTS)) {
    if (await portInUse(port)) {
      fail(`El puerto ${port} (${name}) está ocupado. Libéralo antes de ejecutar las pruebas.`);
    }
  }
}

function composeEnv(rootPassword, password) {
  return { PATH: process.env.PATH, HOME: process.env.HOME, E2E_DB_ROOT_PASSWORD: rootPassword, E2E_DB_PASSWORD: password };
}

function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, { stdio: 'inherit', ...opts });
  if (r.status !== 0) {
    fail(`Falló: ${cmd} ${args.join(' ')}`);
  }
}

function start(name, cmd, args, opts) {
  const out = openSync(join(RUN, `${name}.log`), 'w');
  const child = spawn(cmd, args, { stdio: ['ignore', out, out], detached: true, ...opts });
  child.name = name;
  children.push(child);
  return child;
}

async function waitFor(url, label, child, timeoutMs) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    if (child && child.exitCode !== null) {
      fail(`${label} terminó antes de estar listo. Revisa e2e/.run/${child.name}.log`);
    }
    try {
      const res = await fetch(url);
      if (res.ok) {
        return;
      }
    } catch {
      // todavía no responde
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  fail(`${label} no respondió en ${timeoutMs / 1000} s (${url}).`);
}

function backendJar() {
  const target = join(ROOT, 'backend', 'target');
  const jar = readdirSync(target).find((f) => /^evolutionfit-backend-.*\.jar$/.test(f));
  return jar ? join(target, jar) : fail('No se encontró el jar del backend.');
}

function stopAll() {
  for (const child of children.reverse()) {
    if (child.exitCode === null) {
      try {
        process.kill(-child.pid, 'SIGTERM'); // todo el grupo (ng serve abre subprocesos)
      } catch {
        // ya terminó
      }
    }
  }
}

async function teardown() {
  stopAll();
  await new Promise((r) => setTimeout(r, 3000));
  for (const child of children) {
    if (child.exitCode === null) {
      try {
        process.kill(-child.pid, 'SIGKILL');
      } catch {
        // ya terminó
      }
    }
  }
  spawnSync('docker', [...COMPOSE, 'down', '-v', '--remove-orphans'], {
    stdio: 'ignore',
    env: composeEnv(secrets?.dbRoot ?? 'x', secrets?.db ?? 'x'),
  });
  const busy = [];
  for (const port of Object.values(PORTS)) {
    if (await portInUse(port)) {
      busy.push(port);
    }
  }
  const leftover = spawnSync('docker', ['ps', '-a', '--filter', 'name=evolutionfit-e2e', '--format', '{{.Names}}'], {
    encoding: 'utf8',
  }).stdout.trim();
  log(busy.length ? `ATENCIÓN: puertos aún ocupados: ${busy.join(', ')}` : 'Puertos 3308, 8082 y 4300 libres.');
  log(leftover ? `ATENCIÓN: contenedores que quedaron: ${leftover}` : 'Sin contenedores de pruebas.');
}

async function main() {
  await preflight();
  rmSync(RUN, { recursive: true, force: true });
  mkdirSync(RUN, { recursive: true });
  secrets = {
    dbRoot: secret(16),
    db: secret(16),
    jwt: secret(32),
    admin: 'A' + secret(12),
    reception: 'R' + secret(12),
  };

  log('MySQL de pruebas en localhost:3308 (datos en memoria)...');
  run('docker', [...COMPOSE, 'up', '-d', '--wait'], { env: composeEnv(secrets.dbRoot, secrets.db), stdio: 'ignore' });

  log('Compilando el backend...');
  run(join(ROOT, 'backend', 'mvnw'), ['-q', '-DskipTests', 'package'], { cwd: join(ROOT, 'backend') });

  log('Backend con el perfil e2e en el puerto 8082...');
  const backendEnv = {
    PATH: process.env.PATH,
    HOME: process.env.HOME,
    ...(process.env.JAVA_HOME ? { JAVA_HOME: process.env.JAVA_HOME } : {}),
    SPRING_PROFILES_ACTIVE: 'e2e',
    APP_E2E_ENABLED: 'true',
    DB_URL,
    DB_USERNAME: 'evolution_e2e',
    DB_PASSWORD: secrets.db,
    JWT_SECRET: secrets.jwt,
    SERVER_PORT: String(PORTS.backend),
    CORS_ALLOWED_ORIGINS: `http://localhost:${PORTS.frontend}`,
    BOOTSTRAP_ADMIN_USERNAME: 'e2e_admin',
    BOOTSTRAP_ADMIN_EMAIL: 'e2e.admin@example.test',
    BOOTSTRAP_ADMIN_PASSWORD: secrets.admin,
    TZ: 'America/Bogota',
  };
  const backend = start('backend', 'java', ['-jar', backendJar()], { cwd: RUN, env: backendEnv });
  await waitFor(`http://localhost:${PORTS.backend}/actuator/health`, 'El backend', backend, 180_000);

  log('Frontend en el puerto 4300...');
  const frontend = start(
    'frontend',
    'npx',
    ['ng', 'serve', '--port', String(PORTS.frontend), '--proxy-config', '../e2e/proxy.e2e.json', '--no-hmr'],
    { cwd: join(ROOT, 'frontend'), env: { PATH: process.env.PATH, HOME: process.env.HOME, NG_CLI_ANALYTICS: 'false' } },
  );
  await waitFor(`http://localhost:${PORTS.frontend}/`, 'El frontend', frontend, 240_000);

  log('Datos iniciales por la API...');
  const data = await seed({
    api: `http://localhost:${PORTS.frontend}/api`,
    admin: { username: 'e2e_admin', password: secrets.admin },
    reception: { username: 'e2e_recepcion', password: secrets.reception },
    nextDay: process.env.E2E_SIMULATE_NEXT_DAY === '1',
  });
  log(`Reloj de pruebas: ${data.clockNow} (día de pruebas ${data.today}${data.nextDay ? ', simulando el día siguiente' : ''}).`);

  const testEnv = {
    ...process.env,
    E2E_BASE_URL: `http://localhost:${PORTS.frontend}`,
    E2E_API: `http://localhost:${PORTS.frontend}/api`,
    E2E_ADMIN_USER: 'e2e_admin',
    E2E_ADMIN_PASSWORD: secrets.admin,
    E2E_RECEPTION_USER: 'e2e_recepcion',
    E2E_RECEPTION_PASSWORD: secrets.reception,
    E2E_TODAY: data.today,
  };
  const extra = headed ? ['--headed'] : [];
  const flows = spawnSync('npx', ['playwright', 'test', '--project=flujos', ...extra], {
    cwd: E2E,
    env: { ...testEnv, E2E_PHASE: 'flujos' },
    stdio: 'inherit',
  });
  const cash = spawnSync('npx', ['playwright', 'test', '--project=caja', ...extra], {
    cwd: E2E,
    env: { ...testEnv, E2E_PHASE: 'caja' },
    stdio: 'inherit',
  });
  return flows.status === 0 && cash.status === 0;
}

let ok = false;
process.on('SIGINT', async () => {
  await teardown();
  process.exit(130);
});
try {
  ok = await main();
} catch (e) {
  console.error(`[e2e] ${e.message}`);
  const log = join(RUN, 'backend.log');
  if (existsSync(log)) {
    console.error('[e2e] Últimas líneas del backend:\n' + readFileSync(log, 'utf8').split('\n').slice(-25).join('\n'));
  }
} finally {
  await teardown();
}
log(ok ? 'TODAS LAS PRUEBAS PASARON.' : 'HUBO FALLOS. Reporte: e2e/playwright-report/ (npx playwright show-report)');
process.exit(ok ? 0 : 1);
