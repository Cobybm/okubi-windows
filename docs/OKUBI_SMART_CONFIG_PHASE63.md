# OKUBI 遊 — Fase 63 · SMART CONFIG PS2 (Windows 1.0.11)

**Estado: beta de pruebas.** Esta fase añade un ajuste gráfico automático
*real*, pero acotado, reversible y **voluntario** para PCSX2.

## Diferencia con la Fase 62

SMART PLAY ya aprende si cada juego funcionó o no en una determinada PC.
SMART CONFIG ahora puede utilizar la recomendación de rendimiento para
**establecer automáticamente la resolución interna de un juego PS2**:

| Perfil y evaluación | Ajuste aplicado a PCSX2 |
|---|---|
| Automático + Intel UHD 730 o juego lento | 1× (resolución nativa) |
| Protección/ECO | 1× |
| Equilibrado | 1×, conservador |
| Calidad + hardware calificado como probable | 2× |
| PS1 / PS3 | Sin cambios internos. Quedan fuera de la Fase 63 |

El máximo **2×** es una política conservadora, no una garantía de rendimiento.
No activa speedhacks, overclock ni ajustes de CPU, BIOS, firmware o GPU.

## ¿Cómo se activa?

1. Abre **SMART PC** desde el panel lateral o Ctrl+P.
2. Importa un juego PS2 propio y configura **PCSX2 oficial**.
3. Selecciona el juego y pulsa **SMART CONFIG PS2 · INACTIVO (activar)**.
4. OKUBI explica los efectos y pide consentimiento explícito **para ese
   juego en esa computadora**. Si aceptas, mostrará **ACTIVADO**.
5. Al pulsar JUGAR, OKUBI crea un archivo de ajustes gráfico mínimo
   dentro de su propio perfil y pasa al PCSX2 la opción documentada
   `-gamecfg <ruta.ini>`. PCSX2 puede leerla solo en ese arranque.
6. Si notas errores, pulsa **SMART CONFIG PS2 · ACTIVADO (desactivar)**.
   La siguiente sesión se abrirá sin `-gamecfg`, recuperando así el
   comportamiento habitual de PCSX2. Si el juego está abierto, cierra
   la sesión antes de comprobar el cambio.

**Aviso importante:** `-gamecfg` sustituye temporalmente los ajustes
individuales del título que PCSX2 cargaría normalmente. Aunque OKUBI
no sobrescribe ni elimina esos archivos originales, opciones especiales
de corrección, gráficos u otras preferencias por juego podrían no
aplicarse mientras SMART CONFIG está activo. Para títulos con ajustes
personalizados importantes, déjalo desactivado.

## Protección de datos

- **Desactivado por defecto.** No crea archivos de perfil hasta que
  el usuario aprueba el cambio para ese juego y hardware.
- Los perfiles se guardan como ficheros `.ini` inmutables gestionados
  por OKUBI dentro de `%APPDATA%\OKUBI\smart-config-v1\profiles`.
  La preferencia de activación está en `smart-config-pcsx2-v1.properties`.
- Cada perfil usa un identificador SHA-256 derivado de consola,
  ubicación del juego y características de PC. No guarda rutas de
  ROMs en el archivo de consentimiento, BIOS, claves ni partidas.
- No se editan los archivos globales de PCSX2, juegos, ISO/CHD, carpetas
  de tarjetas de memoria, save states, el registro de Windows ni
  configuraciones de DuckStation/RPCS3.
- Si alguien modifica un archivo de perfil gestionado por OKUBI, su
  siguiente uso se rechaza; **no se sobrescribe** automáticamente.
- Si actualizas procesador o GPU, SMART CONFIG vuelve a estar
  desactivado para ese hardware hasta nuevo consentimiento.
- Si usas `app\RUN-QA57-ISOLATED.cmd`, los archivos SMART CONFIG de
  OKUBI se guardan bajo el perfil QA, separados del perfil normal.
  El emulador externo PCSX2 conserva sus rutas habituales para
  BIOS, tarjetas y saves, ya que este comando no cambia su directorio
  de datos: utiliza solo partidas **de prueba** al evaluar cambios.

## ¿Qué se ha verificado?

GitHub Actions debe comprobar con datos sintéticos: consentimiento por
juego/PC, generación 1×/2×, flags `-gamecfg` y `--` separados,
conservación de rutas con espacios, rechazo de 0×/4×, perfil alterado,
activación/desactivación persistente, aislamiento entre GPU diferentes
e integridad byte a byte de las tarjetas de memoria, ROM y archivo
global de PCSX2. Se preservan las pruebas Windows de todas las
fases anteriores y el verificador del ZIP/EXE real.

**No significa que PCSX2 haya sido probado físicamente en tu PC ni
que se garanticen FPS.** La validación visual y una partida de prueba
real siguen pendientes antes de declarar el sistema estable.

### Fuentes técnicas

- PCSX2, Command Line Options (actual): https://pcsx2.net/docs/advanced/cli/
- PCSX2, configuración por juego: https://pcsx2.net/docs/configuration/general/
- PCSX2, esquema `[EmuCore/GS]` y `upscale_multiplier`:
  https://github.com/PCSX2/pcsx2/blob/master/pcsx2/Config.h

Android y VIDAS permanecen intactos; **PR DRAFT, sin fusionar main**.
