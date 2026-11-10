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

## 8. Decisiones confirmadas en la fase 4 (membresías y congelaciones)

1. **Pendiente de pago:** una membresía vendida queda `PENDING` hasta que se registre el pago total (fase 5). Hasta entonces, solo ADMIN crea membresías activas, y solo como migradas.
2. **"Siguiente":** máximo una por cliente, contando juntas las pendientes de pago (`PENDING`) y las programadas (`SCHEDULED`).
3. **Congelación cancelada:**
   - Si se cancela antes de empezar, no cuenta como la única de la membresía y no suma días.
   - Si se cancela después de empezar, sí cuenta y solo se suman los días ya transcurridos.
4. **Días de congelación:** días calendario seguidos.
5. **Anular la vigente con una siguiente registrada:** la siguiente pasa a empezar hoy.
6. **Cambiar de plan:** se anula la membresía actual y se crea otra. Solo ADMIN, con motivo.
7. **Membresías migradas del sistema anterior:**
   - Solo ADMIN y solo vigentes (vencimiento ≥ hoy).
   - Pueden empezar en el pasado y conservan el vencimiento real.
   - No exigen pago.
   - Precio editable; por defecto, el del plan.
   - Nota de migración obligatoria.
   - Una congelación permitida, salvo que el ADMIN marque que ya la usó en el sistema anterior.
8. **Registro de cambios:** anulaciones, cancelaciones, ampliaciones y congelaciones quedan en `audit_logs`.
9. **Estados y listados:** se calculan con fechas. La columna `status` es solo una copia que se actualiza a diario.

### Interpretaciones de la fase 4 (a confirmar con el dueño)

- **Cancelación el mismo día de inicio:** la congelación ya empezó, así que cuenta como la única, pero suma 0 días porque el cliente puede entrar ese día.
- **Solo se congela la membresía vigente:** no una programada ni una pendiente de pago. La congelación no puede empezar en el pasado ni después del vencimiento.
- **Renovación antes de vencer:** si el cliente tiene una vigente, la nueva empieza obligatoriamente el día siguiente al vencimiento; no se acepta otra fecha.
- **Clientes inactivos:** no se les puede registrar una membresía.
- **Planes inactivos:** no se venden, pero sí se pueden usar al migrar.
- **Último día:** el día de vencimiento todavía es válido; al día siguiente la membresía está vencida.

---

## Pendientes de definir

- Prefijo del consecutivo de comprobantes (sección 6.2).
