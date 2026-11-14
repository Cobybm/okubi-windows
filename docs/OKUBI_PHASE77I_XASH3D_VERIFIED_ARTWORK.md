# OKUBI 遊 — Fase 77I · Xash3D FWGS con captura real verificada

## Objetivo y estado
Esta fase agrega **una única imagen auténtica** a PORTS PC y CINEMA UNIFIED en Windows. Continúa la rama 77H, sin fusionar a `main`. No altera el proyecto Android, VIDAS ni los datos de usuario.

**Cobertura prevista del catálogo (32 ports):**
- 18 ports con fuente exacta propia (17 antes).
- 40 imágenes vinculadas en total: 18 principales y 22 secundarias.
- 12 galerías múltiples sin cambios; Xash3D tiene una sola imagen.
- 14 ports sin fuente aprobada siguen mostrando el fondo CINEMA neutro y aceptan fotografía manual.

## Fuente visual añadida y comprobación
- **Catálogo:** `xash3d` → `FWGS/xash3d-fwgs`.
- **Imagen:** `Documentation/images/ios-touch-ingame.jpg` (captura in-engine Xash3D con controles táctiles y un escenario GoldSrc, no un logotipo ni un arte promocional).
- **Origen:** [repositorio oficial FWGS/xash3d-fwgs](https://github.com/FWGS/xash3d-fwgs).
- **Commit inmutable:** `707596f4905689ec224e993fcaa4d4ad66aa67d6`.
- **Ruta fija:** `https://raw.githubusercontent.com/FWGS/xash3d-fwgs/707596f4905689ec224e993fcaa4d4ad66aa67d6/Documentation/images/ios-touch-ingame.jpg`.

El archivo fue identificado en el árbol del proyecto, recuperado y **examinado visualmente**: presenta una escena de juego GoldSrc bajo el motor y HUD de Xash3D, con controles táctiles en pantalla. No representa la interfaz normal de PC, por lo que no debe confundirse con un ejemplo de cómo se verán los mandos en Windows. Los datos del juego comercial **no se distribuyen** ni se descargan desde OKUBI.

Se rechazaron otras imágenes candidatas revisadas: algunos archivos de OpenMW eran iconos, y otras imágenes eran ejemplos de configuración o material de dependencias que no demostraban el juego de origen. No se incorporan imágenes de proyectos ajenos para inflar el catálogo.

## Aislamiento y seguridad
- URL HTTPS exacta en la allowlist, inmovilizada por SHA de 40 caracteres.
- Ninguna solicitud de imagen introduce telemetría, acceso a partidas o archivos originales.
- La caché usa el hash de identidad `FWGS/xash3d-fwgs`; los recursos de otros proyectos no se mezclan.
- La imagen manual conserva la máxima prioridad, sin tocar su archivo original.
- El archivo oficial se descarga solo bajo las reglas existentes, con límite de 2 MB, validación de formato y sin aceptar redirecciones.
- Offline conserva el fallback o la captura disponible en caché.

## Validación
Los tests existentes se ampliaron para exigir 18 fuentes, 40 imágenes, la identidad exacta Xash3D, la ruta inmutable, la exclusión de fuentes no aprobadas y el aislamiento de la caché. Los pipelines de Windows y Ubuntu siguen ejecutando:
- `DesktopPortArtworkServiceTest`
- `DesktopPortGalleryTest`
- `DesktopVerifiedArtworkExpansionTest`

Antes de integrar: confirmar los estados de GitHub Actions del **último commit**, verificar en PC que la ficha Xash3D muestra la imagen correcta y que una imagen manual sigue primando; repetir con Internet apagado después de haber cacheado la captura. Revisar los términos de reutilización de la imagen si se pretende distribuir un paquete con activos embebidos. La implementación actual solo vincula la imagen; no la empaqueta.

**No fusionar a main, no publicar versión estable ni modificar Android o VIDAS sin autorización.**
