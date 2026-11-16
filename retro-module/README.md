# OKUBI 遊 — Retro Module (fase 1)

## Estado real
- **Implementado en este commit:** una Activity Android en Java, sin bibliotecas externas, con selección visual de GB/GBC/GBA/NES/SNES, lectura de configuración de orientación, diseño de dos columnas para pantallas horizontales amplias y selección de archivos mediante Storage Access Framework (SAF).
- **Interfaz preliminar:** la cruceta y los botones son una previsualización gráfica. Todavía no capturan pulsaciones.
- **Todavía no implementado:** núcleo ejecutable de emulación, audio, mapeo real de controles, guardado de partidas, biblioteca de ROM, ejecución de juegos, shaders y pruebas en dispositivos.
- El proyecto original está empaquetado en `source/nexo-android-1.0.zip`; por tanto, estos archivos **no se incluyen automáticamente en la compilación actual**.

## Integración con el proyecto existente
1. Descomprimir `source/nexo-android-1.0.zip` en el entorno de desarrollo.
2. Copiar `retro-module/java/bo/nexoplay/app/retro/*.java` a `app/src/main/java/bo/nexoplay/app/retro/`, ajustando la ruta si el módulo tiene otro nombre.
3. Agregar al elemento `<application>` de `AndroidManifest.xml`:
   ```xml
   <activity android:name="bo.nexoplay.app.retro.OkubiRetroActivity"
       android:exported="false"
       android:screenOrientation="unspecified" />
   ```
4. Desde la navegación OKUBI, abrir `startActivity(new Intent(this, OkubiRetroActivity.class));`.
5. Probar giro de pantalla, pantallas pequeñas, tablets, fuentes grandes, barras del sistema, selector SAF y retorno a la app antes de pasar a emulación.

## Diseño del motor de emulación
`RetroCore.java` define la frontera de integración para un motor compatible. No es un motor por sí mismo.
- Investigar motores con licencia compatible y obligaciones claras (por ejemplo, Gambatte, mGBA, Snes9x o Nestopia UE, según sistema). **No copiar código sin revisar sus licencias.**
- Usar JNI/NDK para renderizado, audio y controles, con ABI `arm64-v8a`, `armeabi-v7a` y `x86_64` únicamente cuando el motor seleccionado los soporte.
- Resolver ROM desde `ContentResolver.openFileDescriptor()`, no desde rutas absolutas.
- Separar emulación del render de botones, conservar relación de aspecto original y aplicar letterboxing si hace falta.
- Para rendimiento en celulares modestos: evitar escalar bitmaps por CPU en cada frame, usar SurfaceView/OpenGL, no bloquear el hilo UI y controlar el estado del ciclo de vida.
- BIOS y juegos no se distribuyen con OKUBI. El usuario debe aportar archivos legalmente obtenidos.

## Criterios antes de declarar completada la fase
- [ ] La Activity se integra y compila en el proyecto real
- [ ] Diseño vertical/horizontal verificado en 320–600 dp y tablets
- [ ] Cruceta y botones multitáctiles, configurables
- [ ] Core GB y GBC funcional
- [ ] Core GBA funcional
- [ ] Core NES y SNES funcional
- [ ] Audio, guardado/carga de partida y pausa al cambiar de aplicación
- [ ] Pruebas de funcionamiento, batería y temperatura en varios Android

**Compatibilidad universal no puede garantizarse:** las funciones reales dependen de Android, arquitectura CPU, memoria, GPU y licencias de los motores.


## Fase 2: Catálogo y descarga (3 de noviembre de 2026)
- `OkubiCatalogActivity.java` muestra una colección curada de juegos homebrew y fuentes autorizadas de sus creadores.
- **Descarga Android de un toque disponible para Ascension**: se confirmó que el autor enlaza el ROM `build/rom/game.gb` y que el archivo binario existe en el repositorio original. La app usa `DownloadManager` y lo guarda como `ascension.gb` en la carpeta externa privada de la aplicación. El botón no instala ni inicia el juego.
- Para otros juegos se abre la página del autor: algunos requieren confirmar descarga, seleccionar precio libre o descargar un archivo comprimido.
- Para que la pantalla sea accesible, añadir a `<application>` en el manifiesto:
  ```xml
  <activity android:name="bo.nexoplay.app.retro.OkubiCatalogActivity"
      android:exported="false"
      android:screenOrientation="unspecified"/>
  ```
