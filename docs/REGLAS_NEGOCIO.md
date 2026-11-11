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
9. **Los días del plan son días calendario.** Si una membresía vence un domingo o un festivo, vence igual ese día. Los días en que el gimnasio no abre (domingos, festivos o días marcados como cerrados en la caja) no extienden la membresía.

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
- **Caja:** resuelto en la fase 6 (sección 10). El efectivo se calcula a partir de los pagos; los pagos no crean movimientos de caja.

---

## 10. Decisiones confirmadas en la fase 6 (caja)

1. **La caja arranca cuando el ADMIN fija la fecha de inicio** en "Configuración de caja".
   - Sin fecha, no hay cierres ni retiros.
   - Cuenta desde las 10 p.m. del día anterior a esa fecha; lo anterior queda fuera de la caja.
   - La fecha no puede estar en el pasado y se bloquea después del primer cierre.
2. **Cierre automático** a las 10 p.m. de Bogotá (`app.cash.closing-time`):
   - Corre al arrancar el backend y cada minuto. Si el backend estuvo apagado, cierra en orden los días que faltan, uno por uno, sin duplicar ni saltar días.
   - Cada cierre guarda su periodo (desde el corte anterior hasta las 10 p.m.) y la base usada. Si después cambia la base, los días cerrados no se recalculan.
   - Se crea un cierre para todos los días, también sin movimientos o con el gimnasio cerrado.
3. **Efectivo esperado** = base + pagos en efectivo recibidos ese día (aunque luego se anulen) − anulaciones en efectivo hechas ese día − retiros.
   - Un pago cobrado y anulado el mismo día da neto $0.
   - Anular un pago anterior al inicio de la caja no descuenta efectivo.
   - Los ingresos se leen de `payments`; `cash_movements` guarda solo los retiros. Cada pago, anulación y retiro queda marcado con el cierre que lo contó, así ninguno se cuenta dos veces.
4. **Retiros:**
   - Recepción y ADMIN los registran con monto, categoría (insumos, aseo, otros) y concepto obligatorio.
   - No pueden dejar el efectivo esperado del día en negativo.
   - **Solo el ADMIN los anula**, con motivo y solo antes del corte del día del retiro. El retiro anulado no se borra.
5. **Arqueo:**
   - **No es obligatorio en días sin movimientos de efectivo** (quedan "Sin movimientos"). El ADMIN puede registrarlo igual.
   - El arqueo compara lo contado con el **esperado ajustado**: esperado congelado + efectivo que entró después del corte − efectivo que salió después del corte. Así un cobro a las 10:10 p.m., que cuenta para el día siguiente pero ya está en la caja, no da una falsa diferencia.
   - El esperado congelado no cambia; el ajuste y la lista de esos movimientos se guardan aparte en el arqueo.
   - Si entra o sale efectivo mientras el diálogo está abierto, el sistema no registra el arqueo y pide revisarlo de nuevo.
   - Con diferencia, la nota es obligatoria.
6. **Revisión:** solo los arqueos con diferencia quedan por revisar. El ADMIN los marca como revisados con nota obligatoria. Los que cuadran quedan listos.
7. **Reapertura:** el ADMIN puede reabrir un arqueo (por ejemplo, un valor mal digitado) con motivo. El esperado del cierre no cambia.
8. **Totales del informe por método** (caja del día, detalle del cierre y reporte mensual):
   - Total efectivo, total otros métodos (transferencia, Nequi, Daviplata, tarjeta y otro) y total general recaudado, cada uno con lo recibido, lo anulado y el neto.
   - El **total general recaudado** es lo cobrado en el periodo con todos los métodos, neto de anulaciones; no descuenta retiros ni incluye la base.
   - El **efectivo esperado** es lo que debe haber en el cajón: base + efectivo − anulaciones en efectivo − retiros.
   - En cierres y reportes se leen de `cash_closing_totals`; no cambian ningún cierre guardado.
9. **Permisos:** recepción ve el día en curso, registra retiros y sus arqueos pendientes. El ADMIN además ve el historial, todos los retiros, el detalle de cada cierre, el reporte mensual y la configuración.
10. **Auditoría (`audit_logs`):** cierres (hechos por el sistema), retiros y sus anulaciones, arqueos, revisiones, reaperturas y cambios de configuración.

### Interpretaciones de la fase 6 (a confirmar con el dueño)

- **Base fija (por confirmar):** $50.000 todos los días. El ADMIN la cambia en "Configuración de caja" y aplica a los días que aún no se cierran.
- **Efectivo sobrante (por confirmar):** después del arqueo, el dueño retira todo lo que pase de la base, fuera del sistema. El día siguiente arranca otra vez con la base y la diferencia no se arrastra.
- **Días que abre (por confirmar):** todos los días. Configurable; un día marcado como cerrado igual tiene cierre.
- **Arqueo atrasado:** el ajuste incluye todo lo que entró o salió desde el corte hasta el momento de contar. Si se cuenta varios días tarde, el ajuste cubre esos días. Lo recomendable es contar antes de abrir el día siguiente.
- **Anulación de un pago de un día ya cerrado:** se descuenta hoy, aunque el efectivo esperado quede negativo (por ejemplo, la devolución de una anualidad pagada ayer). Mientras quede negativo no se pueden registrar retiros.
- **Pago guardado justo en el corte:** si un pago empieza a las 9:59 p.m. y termina de guardarse después del cierre, cuenta para el día siguiente; nunca se pierde ni se cuenta dos veces.

### Pendiente de la puesta en marcha (no implementado)

- **Procedimiento seguro para borrar los datos de prueba antes de operar con datos reales:**
  - Qué se borra: clientes, membresías, congelaciones, pagos, comprobantes, retiros, cierres y totales de caja. También se reinician los consecutivos (comprobantes) y la fecha de inicio de la caja.
  - Debe hacerse con respaldo previo, sobre una base sin uso real, con confirmación explícita y dejando constancia.
  - Hasta tenerlo, no se borran datos a mano.

---

## Pendientes de definir

- El prefijo del consecutivo ya se definió en la fase 5: `EF-`, editable.
- **Caja (por confirmar con el dueño; hoy rigen los valores por defecto de la sección 10):**
  - ¿La base de $50.000 es fija todos los días?
  - ¿Qué se hace con el efectivo que sobra después del arqueo?
  - ¿El gimnasio abre todos los días?
- **Puesta en marcha:** procedimiento seguro para borrar los datos de prueba (sección 10).
