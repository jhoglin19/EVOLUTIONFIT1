# Diseño visual – EvolutionFit (tema oscuro)

Estado: borrador v1 aprobado en dirección general por el dueño del proyecto (negro, naranja y gris; aspecto profesional de gimnasio). Es un cambio **solo visual**: no cambia reglas de negocio, rutas, textos funcionales ni lógica.

Referencia visual: lienzo "Rediseño EvolutionFit" (Inicio, Caja del día, Nueva inscripción, Clientes y Sistema de diseño). Este documento es la fuente de verdad de los valores.

## 1. Colores (tokens)

| Token | Valor | Uso |
| --- | --- | --- |
| `--ef-bg` | `#0B0B0C` | Fondo de la aplicación |
| `--ef-sidebar` | `#0F0F10` | Barra lateral |
| `--ef-surface` | `#151517` | Tarjetas, tablas, diálogos |
| `--ef-surface-2` | `#1C1C1F` | Bloques dentro de una tarjeta |
| `--ef-hover` | `#18181B` | Fila o ítem con el cursor encima |
| `--ef-border` | `#232326` | Bordes y separadores |
| `--ef-border-strong` | `#3A3A40` | Bordes de botones secundarios |
| `--ef-field-border` | `#67676D` | Borde de los campos de texto (ajuste de contraste, ver abajo) |
| `--ef-text` | `#F5F5F4` | Texto principal |
| `--ef-text-2` | `#D4D4D8` | Texto secundario |
| `--ef-text-3` | `#A3A3AB` | Etiquetas, descripciones |
| `--ef-muted` | `#8B8B94` | Texto de apoyo, encabezados de tabla |
| `--ef-orange` | `#FF7A1A` | Acento: botón primario, cifras clave, ítem activo |
| `--ef-orange-light` | `#FF8F3F` | Enlaces y texto sobre fondo oscuro |
| `--ef-orange-tint` | `rgba(255,122,26,.14)` | Fondo del ítem activo y de estados "por vencer" |
| `--ef-success` | `#4ADE80` | Activa, caja abierta |
| `--ef-danger` | `#F87171` | Vencida, anulada, retiros |
| `--ef-info` | `#60A5FA` | Congelada |

El texto sobre el naranja es `#0B0B0C`. Cumplir contraste WCAG AA en texto normal; si algún valor no cumple, ajustarlo y avisar.

**Ajustes de contraste aprobados por el dueño** (todas las demás combinaciones del documento cumplen AA):

- **Borde de los campos:** `#3A3A40` daba 1,6–1,7:1 y WCAG 1.4.11 pide 3:1 para el borde que identifica un campo. Se usa `--ef-field-border: #67676D` (3,03 a 3,41:1 sobre `#0F0F10`, `--ef-surface` y `--ef-surface-2`). `--ef-border-strong` queda para los botones secundarios, que se identifican por su texto.
- **Texto del chip gris** (Inactivo y otros): `--ef-muted` sobre su tinte daba 4,4999:1. Se usa `--ef-text-3` (`#A3A3AB`), 6,02:1.

## 2. Tipografía

- Títulos y cifras grandes: **Barlow Condensed** (600–800), en mayúsculas para títulos, `letter-spacing: .02–.08em`.
- Interfaz y tablas: **Inter** (400–600).
- Título de página: 44 px. Título de tarjeta: 26 px. Cifras clave (KPI): 56–68 px, peso 800.
- Texto de interfaz: 14 px. Texto de apoyo: 12–13 px.
- Encabezados de tabla y de sección del menú: 11 px, mayúsculas, `letter-spacing: .14em`, color `--ef-muted`.
- Las fuentes deben quedar empaquetadas con la aplicación (por ejemplo, con `@fontsource`), sin depender de un servidor externo en tiempo de ejecución.

## 3. Forma y espacio

- Radios: tarjetas 16 px; botones y campos 10 px; botones pequeños 8 px; chips y estados 99 px.
- Alturas: botón 44 px (pequeño 36 px); campo 48 px; chip de estado 26 px.
- Tarjetas: fondo `--ef-surface`, borde 1 px `--ef-border`, relleno 24 px. Sin sombras fuertes.
- Tarjeta destacada (la cifra a la que hay que mirar): borde `--ef-orange` y halo `0 0 0 3px rgba(255,122,26,.12)`.
- Espacio entre secciones: 24–28 px. Márgenes de la página: 32 px arriba, 40 px a los lados.

