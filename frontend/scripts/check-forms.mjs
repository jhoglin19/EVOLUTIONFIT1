// Falla si algún <form> de la aplicación no tiene [formGroup] (o ngNoForm).
// La app usa solo ReactiveFormsModule: un <form> sin [formGroup] no tiene ninguna directiva que maneje el
// envío, así que (ngSubmit) nunca se ejecuta y el navegador recarga la página. Pasó en la búsqueda del
// asistente, en el diálogo de motivo (anulaciones) y en el arqueo, y las pruebas unitarias no lo vieron.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = new URL('../src/', import.meta.url).pathname;
const problems = [];

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      walk(path);
    } else if ((name.endsWith('.html') || name.endsWith('.ts')) && !name.endsWith('.spec.ts')) {
      check(path);
    }
  }
}

function check(path) {
  const text = readFileSync(path, 'utf8');
  for (const match of text.matchAll(/<form\b[^>]*>/g)) {
    const tag = match[0];
    if (!/\[formGroup\]|ngNoForm/.test(tag)) {
      const line = text.slice(0, match.index).split('\n').length;
      problems.push(`${relative(root, path)}:${line}  ${tag}`);
    }
  }
}

walk(root);
if (problems.length) {
  console.error('Formularios sin [formGroup] (el envío recargaría la página):\n  ' + problems.join('\n  '));
  process.exit(1);
}
console.log('check-forms: todos los <form> tienen [formGroup].');
