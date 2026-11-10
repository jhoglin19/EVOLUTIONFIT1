# Integración del control de acceso físico – EvolutionFit

Estado: requisitos para la fase 7 (control de acceso). Complementa `docs/REGLAS_NEGOCIO.md`.
Toda la identificación del hardware es **visual y preliminar** (hecha por una IA a partir de una foto). Hay que verificarla antes de implementar nada.

## 1. Objetivo

EvolutionFit debe abrir realmente la puerta del gimnasio. El **cliente digita su número de documento (cédula) en un teclado numérico instalado en la puerta**; el sistema valida su membresía y, si corresponde, activa el relé que libera la puerta. La recepción no participa en el flujo normal.

## 2. Flujo normal

1. El cliente digita su documento en el teclado de la puerta y confirma.
2. El teclado está conectado al computador del gimnasio (probablemente como un teclado USB, por verificar): el número se escribe en el campo de la **pantalla de acceso** de EvolutionFit. Una pantalla en la puerta replica la del computador.
3. El backend busca al cliente y valida su estado y su membresía.
4. Si está autorizado, el backend ordena abrir; el controlador activa el relé por un tiempo corto y fijo.
5. Se registra el intento en `access_logs`, con su resultado y el estado de la comunicación con el controlador.
6. Si es denegado, la puerta no abre y el cliente recibe una señal simple (buzzer o mensaje corto).

Excepciones: recepción también puede abrir la puerta a personas sin membresía vigente (por ejemplo, quien viene a preguntar por un plan, o un cliente vencido al que se decide dejar pasar). Se hace con una **acción distinta y autenticada** ("Abrir puerta" con motivo obligatorio: visita, consulta de planes u otro), no digitando un documento. Así queda registrado quién abrió y por qué, y no se comparte ninguna cédula como llave. (Por confirmar con el dueño: si prefiere que recepción digite su propio documento.)

El personal (administrador y recepción) también necesita entrar al gimnasio. Hay que definir cómo: la tabla de usuarios del sistema puede no tener documento, por lo que habrá que decidirlo (por ejemplo, registrar al personal también como persona con acceso).

## 3. Reglas de autorización (el backend decide, nunca el microcontrolador)

| Situación | Resultado |
| --- | --- |
| Cliente activo con membresía vigente | AUTHORIZED |
| Membresía vencida (sin días de gracia) | DENIED |
| Membresía congelada | DENIED |
| Cliente inactivo | DENIED |
| Documento inexistente | DENIED |
| Error de comunicación con el controlador | No se confirma la apertura; se registra el error |

- Todo intento se registra, también los fallidos y los errores de comunicación.
- En la puerta solo se muestra un resultado genérico (abierto / denegado). El motivo exacto va a `access_logs` y a la pantalla de recepción; no se muestra en la puerta, para no revelar a terceros el estado de otra persona.

## 4. Seguridad

- Un documento digitado en un teclado es una credencial débil: cualquiera que conozca una cédula puede intentar entrar con ella. Mitigaciones a diseñar y proponer al dueño:
  - Límite de intentos por minuto y bloqueo temporal tras varios intentos fallidos seguidos.
  - Tiempo mínimo entre dos aperturas del mismo documento.
  - Revisión periódica del historial de accesos por el administrador.
- El frontend nunca activa el relé. Toda apertura pasa por una decisión del backend.
- El dispositivo o servicio de la puerta se autentica ante el backend con su propia credencial (no con el JWT de un usuario), guardada en `.env`, nunca en el código.
- Ante cualquier error de comunicación, la puerta **no abre automáticamente** (falla cerrada), salvo la apertura manual de recepción.
- El relé nunca puede quedar activo indefinidamente: tiempo máximo de activación fijo y configurable.
- Un error del controlador no modifica nunca el estado de una membresía.
- La salida y las emergencias no deben depender de este sistema. El gimnasio debe conservar su método físico de apertura (llave, botón de salida o similar) funcionando si el sistema se cae.
- No modificar el firmware del ESP8266 sin haber hecho antes una copia completa de su memoria flash y sin aprobación explícita.

## 5. Incógnitas que hay que resolver antes de programar

