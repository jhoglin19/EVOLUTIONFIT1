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
2. **Base fija de $50.000 todos los días** (confirmado por el dueño). La base está incluida en el efectivo esperado.
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
11. **Días de atención** (confirmado por el dueño): el gimnasio **abre de lunes a sábado**. **Domingos y festivos de Colombia está cerrado.**
    - El ADMIN puede marcar una fecha concreta como abierta o cerrada.
    - En caja, un día cerrado se muestra como "Gimnasio cerrado" y no exige arqueo. Si aun así tuvo movimientos en efectivo, se trata como un día normal.
    - Los cierres ya guardados no cambian.

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
2. **Consecutivo continuo.** La **numeración empieza en 1** (confirmado por el dueño). El prefijo es `EF-` y es configurable.
3. Los **avisos son solo dentro del sistema** (sin correo, SMS ni WhatsApp).

## 7. Clientes

1. Los clientes **no se borran, se desactivan**. Única excepción (decisión del dueño, después de la fase 7): el **ADMIN puede eliminar para siempre a un cliente que nunca tuvo membresías, pagos ni accesos**. Ver la sección 12.
2. Se **migrarán poco a poco** desde otro sistema, **sin importación masiva**.
3. **El celular es obligatorio al registrar un cliente**, también en el asistente de inscripción:
   - Es un celular colombiano de 10 dígitos que empieza por 3.
   - Se acepta con el indicativo +57 y con espacios, guiones o paréntesis, pero se guarda solo con los 10 dígitos (por ejemplo, `3001234567`), como ya lo hacía el sistema.
   - Los clientes registrados antes sin teléfono siguen funcionando (se pueden inscribir y cobrar); el celular se les exige cuando se editan sus datos.

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

11. **Calendario de atención** (sección 4, punto 11):
    - Los días que abre se configuran en `cash_settings.open_days` (V6: lunes a sábado).
    - Los festivos de Colombia se calculan por ley, sin cargarlos a mano:
      - Fijos: 1 de enero, 1 de mayo, 20 de julio, 7 de agosto, 8 y 25 de diciembre.
      - Trasladados al lunes siguiente (Ley Emiliani): 6 de enero, 19 de marzo, 29 de junio, 15 de agosto, 12 de octubre, 1 y 11 de noviembre, y desde 2026 el 9 de julio (Nuestra Señora del Rosario de Chiquinquirá, Ley 2578 de 2026).
      - Según la Semana Santa: Jueves y Viernes Santo, Ascensión, Corpus Christi y Sagrado Corazón.
      - Las pruebas verifican 2026 y 2027 contra el calendario oficial (19 festivos cada año).
    - Se puede desactivar el cierre en festivos (`closed_on_holidays`).
    - **Excepciones del ADMIN** (`gym_calendar_overrides`): abrir un festivo o domingo, o cerrar un día normal, con nota opcional. Prevalecen sobre la regla general. No se pueden poner ni quitar en un día que ya tiene cierre de caja.
    - Al cerrar la caja, el cierre guarda si ese día abría (`was_open_day`); cambios posteriores del calendario no lo tocan.
    - Quedan en `audit_logs` (`GYM_CALENDAR`).

### Interpretaciones de la fase 6 (a confirmar con el dueño)

- **Efectivo sobrante (por confirmar):** después del arqueo, el dueño retira todo lo que pase de la base, fuera del sistema. El día siguiente arranca otra vez con la base y la diferencia no se arrastra.
- **Arqueo atrasado:** el ajuste incluye todo lo que entró o salió desde el corte hasta el momento de contar. Si se cuenta varios días tarde, el ajuste cubre esos días. Lo recomendable es contar antes de abrir el día siguiente.
- **Anulación de un pago de un día ya cerrado:** se descuenta hoy, aunque el efectivo esperado quede negativo (por ejemplo, la devolución de una anualidad pagada ayer). Mientras quede negativo no se pueden registrar retiros.
- **Pago guardado justo en el corte:** si un pago empieza a las 9:59 p.m. y termina de guardarse después del cierre, cuenta para el día siguiente; nunca se pierde ni se cuenta dos veces.

### Pendiente de la puesta en marcha (no implementado)

