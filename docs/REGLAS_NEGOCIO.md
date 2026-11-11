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

Respuestas del dueño para la fase 6. Reemplazan lo anterior ("sin egresos por ahora" y "recepción ve su turno").

1. **Un solo recepcionista y una sola caja.** Hay **un solo cierre por día**.
2. **Base de $50.000 cada día.** La base está incluida en el efectivo esperado.
3. **Solo se cuadra el efectivo.** Transferencias, Nequi, Daviplata y tarjeta aparecen en el cierre solo como informe del día.
4. **Retiros de efectivo** para gastos del gimnasio (servilletas y cosas varias):
   - Se registran con **monto** y **concepto obligatorio**, y una **categoría** (por ejemplo: insumos, aseo, otros).
   - Bajan el efectivo esperado.
   - Recepción los registra; el ADMIN los ve todos.
5. **Cierre automático a las 10 p.m., hora de Bogotá.** El sistema congela el efectivo esperado del día:
   **base + pagos en efectivo completados − retiros − pagos en efectivo anulados ese día**.
6. **Arqueo:** después del corte, recepción cuenta el efectivo y registra el valor contado y una nota. La diferencia se calcula sola. Se puede registrar con diferencia; el ADMIN la revisa.
7. **Lo que pasa después del corte cuenta para el día siguiente** (pagos y retiros).
8. **Un pago anulado se descuenta el día en que se anula**, no el día en que se pagó. **Los días ya cerrados nunca cambian.**
9. **Reporte mensual:** es solo un informe. Suma los cierres diarios del mes, con totales por método de pago.
10. **Permisos:** recepción solo ve el día en curso y su arqueo pendiente. El ADMIN ve y revisa todo.

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
   - **Si la membresía anulada tiene una congelación programada o en curso,** esa congelación se cancela automáticamente con el motivo "Membresía anulada: …". Sigue la misma regla que una cancelación manual: sin empezar suma 0 días; en curso, solo los ya transcurridos. Las congelaciones ya terminadas no se tocan.
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

## 9. Decisiones confirmadas en la fase 5 (pagos y comprobantes)

1. **Anular un pago anula también su membresía**, con el mismo motivo y las reglas de la fase 4:
   - Si era la vigente, la siguiente empieza hoy.
   - Su congelación abierta se cancela.
   - El pago y la membresía no se borran.
   - El comprobante conserva su número y se reimprime marcado como ANULADO.
2. **Descuento:**
   - Es un monto fijo en COP, lo aplica solo un ADMIN y siempre con motivo.
   - No puede superar el valor del plan.
   - Se permite un total de $0 solo con un descuento total del ADMIN o con un plan de precio 0.
3. **Cobro:**
   - Se cobra el precio fijado al vender la membresía (`price_at_purchase`), no el precio actual del plan.
   - El monto mostrado al usuario debe coincidir con el calculado por el backend.
4. **Fechas al pagar:**
   - Si el cliente tiene una vigente, la nueva empieza el día siguiente a su vencimiento (incluye los días congelados).
   - Si no tiene vigente, empieza en la fecha pedida al vender, siempre que todavía sea futura.
   - En cualquier otro caso empieza el día del pago. Si la fecha pedida ya pasó, el asistente lo avisa.
5. **No hay pagos con fecha atrasada:** el pago queda con la fecha y hora reales de su registro.
6. **Comprobante:**
   - Es un PDF interno, no una factura DIAN.
   - El consecutivo es único y continuo, de 6 dígitos y con prefijo por defecto `EF-`.
   - El número inicial es configurable (por defecto 1) y se bloquea después del primer comprobante.
   - Si cambia el prefijo, la numeración sigue.
   - **Formato:** A5 vertical con letra legible en el celular. El tamaño es configurable (`app.receipts.paper-size`); carta sigue disponible.
   - **Entrega por WhatsApp, a mano:** no hay impresora. Recepción descarga el PDF ("Descargar PDF") y lo adjunta.
     - El botón "Abrir WhatsApp" abre `wa.me` con el teléfono del cliente y un mensaje precargado: "Hola {cliente}, de parte de {nombre comercial}, …". Si aún no hay nombre comercial configurado, se omite "de parte de".
     - Recepción solo puede ver el nombre comercial del gimnasio; el resto de los datos del comprobante sigue siendo solo de ADMIN.
     - Si el número no trae indicativo, se agrega el +57.
     - Si el cliente no tiene teléfono, el botón no aparece.
     - No hay envío automático.
7. **Datos del encabezado:**
   - Nombre comercial, titular o razón social, NIT (texto libre con su dígito de verificación), matrícula mercantil, dirección, ciudad, teléfono, correo y logo.
   - Se guardan en la base y los edita el ADMIN desde "Datos del comprobante".
   - Ningún dato real del gimnasio va en el código, en las migraciones ni en las pruebas.
   - El logo es PNG o JPG de hasta 500 KB.
8. **Pagos anteriores al sistema:** el pago registrado antes de la fase 5 no lleva comprobante.
9. **Auditoría:** se registran en `audit_logs` el cobro, el descuento, la anulación, la activación de la membresía y los cambios de configuración.

### Interpretaciones de la fase 5 (a confirmar con el dueño)

- **Quién cobra:** recepción y ADMIN pueden cobrar e inscribir. Solo ADMIN aplica descuentos, anula pagos y edita los datos del comprobante.
- **Logo en reimpresiones:** el comprobante guarda una copia de todo lo impreso, salvo el logo. Una reimpresión usa el logo vigente en ese momento.
- **Caja:** los pagos todavía no generan movimientos de caja. La fase 6 decidirá cómo calcular el efectivo esperado a partir de los pagos.

---

## Pendientes de definir

- El prefijo del consecutivo ya se definió en la fase 5: `EF-`, editable.
- **Caja (por confirmar con el dueño):**
  - ¿La base de $50.000 es fija todos los días?
  - ¿Qué se hace con el efectivo que sobra después del arqueo?
  - ¿El gimnasio abre todos los días?
