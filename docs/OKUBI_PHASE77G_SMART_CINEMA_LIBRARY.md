# OKUBI 遊 — Fase 77G · Biblioteca visual inteligente para Windows

## Objetivo y alcance

La biblioteca **CINEMA UNIFIED** y **PORTS PC** comparten las mismas imágenes oficiales verificadas, la misma prioridad de arte personalizado y el mismo visor CINEMA PRO, sin alterar juegos, partidas o instalaciones. Esta fase amplía la interfaz de Windows, no Android.

## Funcionalidad implementada

- En una tarjeta de port Windows vinculado, la imagen miniatura abre mediante **clic / Enter / Espacio** el mismo visor ampliado utilizado por **PORTS PC**. Se ofrece además **▣ GALERÍA** cuando hay una imagen configurada o fuente oficial revisada.
- Las capturas horizontales no se recortan al entrar en el espacio vertical estrecho: se muestran completas, centradas sobre el fondo CINEMA, en lugar de cortar la interfaz del juego o convertirla en una mancha indistinguible.
- Una etiqueta honesta distingue:
  - **IMAGEN PERSONAL**: hay un archivo de imagen elegido previamente por el propietario, con prioridad absoluta.
  - **FUENTE OFICIAL REVISADA**: existe un origen específico del proyecto, fijado a commit por la lista de la Fase 77E. No significa que haya descargado correctamente la imagen ni que el juego esté instalado.
  - **SIN CAPTURA VERIFICADA**: el repositorio no está en la lista revisada y no tiene archivo personal configurado. Muestra fondo CINEMA neutro; ninguna imagen de GitHub de otro proyecto.
- Al disponer de varias capturas, se indica su número verdadero. No se inventa arte para repositorios encontrados mediante búsqueda.
- La biblioteca retro (GB/GBC/GBA/SNES) conserva su propio sistema de carátulas, navegación y botón **JUGAR**, sin cambios.
- El botón **ABRIR PC** permanece explícito y sigue requiriendo la confirmación de ejecución previa; ver capturas **nunca ejecuta el EXE**.
- Mantiene el acceso al catálogo de ports y sus botones de descarga externa, vinculación y personalización.
- El estado del archivo personalizado usa solo metadatos locales acotados, sin decodificar cada PNG en el hilo Swing. La decodificación de miniaturas continúa en trabajadores asíncronos de la Fase 77E.
- No se modifica ninguna ruta de caché: `%APPDATA%/OKUBI/port-artwork-v1/manual/` sigue siendo prioridad; `official-screenshots-v2/` y `official-gallery-v1/` permanecen independientes.
- El catálogo sigue teniendo las **13 imágenes principales y 14 extras** (27) de las fases anteriores, con exactamente los mismos enlaces permitidos.

## Archivos modificados

- `DesktopUnifiedLibraryDialog.java` — miniaturas completas, visor en biblioteca y estados de fuente verificados.
- `DesktopCinemaArtworkPolicy.java` — política determinista que asigna personal, revisada o sin foto por identidad exacta.
- `DesktopPortArtworkService.java` — detección ligera y segura de personalización configurada sin carga de imagen en EDT.
- `DesktopCinemaArtworkPolicyTest.java` — fixtures PNG sintéticos, sin acceso HTTP ni juegos.
- `.github/workflows/okubi-phase73-windows-ports.yml` y `.github/workflows/okubi-desktop.yml` — compilan y ejecutan QA junto con las pruebas previas.

## Regresión automática

1. Un proyecto revisado obtiene únicamente su propia imagen y contador de capturas.
2. Repositorios sin fuente revisada no pueden recibir arte de otro juego.
3. Una imagen manual de proyecto descubierto tiene prioridad sobre la ausencia de fuente oficial.
4. Una imagen manual de port revisado prevalece sobre la captura oficial.
5. Quitar una imagen personal del port A no modifica la del port B.
6. Miniatura 110×147 con fotografía horizontal sintética deja márgenes reales y conserva el centro de la imagen, sin recorte panorámico.
7. No se añaden botones de flechas dentro de las minifichas.
8. Ninguna prueba carga ROMs, firma APK, modifica saves ni ejecuta archivos EXE.

## Verificación física Windows pendiente

1. Instala el ZIP de prueba de esta fase en **carpeta nueva**, sin reemplazar la versión anterior ni utilizar datos personales durante las pruebas iniciales.
2. Abre CINEMA UNIFIED y localiza OpenTTD/OpenRA o un port vinculado con imagen oficial.
3. Verifica que la miniatura muestra la escena completa y el contador real, con la acción **GALERÍA**; pulsa en miniatura y prueba teclado/mando en el visor.
4. Comprueba que el botón **ABRIR PC** sigue separado de la visualización, y que no se inicia nada al hacer clic en la imagen.
5. En un proyecto descubierto por GitHub sin fotos verificadas, comprueba el fondo neutro.
6. Configura una imagen personal desde PORTS PC; cierra y reabre CINEMA para confirmar que se conserva en las dos vistas.
7. Desconecta Internet tras descargar capturas: comprueba la continuidad de la caché.
8. Verifica filas en anchos de 1/2/3 columnas, texto/botones legibles sin superposición.

## Restricciones de release

**PR borrador, basado en 77F; sin merge a main, sin release estable y sin reemplazar APK Android**. Pendientes QA visual Windows, revisión de licencias, firma Android canónica y Cross Save físico.