## 4. Estructura general (shell)

- Barra lateral fija de 264 px, fondo `--ef-sidebar`, borde derecho `--ef-border`.
- Arriba, el logotipo de EvolutionFit (toro) y el nombre "EVOLUTIONFIT" con "FIT" en naranja. Mientras no esté el archivo original del toro (lo deja el dueño en `docs/logo/`), solo va la marca en texto; no se dibuja ni se imita un toro. Con el archivo se genera también el favicon.
- Grupos del menú: **Operación** (Inicio, Vencimientos, Nueva inscripción, Clientes, Membresías, Pagos, Planes), **Caja** (Caja, Cierres de caja, Reporte mensual) y **Administración** (Reportes, Configuración de caja, Datos del comprobante, Usuarios). Respetar la visibilidad actual por rol: recepción no ve lo que hoy no ve.
- **Planes va en Operación para todos los roles** (decisión del dueño): recepción lo usa a diario y así no ve un grupo "Administración" con una sola opción. Un grupo sin opciones visibles no se muestra.
- Ítem activo: texto `--ef-orange-light`, fondo `--ef-orange-tint` y franja naranja de 3 px a la izquierda.
- Insignia numérica naranja para los vencimientos próximos.
- "Cerrar sesión" abajo del todo en la barra lateral, y también en el menú del usuario.
- Cabecera de cada página: título grande a la izquierda y, a la derecha, la acción principal y el usuario (iniciales, nombre y rol).

## 5. Componentes

- **Botón primario:** fondo naranja, texto negro, peso 600. **Secundario:** transparente, borde `--ef-border-strong`, texto claro. Estado deshabilitado con opacidad reducida y sin cursor de clic.
- **Campo de texto:** fondo `#0F0F10`, borde `--ef-field-border`, texto claro; foco con borde naranja. Etiqueta arriba en `--ef-text-3`. Errores en `--ef-danger` con mensaje en español.
- **Chip de estado:** Activa (verde), Por vencer (naranja), Vencida (rojo), Congelada (azul), Inactivo/otros (gris, con texto `--ef-text-3`). Siempre con texto, nunca solo color.
- **Tablas:** sin bordes verticales; separadores horizontales `--ef-border`; fila con `--ef-hover`; paginador en español.
- **Diálogos:** fondo `--ef-surface`, borde `--ef-border`, ancho suficiente para sus tablas, botones de acción alineados a la derecha.
- **Selector de plan:** tarjetas seleccionables; la elegida con borde naranja de 2 px y fondo `--ef-orange-tint`.
- **Métodos de pago:** botones tipo chip; el elegido en naranja.
- **Barras de proporción** (ingresos por método): el efectivo en naranja, el resto en gris.

## 6. Pantallas del borrador

1. **Inicio:** cuatro tarjetas de cifras (vigentes, por vencer, vencidas recientes, inscripciones de hoy); caja de hoy con efectivo esperado en grande; ingresos de hoy por método; lista "Por vencer en 5 días" con acción; pagos de hoy.
2. **Caja del día:** tres totales (efectivo en caja, otros métodos, total general recaudado); movimientos con filtros; arqueo; estado del cierre.
3. **Nueva inscripción:** búsqueda por documento; datos del cliente; planes; pago con método y total; "Cobrar y generar recibo".
4. **Clientes:** búsqueda, filtros por estado, tabla con estado y paginador.
5. El resto de pantallas (Pagos, Membresías, Reportes, Planes, Configuración, Usuarios, Login, ficha del cliente) siguen el mismo sistema.

## 7. Reglas del cambio

- Solo visual. No tocar lógica, servicios, rutas ni reglas de negocio.
- Conservar los textos, las etiquetas y los roles de accesibilidad actuales, porque las pruebas e2e dependen de ellos. Si hace falta cambiar alguno, avisar antes.
- Todo `<form>` conserva su `[formGroup]` (lo vigila `scripts/check-forms.mjs`).
- Pasar `npm test`, `ng build`, `check-forms` y `npm run e2e` en cada etapa.
- Sin dependencias nuevas pesadas sin aprobación.
- Que se vea bien al menos desde 1280 px de ancho; en pantallas más estrechas, el menú puede colapsarse.
- Los datos del borrador son de ejemplo.
