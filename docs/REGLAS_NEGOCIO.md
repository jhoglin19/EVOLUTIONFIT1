# Reglas de negocio de EvolutionFit

Respuestas del dueño del gimnasio al cuestionario de reglas de negocio (sección 15 del documento técnico y funcional).

**Este documento es fuente obligatoria.** Prevalece sobre cualquier suposición y sobre el documento técnico cuando haya contradicción. Lo que no esté aquí ni en el documento técnico no se asume: se pregunta o se deja configurable y documentado.

Roles: `ADMIN` (administrador) y `RECEPTIONIST` (recepción).

---

## 1. Membresías

1. Un cliente tiene **una sola membresía activa** a la vez.
2. Si el cliente **renueva antes de vencer**, la nueva membresía queda **programada** y empieza el **día siguiente al vencimiento** de la actual.
3. Una **membresía nueva** cuenta desde el **día del pago**. Opcionalmente se puede indicar una **fecha de inicio futura**.
4. Una **renovación después de vencida** cuenta desde el **día del pago**.
5. **No hay días de gracia.**
6. **No hay cambio de plan a mitad de periodo:** se termina la membresía actual y se crea otra.
7. **Solo ADMIN anula** una membresía, con **motivo obligatorio**. La membresía anulada **no se borra**.
8. Cada membresía guarda el **nombre y el precio del plan** al momento de la venta (`price_at_purchase` y el nombre del plan); un cambio posterior del plan no altera membresías ya vendidas.

## 2. Congelaciones

1. **Motivo obligatorio.**
2. Días elegibles, **máximo 30**.
3. **Una congelación por membresía.**
4. Los **días congelados se suman al vencimiento** de la membresía.
5. Se puede **programar a futuro**.
6. **Recepción registra** congelaciones; **solo ADMIN las cancela o amplía**.
7. Un **cliente congelado no entra** al gimnasio.

## 3. Pagos

1. **Sin abonos:** no hay pagos parciales. El **pago total activa la membresía**.
2. Métodos de pago: `CASH`, `BANK_TRANSFER`, `NEQUI`, `DAVIPLATA`, `CARD`, `OTHER`.
3. **Referencia obligatoria** en `BANK_TRANSFER`, `NEQUI` y `DAVIPLATA`.
4. **Descuentos solo ADMIN**, con **motivo**.
5. **Anulación solo ADMIN**, con **motivo**. El pago anulado **no se borra**.
6. **Solo se cobran planes.** El día suelto es un **plan de 1 día**.
7. **Sin pasarela de pagos** (no hay pagos en línea).

## 4. Caja

1. **Una sola caja.**
2. **Cierre diario** y **reporte mensual**; el reporte mensual es la **suma de los cierres diarios**.
3. El cierre registra **efectivo esperado, efectivo contado, diferencia y nota**.
4. **Se puede cerrar con diferencia.**
5. **Sin egresos** por ahora.
6. **Recepción ve su turno; ADMIN ve todo.**

## 5. Control de acceso

1. El cliente **digita su número de documento** en un **teclado en la puerta**, conectado al computador del gimnasio.
2. Membresía **vencida o congelada: se deniega** el acceso.
3. Recepción puede abrir con un **botón autenticado "Abrir puerta"**, con **motivo obligatorio**.
4. **Sin horarios por plan.**
5. **Sin límite diario de entradas;** se **registra cada entrada**.
6. **Aviso de vencimiento 5 días antes.**
7. La integración física está descrita en [HARDWARE_CONTROL_ACCESO.md](HARDWARE_CONTROL_ACCESO.md) y corresponde a la **fase 8**. No se analiza ni se programa antes de esa fase.

## 6. Comprobantes y avisos

1. El comprobante es un **PDF interno**, **no es factura electrónica DIAN**.
2. **Consecutivo continuo.** El **prefijo está pendiente** de definir (debe quedar configurable).
3. Los **avisos son solo dentro del sistema** (sin correo, SMS ni WhatsApp).

## 7. Clientes

1. Los clientes **no se borran, se desactivan**.
2. Se **migrarán poco a poco** desde otro sistema, **sin importación masiva**.

---

## Pendientes de definir

- Prefijo del consecutivo de comprobantes (sección 6.2).