- **Antes de la puesta en marcha se borran los datos de prueba y se reinicia el consecutivo** (decisión del dueño). La numeración de comprobantes vuelve a empezar en 1.
- **Procedimiento seguro para borrar los datos de prueba antes de operar con datos reales:**
  - Qué se borra: clientes, membresías, congelaciones, pagos, comprobantes, retiros, cierres y totales de caja. También se reinician el consecutivo de comprobantes (a 1) y la fecha de inicio de la caja.
  - Debe hacerse con respaldo previo, sobre una base sin uso real, con confirmación explícita y dejando constancia.
  - Hasta tenerlo, no se borran datos a mano.

---

## 11. Decisiones confirmadas en la fase 7 (panel de inicio, alertas de vencimiento y reportes)

1. **Fechas, no la columna `status`:** todo se calcula con `start_date`, `end_date`, las congelaciones y las fechas de pago y anulación, en **hora de Bogotá**. De `status` solo se usan los estados que fija un usuario (`PENDING` y `CANCELLED`).
2. **"Hoy" es el día calendario** (de 12 a.m. a 12 a.m.) en el panel y en los reportes. La tarjeta de caja del panel respeta el día de caja, con su corte de las 10 p.m.
3. **Panel de inicio:**
   - Recepción y ADMIN ven:
     - Clientes con membresía vigente (activas y congeladas).
     - Las que vencen en los próximos 5 días y las vencidas recientes.
     - Las inscripciones del día: nuevas, renovaciones, migradas y pendientes de pago.
     - La cantidad y la lista de los pagos del día, con el valor de cada pago **y sin sumas**.
     - El estado de la caja (arqueos pendientes).
   - Solo el ADMIN ve los ingresos de hoy y del mes, las anulaciones y descuentos del mes y las diferencias de arqueo por revisar. Los ingresos se piden en un endpoint aparte, solo para el ADMIN.
4. **Alertas de vencimiento**, solo dentro del sistema:
   - **Por vencer:** vigentes que vencen entre hoy y hoy + 5 días (`app.memberships.expiry-warning-days`) y que no tienen una renovación pagada. Es el mismo criterio de "Por vencer" en Membresías.
   - **Vencidas recientes:** vencieron entre hace 15 días y ayer (`app.memberships.recently-expired-days`, configurable) y el cliente no tiene otra membresía pagada o migrada que empiece después.
   - Una renovación **pendiente de pago** no quita la alerta: la acción pasa a ser "Cobrar renovación".
   - **Los clientes inactivos no aparecen** en las alertas.
   - **Sin seguimiento:** no se guarda "contactado". El cliente sale de la lista cuando paga la renovación.
   - **"Recordar por WhatsApp":** abre `wa.me` con el celular del cliente. Usa la misma normalización (+57) y el mismo criterio que el comprobante: sin un número usable, el botón no aparece.
     - Mensaje: "Hola {cliente}, de parte de {nombre comercial}, tu plan {plan} vence el {fecha}. Si quieres renovarlo, te esperamos."
     - Si ya venció, dice "venció el {fecha}". Sin nombre comercial se omite "de parte de".
     - Lo envía recepción a mano; no hay envío automático.
   - El menú muestra el número de alertas.
5. **Cliente nuevo frente a renovación** (panel y reportes, una sola definición):
   - Una membresía cuenta cuando está pagada o es migrada. Las pendientes y las anuladas no cuentan.
   - Empieza a contar al activarla el pago o, si es migrada o anterior a la fase 5, al registrarla.
   - Un cliente es **nuevo** solo si su primera membresía, de **cualquier tipo**, cae en el periodo.
   - Quien tiene una membresía **migrada**, en cualquier estado, cuenta como **migrado**. Su primera renovación pagada es una renovación, no un cliente nuevo.
   - Si el primer pago de un cliente se anuló, esa membresía anulada no cuenta y su siguiente pago es el primero.
6. **Reporte de ingresos: la misma regla de Caja.**
   - Lo recibido se cuenta por la fecha del pago, aunque después se anule.
   - Lo anulado se cuenta por la fecha de anulación. Neto = recibido − anulado.
   - Tiene las mismas columnas y los totales de efectivo, otros métodos y general.
   - Se agrupa por día, semana (lunes a domingo) o mes, recortado al rango.
   - Diferencias con Caja:
     - Los días son calendario. La pantalla aclara que un pago hecho entre las 10 p.m. y las 12 a.m. aparece en el reporte ese día y en Caja el día siguiente.
     - El reporte no depende de la fecha de inicio de la caja: incluye también los pagos y anulaciones anteriores a ella.
