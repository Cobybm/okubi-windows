# OKUBI 遊 — Fase 73: PORTS PC / GITHUB

**Estado:** beta Windows, PR independiente apilado sobre Fase 72. No fusionado a `main`.
**Alcance:** solo Windows. La APK Android, el motor retro, los perfiles y los guardados existentes no se modifican.

## Cómo usar PORTS PC

1. Abre OKUBI Windows y pulsa **✦ PORTS PC · GITHUB** en el menú lateral.
2. Busca un proyecto o filtra por **género** y **tipo de datos**:
   - **Gratis o con datos libres:** un juego libre completo, un motor que puede usar contenido libre o recursos disponibles legalmente. Lee siempre la ficha.
   - **Requiere archivos originales:** solo motor/código. Consigue legítimamente los datos del juego comercial; NO vienen con OKUBI.
   - **Admite datos libres u originales:** se necesita contenido compatible, quizá libre y quizá de tu copia legal.
3. **DESCARGAS** abre una página oficial de GitHub. Cuando el proyecto no publica binarios allí, abre el repositorio; consulta su página web oficial para descargar Windows.
4. Comprueba los requisitos de Windows 64 bits (o x86), licencia, procedencia y advertencias del navegador. Descarga/extrae tú el port; OKUBI no instala ni ejecuta descargas automáticamente.
5. Pulsa **VINCULAR .EXE** y selecciona el ejecutable ya instalado. El archivo debe existir, ser PE Windows x64/x86 y no ser un enlace simbólico.
6. Aparecerá **▶ JUGAR**. Confirma el archivo y su ruta para iniciar el programa externo. OKUBI no modifica su carpeta, saves o mods; el port utiliza sus propios guardados.

**Importante:** la clasificación de rendimiento «ligero/medio/variable» es únicamente orientativa y **no** garantiza que un título funcione en un procesador, tarjeta gráfica o Windows específicos. Especialmente motores 3D como OpenMW, dhewm3, Daggerfall Unity o SuperTuxKart dependen de controladores y recursos del equipo. El programa no instala emuladores externos por la fuerza.

## Proyectos curados en la versión inicial (25)

| Motor/juego | Repositorio oficial (GitHub) | ¿Datos del juego original? |
| --- | --- | --- |
| OpenTTD | https://github.com/OpenTTD/OpenTTD | No, con recursos libres |
| DevilutionX | https://github.com/diasurgical/DevilutionX | Sí, o shareware autorizado |
| OpenRCT2 | https://github.com/OpenRCT2/OpenRCT2 | Sí, RollerCoaster Tycoon 2 |
| OpenLoco | https://github.com/OpenLoco/OpenLoco | Sí, Locomotion |
| OpenMW | https://github.com/OpenMW/openmw | Sí, Morrowind |
| ScummVM | https://github.com/scummvm/scummvm | Según el juego |
| GZDoom | https://github.com/ZDoom/gzdoom | WAD compatible libre u original |
| dhewm3 | https://github.com/dhewm/dhewm3 | Sí, Doom 3 |
| CorsixTH | https://github.com/CorsixTH/CorsixTH | Sí, Theme Hospital |
| Augustus | https://github.com/Keriew/augustus | Sí, Caesar III |
| Julius | https://github.com/bvschaik/julius | Sí, Caesar III |
| GemRB | https://github.com/gemrb/gemrb | Sí, RPG compatible |
| OpenRA | https://github.com/OpenRA/OpenRA | Consulta contenido ofrecido por el proyecto |
| OpenTyrian | https://github.com/opentyrian/opentyrian | Datos autorizados por separado |
| VCMI | https://github.com/vcmi/vcmi | Sí, Heroes III |
| Endless Sky | https://github.com/endless-sky/endless-sky | No |
| Warzone 2100 | https://github.com/Warzone2100/warzone2100 | No |
| Freeciv | https://github.com/freeciv/freeciv | No |
| OpenJK | https://github.com/JACoders/OpenJK | Sí, Jedi Academy/Outcast |
| Yamagi Quake II | https://github.com/yquake2/yquake2 | Sí, Quake II |
| Arx Libertatis | https://github.com/arx/ArxLibertatis | Sí, Arx Fatalis |
| Xash3D FWGS | https://github.com/FWGS/xash3d-fwgs | Sí, Half-Life u otro compatible |
| Luanti | https://github.com/luanti-org/luanti | Juego libre adicional |
| SuperTuxKart | https://github.com/supertuxkart/stk-code | No |
| Daggerfall Unity | https://github.com/Interkarma/daggerfall-unity | Datos originales obtenibles gratis |

La lista es una **selección inicial verificable**, no un índice de todos los proyectos públicos de GitHub. El botón **BUSCAR MÁS EN GITHUB** abre la búsqueda oficial de GitHub para ampliar investigaciones futuras. Proyectos para Linux/ARM o PortMaster NO se consideran ejecutables Windows solo por estar alojados en GitHub; por ejemplo, PortMaster distribuye ports para consolas Linux, no paquetes nativos de PC Windows.

## Seguridad y datos

- Nunca se descarga un archivo desde el interior de OKUBI, ni se ejecutan scripts de instalación automática, ni se desactivan Microsoft Defender/SmartScreen o los permisos del sistema.
- Abrir páginas externas exige HTTPS y host github.com.
- El ejecutable vinculado debe existir, tener cabeceras `MZ/PE` y arquitectura Windows x86/x64. No se admiten `.bat`, `.cmd`, enlaces simbólicos ni archivos falsos con extensión `.exe`.
- Solo la pulsación explícita de **▶ JUGAR**, seguida de confirmación con ruta visible, puede iniciar un ejecutable del usuario. Se usa `ProcessBuilder` directo, sin intérprete de comandos.
- Los vínculos se guardan únicamente en `%APPDATA%/OKUBI/desktop-ports-v1.properties` (perfil QA si se utiliza el lanzador aislado) y se escriben de forma atómica. No hay estadísticas remotas ni transferencias de rutas personales.
- Quitar un vínculo no desinstala ni elimina datos del port.

## Validación pendiente

- [x] Catalogar 25 proyectos en GitHub con sus requisitos de archivos.
- [x] Añadir catálogo Windows con filtros y enlaces oficiales.
- [x] Implementar selección protegida de EXE local, vínculo y ejecución manual.
- [ ] Validar el botón en una PC física y vincular un port **gratuito** de prueba (por ejemplo OpenTTD).
- [ ] Revisar los resultados de GitHub Actions Windows de la rama.
- [ ] Realizar revisión final de licencias de cualquier binario de OKUBI que vaya a distribuirse; los ports externos no se redistribuyen con OKUBI.
- [ ] Autorizar integración de PRs por orden, sin tocar el repositorio privado original.