- Para descargar, verificar `<uses-permission android:name="android.permission.INTERNET" />` en el manifiesto, antes de `<application>`.
- El catálogo está enlazado desde el botón «Descubre juegos retro» de `OkubiRetroActivity`.
- **Pendientes:** insertar ambos Activities en el ZIP Gradle del proyecto; integrar motor, importación automática del archivo descargado, proceso de finalización de descarga, botones táctiles funcionales y pruebas reales. El sistema «un clic para descargar y jugar» **todavía no está completo**.
- Seguridad para la fase siguiente: catálogo remoto firmado o controlado, validación de redirecciones, tamaño máximo y hash SHA-256, descarga a almacenamiento privado, manejo de fallas de red y reconexión. Evitar descargas automáticas de parches que requieren ROM comercial y evitar contenido sin permiso del titular.


## Fase 3: Colección personal Pokémon
- `OkubiPokemonActivity.java` implementa **43 títulos oficiales** listados como metadatos, desde GB hasta Switch, y **6 referencias a ROM hacks**.
- Navegación desde `OkubiCatalogActivity`, con pestañas de oficiales/hacks y filtros de consola.
- Importación de archivo del usuario mediante Storage Access Framework, acceso persistente y referencia de URI guardada localmente en preferencias privadas. **No se descargan ni se distribuyen ROMs comerciales**.
- Algunos títulos NDS, 3DS y Switch se listan como biblioteca visual, no como plataformas emulables por el motor retro actual.
- Los hacks requieren parches obtenidos de fuentes autorizadas y ROM original compatible. No hay parcheador aún.
- Agregar al manifiesto: `<activity android:name="bo.nexoplay.app.retro.OkubiPokemonActivity" android:exported="false" android:screenOrientation="unspecified" />`.
- Pendiente antes de usar: integrar el código fuente en el ZIP del proyecto principal, compilar, probar importación de archivos, diseñar biblioteca con rutas de ejecución, comprobar formatos de archivo y conectar cores emuladores con licencias adecuadas. **No hay inicio de partidas funcionando**.

## Calidad de controles e importación
Se añadió RetroTouchPad.java para entrada multitáctil con diagonales, y RetroFileInspector.java para comprobaciones iniciales de ROM y parches. El integrador y su prueba automatizada ahora incluyen ambos archivos. Todavía falta conectar las entradas a un núcleo de emulación, ejecutar el APK en Android y verificar rendimiento y guardado.


## Fase 6 — Perfiles adicionales y puentes de emulación externos (2026-11-03)

**Se añadieron definiciones de 16 sistemas, no 16 motores funcionales.**
- Recomendados en equipos de gama de entrada como el Galaxy A05s: GB, GBC, GBA, NES, SNES, SMS, GG, MD, PCE, Atari 2600, PS1 y Nintendo DS.
- Experimentales: Nintendo 64, PSP, Dreamcast y SEGA Saturn.
- Los perfiles son estimaciones de hardware; ningún dato es una medición de FPS en el A05s.
- 3DS y Nintendo Switch continúan solo como biblioteca personal de Pokémon; **no están considerados sistemas jugables**.

### Cambios incorporados
- `RetroSystems.java`: registro único de consolas, extensiones, proporciones, grupo de controles, motor candidato y rendimiento sugerido.
- `RetroDeviceProfile.java`: muestra modelo de dispositivo, memoria física aproximada y núcleos detectados; modos de vídeo propuestos, **sin aplicar aún ajustes a ningún motor**.
- `RetroTouchPad.java`: botones específicos de consola (Atari un disparo, GBA con L/R, Super Nintendo con cuatro botones, Mega Drive con seis). Los botones generan estados de pulsación en la interfaz, pero **no están conectados al motor**.
- `OkubiRetroActivity.java`: selector actualizado, pantalla dual de Nintendo DS con vista vertical/horizontal y vista de perfiles.
- `RetroFileInspector.java`: reconoce extensiones de los sistemas nuevos y mantiene archivos de 3DS/Switch exclusivamente para organización.
- `RetroExternalLauncher.java`: permite **intentar abrir ROMs propias** mediante PPSSPP (PSP) y melonDS (NDS) si están instalados; para otros sistemas muestra el selector de Android. Estos flujos requieren pruebas en dispositivos y pueden depender de versiones/permisos del emulador instalado.
- `retro-module/tests/test_profiles.py`: coherencia de IDs, niveles y controles.

### Restricciones y obligaciones antes de distribuir
1. **No hay núcleos incorporados.** No confundir "soporte en menú" con emulación funcional.
2. No descargar ROMs comerciales o BIOS sin autorización.
3. Revisar la licencia de cada motor **por separado**; algunos son GPL y Snes9x incluye restricciones para uso comercial. No unirlos en una aplicación redistribuible sin revisar las condiciones.
4. Emulación en dispositivos de baja gama: comenzar con escala nativa 1x, sin shaders ni mejora gráfica, y registrar audio, velocidad, FPS, temperatura y suspensión.
5. PSP y NDS: enlaces de integración confirmados por documentación oficial de PPSSPP y README de melonDS; no se ha verificado lanzamiento en un Android real.
6. Las pantallas de DS muestran dos espacios orientativos; el segundo no es táctil todavía.
7. El flujo de compilación necesita verificarse en Actions y en el Galaxy A05s antes de denominarse listo.