7. **Otros reportes del ADMIN** (rango de fechas de máximo 366 días, `app.reports.max-range-days`):
   - **Vencimientos:** membresías pagadas o migradas que vencen en el periodo. Cada una dice si renovó, si tiene la renovación pendiente de pago o si no renovó. Incluye la tasa de renovación de las ya vencidas y los clientes inactivos.
   - **Clientes nuevos y migrados:** según el punto 5.
   - **Planes más vendidos:** membresías pagadas en el periodo, sin anuladas ni migradas. Muestra cantidad, nuevas frente a renovaciones, ingreso y descuentos, con el nombre que tenía el plan al venderse.
   - **Pagos anulados:** por la fecha de anulación, con quién anuló y el motivo.
   - **Descuentos:** por la fecha del pago, con quién lo aplicó y el motivo. Los pagos que después se anularon aparecen marcados y no suman.
   - Los reportes de asistencia y entradas quedan para la fase 8.
8. **Exportación CSV:**
   - UTF-8 con BOM, separador `;`, coma decimal y fechas `aaaa-mm-dd`.
   - Los textos que empiezan por `=`, `+`, `-` o `@` llevan un apóstrofo, así Excel no los ejecuta como fórmula.
   - Cada exportación queda en `audit_logs` (`REPORT` / `EXPORT`).
9. **Permisos (`@PreAuthorize`):**
   - `/api/dashboard` y `/api/alerts/**`: recepción y ADMIN.
   - `/api/dashboard/admin` y `/api/reports/**`, incluida la exportación: solo ADMIN.
10. **Migración V7:** solo agrega índices para las consultas por fecha (`payments.voided_at`, `memberships.activated_at`, `memberships.created_at`). No cambia datos.

---

## 12. Ajustes posteriores a la fase 7

### Eliminar un cliente sin historial

1. **Solo el ADMIN**, desde la ficha del cliente ("Eliminar cliente"), con un diálogo de confirmación.
2. **Solo si nunca tuvo membresías, pagos ni accesos:**
   - Cuentan las membresías de cualquier estado: pendientes, anuladas y migradas.
   - Cuentan los pagos, también los anulados.
   - Cuentan los registros de `access_logs`.
3. **Con historial no se borra:** el backend responde que solo se puede desactivar y dice cuánto historial tiene.
4. **El borrado es permanente** y queda en `audit_logs` (`CLIENT` / `DELETE`), con quién lo hizo y una copia de los datos borrados: documento, nombre, celular, correo, estado y fecha de registro.
5. **Sin carreras:** el cliente se bloquea mientras se revisa su historial, así una inscripción simultánea no puede quedar huérfana. La base de datos también lo impide con su llave foránea.
6. **Dónde está:** en la ficha del cliente, en la barra de acciones del encabezado, después de "Registrar sin cobrar".
   - El ADMIN siempre lo ve. Recepción nunca.
   - Si la ficha muestra membresías o pagos, el botón aparece deshabilitado y debajo se explica por qué: "tiene historial…, solo se puede desactivar".
   - El backend vuelve a revisar todo, incluidos los accesos, y es quien decide.
   - Un cliente inscrito con el asistente siempre tiene historial, porque el asistente registra la membresía y el pago. Solo se puede eliminar un cliente creado desde "Clientes" que nunca se inscribió.

### Ninguna acción falla en silencio

- Si una petición falla, la pantalla o el diálogo muestran el mensaje del backend, o uno claro si no hay conexión.
- Si un error se escapa de una pantalla, un aviso global lo muestra (`UserErrorHandler`).

---

## Pendientes de definir

- **Caja (por confirmar con el dueño):** ¿qué se hace con el efectivo que sobra después del arqueo? Hoy rige el valor por defecto de la sección 10.
- **Puesta en marcha:** falta implementar el procedimiento seguro para borrar los datos de prueba y reiniciar el consecutivo (sección 10).
