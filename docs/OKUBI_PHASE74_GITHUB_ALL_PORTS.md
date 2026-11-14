# OKUBI 遊 · Fase 74 — Explorador GitHub de ports para PC

## Objetivo

Pasar de una lista cerrada de **25** proyectos a un catálogo **ampliable y consultable** desde OKUBI Windows.

**Incluye ahora 32 proyectos con ficha revisada** y el apartado adicional **EXPLORAR TODO GITHUB** para consultar repositorios públicos, incluso nuevos, por categoría o por texto libre. No es posible afirmar que literalmente todos los ports estén registrados: GitHub no ofrece un censo fiable de ports de PC y no todos sus proyectos son ejecutables Windows.

## Funciones del explorador

- Accede desde **OKUBI Windows → ✦ PORTS PC · GITHUB → EXPLORAR TODO GITHUB**.
- Categorías: ports de videojuegos, motores abiertos, juegos Windows, FPS clásicos, RPG, estrategia, retro, emuladores y juegos libres.
- Búsqueda libre de nombres, repositorios y palabras clave de hasta 80 caracteres. Si no escribes nada, utiliza la categoría.
- Resultados públicos obtenidos desde el **servicio oficial de búsqueda GitHub API**. Se omiten forks, archivados y proyectos privados.
- **40 resultados por página**, botones para avanzar y regresar, resultados anteriores almacenados en memoria mientras está abierta la ventana; no descarga todos los resultados ni crea miles de tarjetas simultáneamente (importante para PC modestos).
- Metadatos: propietario/nombre, lenguaje, estrellas y descripción, con enlaces originales **REVISAR GITHUB** y **RELEASES**.
- **CANDIDATO POR REVISAR:** un repositorio encontrado no se afirma compatible con Windows, seguro o legalmente redistribuible solo por aparecer en una búsqueda.
- **VINCULAR .EXE / JUGAR:** únicamente si el usuario ya tiene un ejecutable Windows que ha revisado personalmente; hay doble confirmación y validación de cabecera PE, sin scripts, enlaces simbólicos o instalación automática. Quitar un vínculo no elimina juegos ni partidas.

## Límites reales de GitHub

Según la [API de GitHub](https://docs.github.com/en/rest/search/search), la búsqueda entrega **hasta 1.000 resultados por consulta**, máximo **100 por página**, y para consultas **sin autenticación** permite alrededor de **10 solicitudes/minuto**. OKUBI usa 40 resultados por página y como máximo 25 páginas por consulta; no utiliza tokens ni solicita que el usuario introduzca claves.

Para buscar más allá de 1.000 resultados, cambia los términos o la categoría y vuelve a buscar. GitHub puede devolver resultados parciales o aplicar límites, que OKUBI mostrará sin inventar datos. No se recorre ni se «clona todo GitHub».

## Seguridad y privacidad

- La app consulta únicamente `https://api.github.com/search/repositories`, vía HTTPS, al pulsar explícitamente **BUSCAR EN GITHUB** o **SIGUIENTES 40**. No hay búsqueda en segundo plano.
- Respuesta JSON limitada en tamaño, con validación de propietarios, nombres, estado de repositorio y URL canónica `github.com/propietario/repositorio`. Redirecciones bloqueadas.
- La búsqueda solo envía los términos de consulta a GitHub. **No envía rutas locales, inventario de juegos, partidas, datos de cuenta ni tokens**.
- Los vínculos locales están en el perfil Windows/QA, no en el repositorio GitHub público. Cualquier ejecución de un programa de terceros requiere un clic y confirmación con ruta visible.
- `main`, Android y el repositorio privado original siguen sin modificaciones.

## Para probar en Windows

1. Abre una copia de pruebas aislada de OKUBI e ingresa a **PORTS PC · GITHUB**.
2. Comprueba que hay **32 proyectos revisados**, con filtro por género y requisitos de archivos.
3. Abre **EXPLORAR TODO GITHUB**, elige **FPS clásicos** y pulsa **BUSCAR EN GITHUB**.
4. Verifica que aparezcan resultados clasificados como candidatos; pulsa **SIGUIENTES 40** y **ANTERIORES** y prueba la búsqueda libre, por ejemplo `quake port`.
5. Si GitHub bloquea por límite de solicitudes, comprueba que aparece un aviso y que puedes seguir utilizando el catálogo revisado.
6. Abre un repositorio oficial y, solo si ya instalaste un port Windows confiable, vincula el `.exe` para probar **JUGAR**.
7. Cierra y vuelve a abrir OKUBI: el vínculo debe persistir sin haber modificado los archivos del juego ni sus guardados.

## Estado de lanzamiento

La vista de ports es una funcionalidad **beta**. El éxito de CI no valida todos los repositorios públicos ni la compatibilidad en hardware real. No autoriza todavía una versión estable: siguen pendientes Cross Save físico, correspondencia exacta de fuentes de DLL Windows, revisión de licencias y certificado de actualización Android.
