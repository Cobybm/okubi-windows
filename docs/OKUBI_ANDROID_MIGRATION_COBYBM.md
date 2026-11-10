# OKUBI 遊 — Migración Android a cuenta Cobybm

## Snapshot de Android
- Código tomado de la rama de desarrollo privada que acompaña la Fase 69 de Windows (snapshot del 10 de noviembre de 2026).
- Se migran `android-app/`, `retro-module/`, `catalogs/` y herramientas de QA seleccionadas.
- El proyecto editable `android-app/` es la fuente de la app. `retro-module/integrate.py` lo integra con los núcleos retro, sin recurrir al ZIP histórico.
- El paquete canónico sigue siendo `bo.nexoplay.app.unified`, versión de código 36, con las opciones antiguas preservadas.
- Para QA de esta migración se usa la variante de depuración `bo.nexoplay.app.unified.qa57`, con datos separados; nunca sustituye de forma automática la app instalada.

## Compilación segura
El workflow `.github/workflows/okubi-android-migrated-qa.yml` ejecuta pruebas, construye SameBoy, mGBA y Supafaust desde revisiones fuente fijadas, verifica las licencias y genera un APK QA firmado **solo con la firma debug efímera** del runner.

El APK QA no es una actualización del paquete canónico y la firma debug no garantiza actualizaciones futuras de esa variante. Nunca desinstalar una app con partidas para reemplazarla por una firma diferente.

No se incluyen ROMs comerciales, juegos, BIOS, partidas privadas, claves de firma ni ZIP históricos. Antes de cualquier publicación general, completar revisión de las licencias/código fuente correspondiente de los núcleos, pruebas en teléfono físico y firma permanente controlada por el propietario.

## Independencia
- La aplicación de Windows sigue en `desktop/` y no se modifica aquí.
- Este trabajo está en una rama apilada sobre la migración de Windows, pendiente de revisión y sin cambios en `main`.
- No modifica el repositorio privado de origen ni los proyectos VIDAS/SigmaOS.