1. **¿Cómo llega lo digitado al sistema?** El dueño indica que el teclado está conectado al computador. Hipótesis más probable: teclado USB que escribe en la ventana activa. Verificar. Consecuencias si es así: (a) el sistema no distingue si tecleó el cliente en la puerta o recepción en el mismo teclado; (b) si la ventana de EvolutionFit pierde el foco, la cédula se escribe en otro lado; (c) la pantalla de acceso debe mantener siempre el cursor en el campo de documento.
1b. **La pantalla de la puerta replica la del computador.** Todo lo que se muestre ahí lo ve cualquiera que esté en la puerta. La pantalla de acceso debe mostrar poco (nombre de pila y resultado), nunca plan, pagos ni datos del cliente, y recepción no debería abrir pantallas con datos de otros clientes mientras la puerta replica la pantalla. Decidir con el dueño si se usa una segunda pantalla o una vista separada.
1c. **Sesión de la pantalla de acceso.** Con JWT de 8 horas, si la sesión vence la puerta deja de funcionar. La pantalla de acceso necesita una credencial de dispositivo (o renovación automática) para funcionar todo el día sin intervención.
2. **¿Qué recibe el ESP8266?** ¿El número de documento, o solo una orden de abrir?
3. **¿Cómo se comunica el software actual con la placa?** Puerto COM, velocidad, formato de los comandos y respuestas. No inventar comandos.
4. **¿Hay sensor de puerta** que confirme que realmente abrió? Si no, solo se puede registrar "orden enviada" y "respuesta del controlador", no "apertura confirmada".
5. **¿Dónde corre el backend?** Un cable USB/COM solo es accesible desde el computador al que está conectado. Si el backend no corre en ese computador, hará falta un pequeño servicio puente en él.
6. **¿Qué software usa hoy el gimnasio** y qué mantiene ocupado el puerto COM? Dos programas no pueden abrir el mismo puerto a la vez.
7. **¿Tiempo de apertura del relé y comportamiento del buzzer** actuales?

## 6. Arquitectura propuesta (a validar con el diagnóstico)

```
Cliente digita documento en el teclado (conectado al computador)
        ↓
Pantalla de acceso (Angular, en el computador del gimnasio)
        ↓  HTTP + credencial del dispositivo
Backend Spring Boot: valida cliente y membresía, registra en access_logs
        ↓  AUTHORIZED / DENIED
Servicio de hardware (puerto serial COM del mismo computador)
        ↓
ESP8266 → relé → puerta
```

- Un componente independiente se encarga de la comunicación con el controlador. No se mezcla con la lógica de membresías.
- Si el backend corre en el mismo computador que tiene el cable USB, el servicio de hardware puede ser un módulo del backend. Si corre en otra parte, hará falta un programa puente en ese computador que reciba las órdenes del backend. Claude Code debe proponer cuál conviene tras el diagnóstico.

## 7. Registro de accesos

Usar la tabla `access_logs` existente. Antes de cambiar nada, leer su definición actual y proponer una migración Flyway (V3 o posterior, nunca editar V1) solo para lo que falte. Datos mínimos:

- Cliente asociado (si existe) y documento digitado.
- Fecha y hora.
- Resultado: AUTHORIZED o DENIED.
- Motivo exacto del resultado.
- Estado de comunicación: orden enviada, respuesta recibida, error.
- Identificador del dispositivo.
- Cuando el hardware lo permita: apertura confirmada. No registrar apertura física exitosa solo porque se envió una orden.

## 8. Pantalla de recepción

- Estado del controlador: CONECTADO o DESCONECTADO.
- Últimos accesos en tiempo casi real (cliente, hora, resultado, motivo).
- Botón de apertura manual, solo para usuarios autenticados, con motivo obligatorio.
- Consulta del cliente por documento: nombre, plan, vencimiento y días restantes.

## 9. Plan por etapas (esperar aprobación entre etapas)

1. Analizar el proyecto y decidir dónde encaja el módulo de acceso.
2. Investigar el hardware y el software actual. **Solo lectura, sin cambiar nada.** Hacerlo fuera del horario del gimnasio para no dejar la puerta sin servicio.
3. Crear una herramienta de diagnóstico serial independiente, sin afectar el sistema actual.
4. Diseñar el servicio de integración (sección 6).
5. Implementar la validación de membresías y la autorización de acceso en el backend.
6. Integrar el frontend (pantalla de recepción).
7. Pruebas controladas: apertura, rechazo, desconexión y recuperación.

Dependencia: la validación de membresías (etapa 5) necesita la fase 4 del proyecto (membresías y congelaciones). La investigación de hardware (etapas 1 a 3) puede hacerse antes porque es la mayor incógnita.

## 10. Prohibido

- Inventar comandos seriales o presentar una simulación como apertura real.
- Reemplazar funcionalidades que ya funcionan.
- Programar o modificar el ESP8266 sin copia de seguridad y sin aprobación.