## Fase de estabilidad y optimización (2026-11-03)

### Cambios de código
- `RetroTouchPad`: cálculo con máscara de bits para todos los dedos activos en cada evento, sin crear matrices/mapas por movimiento. Captura de eventos para evitar que el ScrollView interrumpa la cruceta. Liberación al cancelar el gesto, perder foco o suspender la pantalla.
- `OkubiRetroActivity`: los 16 botones de consola se construyen una sola vez; cambiar de consola actualiza las vistas existentes. La selección de ROM se valida mediante un ejecutor en segundo plano y descarta callbacks de solicitudes anteriores.
- `RetroAspectFrame`: mantiene la proporción de imagen de cada sistema mediante letterboxing, sin estirar el contenido en pantallas anchas.
- `OkubiPokemonActivity`: importación validada fuera del hilo principal, filtros horizontales y posición de desplazamiento conservada al registrar una ROM. Los juegos clásicos importados permiten abrir un emulador externo instalado; los parches y 3DS/Switch no se anuncian como jugables.
- `RetroExternalLauncher`: intenta PPSSPP normal, Gold y Legacy; usa URI SAF de solo lectura para melonDS y selector de aplicaciones como alternativa.
- El integrador ahora mantiene **un solo icono de aplicación**, modifica el nombre visible a **OKUBI 遊** y añade el acceso a retro dentro de Inicio sin eliminar las pantallas existentes.
- `.github/workflows/okubi-retro.yml` utiliza Gradle 8.11.1, compatible con Android Gradle Plugin 8.9.2 del ZIP original, instala Android SDK 35 y verifica la estructura del APK.
- `retro-module/tests/test_performance_contracts.py`: pruebas preventivas del código crítico para baja gama.

### Cómo evaluar la fluidez en el A05s
Desde Windows con ADB configurado, después de instalar voluntariamente un APK de prueba compilado:
```powershell
powershell -ExecutionPolicy Bypass -File retro-module/qa/measure-a05s.ps1
```
Este script **no instala ni modifica** la app; abre su pantalla Retro, hace desplazamientos por ADB y resume métricas de tiempo de fotogramas del dibujo de la interfaz. No mide la velocidad de emulación. No se ha ejecutado en un A05s durante esta fase.

### Pendientes antes de afirmar «sin errores»
- Obtener un resultado verde comprobado de CI y una instalación de prueba real.
- Verificar toque múltiple, giro, archivo corrupto, reanudación y cambio rápido de consola en hardware.
- Medir frames p95, ANR, batería y memoria en el Galaxy A05s.
- Integrar un núcleo real de GB/GBC y después GBA con audio y guardado; **ninguna de las 16 consolas tiene núcleo integrado todavía**.
- Probar con un juego homebrew de licencia clara. No incluir ROMs comerciales ni BIOS sin autorización.

Los tests de Python son **contratos estáticos** de regresión, no una garantía de ausencia de bugs, lag o cierres inesperados.


## Fase 7 — Primer motor GB/GBC conectado al código (2026-11-03)

**Alcance:** esta fase deja programado el flujo para ejecutar Game Boy y Game Boy Color mediante un núcleo SameBoy real, todavía sujeto a compilar, probar y depurar un APK Android. No implica que haya un juego confirmado funcionando en el Galaxy A05s.

