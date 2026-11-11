import { ExpirationAlert } from '../models/dashboard.model';
import { Payment } from '../models/payment.model';

/** Indicativo de Colombia: se agrega cuando el número no trae uno. */
const COLOMBIA = '57';

/**
 * Número para wa.me: solo dígitos y con indicativo de país.
 * "+57 300 123 4567" o "573001234567" se dejan como están; "300 123 4567" pasa a "573001234567";
 * "+1 305 555 0100" o "00 1 305..." conservan su indicativo. Devuelve null si no hay un número usable.
 */
export function whatsappNumber(phone: string | null | undefined): string | null {
  const raw = (phone ?? '').trim();
  let digits = raw.replace(/\D/g, '');
  if (digits.length < 7) {
    return null;
  }
  if (raw.startsWith('+')) {
    return digits;
  }
  if (digits.startsWith('00')) {
    digits = digits.slice(2);
    return digits.length >= 7 ? digits : null;
  }
  if (digits.startsWith(COLOMBIA) && digits.length === 12) {
    return digits;
  }
  return COLOMBIA + digits;
}

const money = (value: number) =>
  '$ ' + new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 }).format(value);

const date = (iso: string) => iso.slice(0, 10).split('-').reverse().join('/');

/**
 * Mensaje precargado. El PDF lo adjunta recepción a mano: no hay envío automático.
 * Si el gimnasio aún no tiene nombre comercial configurado, se omite "de parte de".
 */
export function receiptMessage(p: Payment, gymName?: string | null): string {
  const from = gymName?.trim() ? `, de parte de ${gymName.trim()},` : ',';
  return (
    `Hola ${p.clientName}${from} te compartimos el comprobante ${p.receiptNumber} de tu pago del plan ` +
    `${p.planName} por ${money(p.amount)}. Vigencia: del ${date(p.membershipStartDate)} al ` +
    `${date(p.membershipEndDate)}. A continuación te enviamos el PDF. ¡Gracias!`
  );
}

/** Enlace wa.me con el mensaje, o null si el cliente no tiene teléfono o el pago no tiene comprobante. */
export function receiptWhatsappUrl(p: Payment, gymName?: string | null): string | null {
  const number = whatsappNumber(p.clientPhone);
  if (!number || !p.receiptNumber) {
    return null;
  }
  return `https://wa.me/${number}?text=${encodeURIComponent(receiptMessage(p, gymName))}`;
}

/**
 * Recordatorio de vencimiento: "Hola {cliente}, de parte de {gimnasio}, tu plan {plan} vence el {fecha}. Si
 * quieres renovarlo, te esperamos." Si ya venció, "venció el {fecha}". Sin nombre comercial configurado se
 * omite "de parte de". Lo envía recepción a mano: no hay envío automático.
 */
export function expirationMessage(a: ExpirationAlert, gymName?: string | null): string {
  const from = gymName?.trim() ? `, de parte de ${gymName.trim()},` : ',';
  const verb = a.daysToEnd < 0 ? 'venció' : 'vence';
  return (
    `Hola ${a.clientName}${from} tu plan ${a.planName} ${verb} el ${date(a.endDate)}. ` +
    'Si quieres renovarlo, te esperamos.'
  );
}

/** Enlace wa.me del recordatorio, o null si el cliente no tiene un teléfono usable (igual que el comprobante). */
export function expirationWhatsappUrl(a: ExpirationAlert, gymName?: string | null): string | null {
  const number = whatsappNumber(a.clientPhone);
  if (!number) {
    return null;
  }
  return `https://wa.me/${number}?text=${encodeURIComponent(expirationMessage(a, gymName))}`;
}