### Elementos añadidos
- `native/sameboy_bridge.c`: cargador JNI del núcleo Libretro SameBoy y adaptadores de imagen ARGB 160×144, audio PCM estéreo, cruceta y botones.
- `native/libretro.h`: únicamente las estructuras/constantes ABI Libretro necesarias, bajo licencia MIT con atribución.
- `native/CMakeLists.txt`: biblioteca nativa propia `libokubi_sameboy_bridge.so`.
- `build_sameboy.py`: obtiene una revisión **fijada** de [libretro/SameBoy](https://github.com/libretro/SameBoy/commit/aa158a889a48b538a0302873704a34577c8eb67d), la compila con Android NDK para `arm64-v8a`, `armeabi-v7a` y `x86_64`, copia `libretro.so` como `libsameboy_libretro_android.so` al proyecto y conserva su licencia.
- `SameBoyNative.java`: interfaz JNI, con detección de biblioteca ausente.
- `OkubiGameBoyActivity.java`: ventana de juego vertical/horizontal con controlador multitáctil, worker dedicado para ejecutar fotogramas, audio por `AudioTrack`, píxeles sin interpolar y ritmo de ejecución basado en FPS del núcleo.
- Guardados internos `.srm` de memoria de batería y `.rtc` de reloj de cartucho, en archivos atómicos cuyo nombre procede del SHA-256 de la ROM importada, para evitar colisiones entre juegos de igual título.
- `OkubiRetroActivity` y catálogo Pokémon: botón `Jugar dentro de OKUBI` solo para archivos GB/GBC importados, activo únicamente si está disponible la biblioteca nativa de OKUBI.
- `OkubiCatalogActivity`: el juego homebrew [Ascension](https://wolandark.itch.io/ascension) puede descargarse desde su URL publicada por el desarrollador, a almacenamiento privado y bajo límite de 8 MB; cuando el núcleo está disponible la app intenta abrirlo directamente.
- `tests/test_gb_native.py`: contratos estáticos entre JNI, actividades, ABI y build.

### Antes de dar la fase por validada
1. Ejecutar el workflow `.github/workflows/okubi-retro.yml`; revisar si las pruebas, NDK y Gradle finalizan sin errores.
2. Verificar que el APK contenga `libokubi_sameboy_bridge.so` y `libsameboy_libretro_android.so` para las arquitecturas correspondientes.
3. Instalar en el A05s y comprobar una ROM homebrew legal (Ascension) durante 15 minutos sin cuelgues, sonido cortado, botones pegados ni pérdidas de guardado.
4. Cambiar orientación con una partida en marcha, bloquear/desbloquear el equipo, volver desde multitarea, guardar y reabrir.
5. Probar ROMs con reloj de cartucho (por ejemplo Pokémon Oro/Cristal, proporcionadas legalmente por el usuario).
6. Medir consumo, rendimiento y errores con ADB. Corregir los problemas observados antes de prometer un APK final.

### Límites funcionales honestos
- Game Boy/Game Boy Color: **implementación de primera versión**, no certificada en dispositivo.
- Game Boy Advance, NES, SNES y demás: aún sin núcleo interno.
- GB/GBC: guardado de SRAM y RTC implementados a nivel de interfaz; guardados instantáneos / save states, enlaces por cable, filtros avanzados y mandos físicos quedan pendientes.
- El emulador no distribuye ROMs comerciales ni BIOS de Nintendo. SameBoy incluye alternativas de boot ROM con código abierto.
- `Ascension` puede cambiar o dejar de alojarse en la URL actual; un error HTTP no implica que el motor esté roto.
- Asegurar distribución de código y licencias de SameBoy y librerías Libretro asociadas antes de publicar una versión pública.


### Revisión posterior del núcleo SameBoy
- El fuente Libretro fijado usa una frecuencia interna de audio de hasta 384000 Hz en Android. Se corrigió el puente para reducir la frecuencia a aproximadamente 48000 Hz promediando muestras consecutivas; evita saturar el búfer de AudioTrack, pero su calidad debe validarse escuchando en hardware real.
- Se incorporó la opción `sameboy_model` para distinguir Game Boy monocromo de Game Boy Color, transmitida desde la ficha de ROM a la Activity y al núcleo.
- En CI se comprueba que el APK contenga **tanto** `libsameboy_libretro_android.so` **como** `libokubi_sameboy_bridge.so` para las tres ABI.
- El flujo `Descargar y jugar` de Ascension está previsto para Android con red, no representa una ejecución comprobada en el Galaxy A05s.


## Fase 8 — Rendimiento de biblioteca y guardado rápido (2026-11-03)

- **Exploración Android menos costosa:** `GameCatalog.reload()` limita escaneos automáticos repetidos dentro de una ventana de 15 segundos y mantiene un caché LRU de hasta 96 iconos. El botón **Reescanear** y el selector manual fuerzan una actualización inmediata. Esto evita volver a consultar todos los paquetes y volver a decodificar todos los iconos en cada retorno de un juego.
- **Game Boy / Color:** se añadieron `retro_serialize_size`, `retro_serialize` y `retro_unserialize` al puente SameBoy para crear y restaurar un punto de control completo.
- **Guardado protegido:** cada punto de control se almacena en `<SHA-256 de ROM>.state`, en el espacio privado de la aplicación con `AtomicFile`, aislado por ROM. No altera otros juegos ni requiere almacenamiento externo.
- **Seguridad:** no se aceptan snapshots de tamaño mayor que 8 MB o diferentes del tamaño que declara el núcleo. Las operaciones se ejecutan en el hilo de emulación, no en el hilo gráfico Android.
- **Interfaz:** botones **Guardar batería**, **Guardar punto** y **Cargar punto**. Los guardados `.srm` / `.rtc` existentes siguen compatibles con la versión anterior.
- **Validaciones:** `test_gb_native.py` comprueba el contrato de JNI, almacenamiento y el límite de memoria; el workflow compila las bibliotecas nativas de las tres arquitecturas.

**Sigue pendiente:** instalar la nueva build en un dispositivo real y probar que SameBoy cree y recupere correctamente un punto en juegos homebrew y GB/GBC. Un build verde no implica automáticamente que no haya cuelgues, retrasos de audio ni fallos de guardado en Android.


### Controles Bluetooth/USB
- La pantalla de juego GB/GBC admite cruceta física, botones A/B y Start/Select mediante los eventos de mando de Android; el stick izquierdo también puede controlar la dirección con una zona muerta del 45 %.
- Los estados táctiles, botones físicos y stick se combinan sin que uno anule al otro. Se limpian al suspender la aplicación o cargar un punto de control.
- Las equivalencias de botones pueden variar por fabricante y deberán probarse con mandos reales. No requiere root ni permisos especiales.


## Fase OKUBI 0.13A: audio seguro y controles a medida (2026-11-03)

### Audio Game Boy/Game Boy Color
- Volumen inicial del emulador **35 %** (independiente del volumen general del teléfono), almacenado localmente.
- Menú rápido > **Audio y volumen**: deslizante de 0-100 %, silenciar, modo audio seguro y reiniciar sonido.
- El modo seguro, activado por defecto, usa `AudioTrack.WRITE_BLOCKING` con un búfer mayor; el modo alternativo usa `WRITE_NON_BLOCKING`. La configuración se aplica al hilo de emulación mediante banderas y no toca `AudioTrack` desde el hilo UI.
- Se pausa y vacía el búfer de audio al dejar la aplicación en segundo plano; se reinicia al volver, cargar un estado o cambiar la orientación.
- El puente del núcleo sigue reduciendo audio generado a 384 kHz hacia ~48 kHz. **El pitido reportado aún requiere escucha comparativa en el Galaxy A05s** para confirmar causa/solución; la atenuación y el reinicio son medidas preventivas, no una certificación.

### Nuevos controles
- `RetroControlProfile.java`: persistencia de perfiles vertical/horizontal, escala 72-150 %, opacidad 25-100 %, modo zurdo, invertir A/B, mostrar hombros, posiciones independientes para cruceta y botones.
- `RetroTouchPad.java`: control táctil multitouch dibujado en Canvas, seis skins (**OKUBI Neon**, **Classic GBA**, **Minimal Ghost**, **Arcade Bold**, **Retro Pocket**, **Pro Touch**) y zonas táctiles al menos un 35 % mayores que el radio visible para botones estándar. El área virtual de cruceta sigue admitiendo diagonales.
- En **☰ Menú** > **Estilos y tamaño de los botones**, el usuario elige skin, escala, transparencia, modo zurdo y A/B invertidos.
- En **☰ Menú** > **Mover botones en pantalla**, arrastrar controles, luego pulsar **✓ Listo**. Se guarda el diseño solo para la orientación actual.
- En Game Boy, los botones virtuales L y R actúan como atajos de guardar/cargar punto rápido; no son botones físicos del Game Boy. Se mantiene soporte para mandos USB/Bluetooth y `.srm`, `.rtc`, `.state` anteriores.

### Selector retro y marca
- El selector ya no dibuja pantallas de juego ni mandos ficticios: muestra estado real de motor, archivos compatibles y perfil de rendimiento.
- GB/GBC primero como motores internos; GBA, NES, SNES, etc. requieren emulador externo o futuras integraciones, nunca se anuncian como jugables internamente.
- Marca OKUBI, logo de espada/mando y paleta negra-fucsia conservados. Identificador Android sin cambios para preservar los datos.
- Android versionCode incrementado a 12, versionName `0.13.0-alpha`, siguiendo el criterio de reserva de 1.0 oficial.

### Comprobación exigida antes de considerar resuelto el reporte
1. Compilación de NDK/Gradle en GitHub Actions, tests de regresión y verificación de contenido del APK.
2. En Galaxy A05s: Ascension u otro homebrew autorizado, audio original a 35 % y modo seguro; comparar con silencio y modo alternativo a volumen bajo.
3. Jugar cinco minutos con dos dedos presionando diagonal+A/B; editar controles, girar a horizontal, comprobar que perfiles/posiciones se conservan por separado.
4. Probar guardar/cargar estado, recuperación automática de cartucho, bloqueo/desbloqueo, audio tras reanudar y mando físico opcional.
5. Si persiste el pitido, registrar descripción de cuándo ocurre, volumen/auriculares y logcat sin compartir información privada; no afirmar que está corregido hasta observarlo.


## OKUBI 0.14 — Repositorio personal Pokémon (2026-11-03)

**Objetivo:** convertir la lista previa en una biblioteca desde la que el usuario pueda importar y lanzar partidas de forma directa sin volver a escoger la ROM cada vez.

- Archivo de catálogo en GitHub: `catalogs/okubi-pokemon-v1.json` contiene **43 juegos oficiales (solo nombres/metadatos)**, 6 referencias de parches, 6 aplicaciones Android verificadas y 2 demos originales de captura de criaturas.
- `OkubiPokemonRepository.java`: índice local privado de hasta 250 archivos seleccionados con Storage Access Framework. Almacena referencias persistentes a los documentos, nombre, consola, favoritos y última partida; no copia ROMs ni las sube a servidores.
- `OkubiPokemonActivity.java`: secciones **Míos**, **Clásicos**, **ROM hacks**, **Gratis**, **Android**, con búsqueda, filtros por consola, importación múltiple de hasta 30 archivos por selección y acceso a «Continuar jugando».
- Los juegos GB/GBC importados y validados se lanzan dentro de SameBoy; GBA y NDS solicitan un emulador externo; 3DS/Switch no se anuncian como jugables.
- Mantiene los archivos importados anteriormente mediante `entry_official_*` y `entry_hack_*`; no cambia el directorio de guardados `.srm`, `.rtc`, `.state`.
- Los ROM hacks son parches independientes y requieren el juego base compatible. **Nunca** se distribuyen ROMs comerciales pre-parcheadas.
- Demos gratuitas y originales publicadas por sus creadores:
  - [Monpact Demo](https://retrokuro.itch.io/monpact), RPG de captura de criaturas para Game Boy Color.
  - [PachiPals Demo](https://rebusmind.itch.io/pachipals-demo-gbc), captura de criaturas y pinball para Game Boy Color.
  El botón de OKUBI abre la página del creador. El usuario descarga el `.gbc` allí y lo importa con «Importar varios».
- Seis Pokémon Android oficiales enlazan a sus fichas de Google Play o se abren si ya están instalados (según visibilidad de Android): Champions, GO, Masters EX, UNITE, Café ReMix y Quest. La disponibilidad varía por país y teléfono.

**Limitación legal:** el catálogo no contiene archivos de juegos oficiales, ROMs comerciales, carátulas protegidas, enlaces a copias de terceros ni descargas de hacks pre-parcheados. Se importan únicamente archivos que el usuario puede utilizar legalmente. Los juegos homebrew tienen sus propias licencias y condiciones de autor.

**Pruebas pendientes en Galaxy A05s:** elegir múltiples `.gb` / `.gbc`, suspender/reabrir OKUBI, jugar, guardar y cargar partida, comprobar que favoritos y reciente persisten y que el archivo sigue accesible tras reiniciar Android. La compilación por sí sola no garantiza el funcionamiento en el dispositivo.


## OKUBI 0.15 — Tus copias Pokémon y formatos de archivo (2026-11-03)

### Qué se reconoce y qué se ejecuta
- **GB (`.gb`) y GBC (`.gbc`)**: motor interno SameBoy; ZIP con ROM GB/GBC dentro también funciona sin extraerlo en una carpeta. Lee hasta 8 MiB de ROM descomprimida; el guardado conserva el SHA-256 de los bytes reales del cartucho, de modo que un juego importado como ZIP usa el mismo guardado que la ROM suelta.
- **GBA (`.gba`) y Nintendo DS (`.nds`, `.srl`)**: catálogo local y apertura mediante un emulador externo instalado; el motor integrado **no** ejecuta estas consolas. melonDS soporta archivos DS en ZIP/7z mediante su integración pública; otros emuladores externos pueden requerir ROM descomprimida.
- **Nintendo 64 (`.z64`, `.n64`, `.v64`)**: biblioteca y selector de emulador externo; sin motor interno.
- **GameCube (`.gcm`), GameCube/Wii (`.rvz`), Wii (`.wbfs`, `.wia`), Wii U (`.wud`, `.wux`, `.wua`), 3DS (`.3ds`, `.cci`, `.cia`, `.cxi`) y Switch (`.nsp`, `.xci`, `.nsz`, `.xcz`)**: reconocimiento para catalogar copias propias, **no se promete emulación en Galaxy A05s**.
- **ISO (`.iso`)**: solo se cataloga, porque esa extensión es ambigua entre varias consolas.
- **ZIP (`.zip`)**: detecta el primer archivo con extensión reconocida dentro del ZIP; para GB/GBC ejecuta la ROM desde memoria, sin extraer archivos ni crear rutas inseguras. Si contiene varias ROMs, conviene separarlas en archivos individuales para evitar ambigüedad.
- **7z/RAR/TAR/GZ**: no hay extractor integrado, por lo que OKUBI pide extraer la ROM con un administrador de archivos. No afirma reconocer internamente el contenido.
- **IPS/BPS**: parcheador local integrado, desde `Pokémon → ROM hacks → Aplicar parche IPS / BPS a mi copia`. Elige ROM base GB/GBC/GBA sin comprimir, parche de su creador y destino a través del selector Android. BPS verifica checksums del original, del parche y del resultado. IPS carece de comprobación de ROM base, por lo que exige usar la versión correcta.
- **UPS**: requiere parcheador externo. **.sav/.srm/.state** no son juegos; las partidas GB/GBC previas se mantienen en archivos privados indexados por SHA-256 de cada ROM.

### Protección y pruebas
- Importación vía Storage Access Framework, sin leer libremente el almacenamiento del teléfono. Solo se guardan URI de documentos y metadatos; no se sube ninguna ROM al repositorio ni a terceros.
- `OkubiRomFormats.java` usa `ZipInputStream`, máximo 64 entradas por ZIP, 1 MiB de contenido adicional ignorado por entrada, ROM GB/GBC de 8 MiB como máximo. No extrae archivos a rutas arbitrarias.
- `OkubiPatchEngine.java` limita archivos fuente y resultado a 32 MiB; IPS rechaza offsets fuera de rango, BPS valida límites, tamaño y CRC32 del parche, fuente y destino.
- Pruebas sintéticas de los algoritmos BPS/IPS en Java en `retro-module/tests/java`, además de tests Python y compilación NDK/Android.
- Matriz completa editable: `catalogs/rom-format-compat-0.15.json`.

**Advertencia:** reconocer el formato del archivo no garantiza que su contenido sea un juego válido ni que pueda funcionar en el teléfono. Se requiere probar en Galaxy A05s con archivos propios que el usuario tenga derecho a utilizar. Nunca se distribuyen juegos comerciales o claves/BIOS protegidas.


### Importar un guardado de cartucho que ya tienes
En una partida Game Boy o Game Boy Color abierta dentro de OKUBI, entra en **☰ Menú → Importar partida .sav / .srm**, elige un archivo propio y deja que el núcleo valide su tamaño. Si coincide con la memoria del cartucho, el progreso se restaura y se guarda de forma atómica bajo el identificador SHA-256 de la ROM. No admite guardados de GBA, DS u otras consolas; sus emuladores externos manejan esos archivos por separado. Los archivos `.state` no se deben confundir con los `.sav` normales.

**Recomendación:** respalda el progreso actual antes de importar otro guardado, porque importar SRAM reemplaza la partida de batería activa para ese cartucho. Los otros juegos conservan sus partidas separadas.


## OKUBI 0.16 — Motor Game Boy Advance integrado

**Objetivo:** jugar ROMs personales `.gba`, incluidas dentro de un ZIP, dentro de OKUBI, sin salir a un emulador externo.

- Núcleo **mGBA Libretro**, upstream `libretro/mgba` fijado al commit `7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6` (licencia MPL-2.0). El proceso `retro-module/build_mgba.py` lo compila **desde el código fuente** para `arm64-v8a`, `armeabi-v7a` y `x86_64`; la APK debe contener `libmgba_libretro_android.so` y `assets/licenses/mGBA-LICENSE.txt`.
- El puente Libretro de OKUBI carga **SameBoy** para GB/GBC y **mGBA** para GBA, exclusivamente uno a la vez. El reproductor adapta el área de vídeo a 160×144 o 240×160, mantiene la salida de audio y no ejecuta llamadas del núcleo desde el hilo de interfaz.
- En GBA, L y R del mando táctil y de mandos Bluetooth/USB sirven como botones reales de GBA. Guardar/cargar punto permanece en el menú rápido; en GB/GBC, L/R virtuales siguen siendo atajos de guardado/carga.
- Las partidas GBA se guardan en `getFilesDir()/okubi/gba/<SHA-256 de ROM>.srm` y los estados rápidos en `.state`; SameBoy conserva `okubi/gameboy/` y sus guardados anteriores. El original de la ROM nunca se modifica.
- Las fuentes de ROM usan Android Storage Access Framework (no lectura global del almacenamiento): ficheros `.gba` sueltos y dentro de `.zip` con límite de 32 MiB descomprimidos. Los archivos `.7z` y `.rar` todavía requieren extracción previa.
- La aplicación muestra GBA como listo **solo si realmente está disponible la biblioteca mGBA**. Si no está incluida o Android no puede cargarla, ofrece usar un emulador externo.
- mGBA es libre, pero **no se incluyen ROMs comerciales ni la BIOS de Nintendo**. La BIOS de GBA no es obligatoria para las configuraciones habituales de mGBA.
- Pruebas: el workflow compila SameBoy y mGBA para 3 arquitecturas y verifica ambas bibliotecas y la licencia dentro de la APK, además de tests unitarios de contratos. **Todavía se requiere jugar en el Samsung Galaxy A05s** para medir FPS, audio, batería, uso de memoria, guardado, reanudación y rotación.


## OKUBI 0.16.1 — Edición personal con cartuchos de usuario

- La APK base compilada en GitHub **NO incluye** ROMs comerciales ni archivos del usuario. El workflow lo verifica y falla si detecta un archivo bajo `assets/personal-roms/`.
- `OkubiPersonalLibrary.java` contiene únicamente los **nombres de dos rutas de activos opcionales**, no bytes de los cartuchos.
- Una edición local opcional, generada fuera de GitHub con archivos que el jugador ha aportado, puede incluir:
  - `assets/personal-roms/pokemon-rojo-fuego.gba`
  - `assets/personal-roms/pokemon-esmeralda.gba`
- En Pokémon → **Míos** aparece la sección «Mis cartuchos incluidos» solo si esos activos existen. Los botones «Jugar ahora» los ejecutan directamente con mGBA, sin volver a pedir un archivo ni conectarse a internet.
- `OkubiGameBoyActivity` valida el nombre frente a una lista permitida, abre el activo internamente, limita la lectura a 32 MiB y utiliza los guardados normales de mGBA, separados por el SHA-256 de cada cartucho. No manda ROMs a GitHub ni a un servidor.
- La variante personal debe firmarse **después** de incluir los archivos, con una clave local que no se sube al repositorio. Por ello la firma no coincidirá con la APK de depuración de GitHub: **hacer copia de las partidas antes de desinstalar o sustituir la APK anterior**.
- La APK personalizada es un artefacto privado exclusivamente para quien aportó las copias; no redistribuir esa APK a terceros.
- Se requiere prueba física en Galaxy A05s de instalación, arranque de ambos juegos, guardado, carga, reinicio y rotación.


## OKUBI 0.16.3 — Descargas directas desde Android

- **Descubre → Nintendo / Descargas y ROM hacks** ofrece un botón «↓ Descargar parche .bps» para tres parches gratuitos **publicados por sus traductores**: Radical Red Español v0.992, Emerald Rogue Español v0.66 y Ghost Grey Español v0.992. Son URLs de publicación fijadas a una versión conocida, no ROMs completas.
- Cada botón entrega la URL HTTPS original a **DownloadManager** de Android y muestra su notificación. Los archivos se guardan en **Descargas** en Android 10+, con nombre identificable y sufijo único; en Android 8 y 9 se guardan en la carpeta externa privada de la aplicación porque no hay permisos globales de almacenamiento.
- Se comprueba la procedencia `github.com/SeCaVa/.../releases/download/` y extensión `.bps`. Los botones solo aceptan objetos de la lista revisada. La gestión de estados impide descargar dos veces simultáneamente el mismo parche. Estos archivos pueden tener decenas de MB y requieren conexión.
- **IMPORTANTE:** los tres parches requieren ROM de base **inglesa USA**, no las copias **españolas** del usuario. FireRed USA v1.0 CRC32 `DD88761C` para Radical Red y Ghost Grey; Emerald USA CRC32 `1F1C08FB` para Emerald Rogue. El parcheador BPS de OKUBI verifica el CRC32 de la ROM de base y rechaza versiones incompatibles.
- Tras descargar, el usuario abre Pokémon → ROM hacks → «Aplicar parche IPS / BPS a mi copia»; los juegos base no se descargan, incluyen ni distribuyen.
- Los tres sitios aportados por el usuario (Whack a Hack, PokéMundo, Full GBA ROMs) permanecen como **catálogos externos**, porque algunos llevan a juegos comerciales pre-parcheados o enlaces intermediarios cuya licencia y URL directa no puede garantizarse. **Ningún botón de OKUBI descarga automáticamente ROMs comerciales**.
- Ascension sigue siendo la descarga original libre con lanzamiento interno GB.
- Pendiente: probar en Galaxy A05s los destinos DownloadManager, los estados de descarga, importación de los parches BPS y la compatibilidad de instalación con firmas de depuración anteriores.
