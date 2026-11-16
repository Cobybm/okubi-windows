# OKUBI 遊 — Requisitos de controles automáticos

## Objetivo obligatorio (Windows y Android)
- Detectar teclado, mandos USB y Bluetooth mediante los mecanismos soportados del sistema operativo y del motor de emulación. No prometer soporte universal de cualquier dispositivo.
- Mapear botones por consola/núcleo, no usar un único mapa indiferenciado.
- Sin mando conectado, activar perfil de teclado y mostrar accesos relevantes.
- Primera apertura por consola: guía no intrusiva de controles efectivos con acciones, teclas y botones del dispositivo activo; debe poder cerrarse, mostrarse de nuevo desde pausa y consultarse por juego.
- Reasignar teclas y botones, sensibilidad/deadzone cuando corresponda, perfiles por consola y por juego, recuperación por defecto.
- Detectar conexión/desconexión en caliente; cambiar perfiles sin perder progreso, pausar ante desconexión cuando sea apropiado.
- Resolver conflictos de teclas, controles ocupados, asignaciones vacías, múltiples mandos, soporte de accesibilidad.
- Conservar configuraciones actuales y partidas durante migraciones; no sobrescribir bindings personalizados.

## Orden del trabajo
1. Inspeccionar implementaciones actuales Windows/Android y documentar qué motores y dispositivos están soportados.
2. Incorporar inventario de entradas y perfiles por sistema; adaptadores para libretro y otros motores según API disponible.
3. Tutorial primera apertura basado en bindings reales, no en teclas inventadas.
4. Pantalla Configuración > Controles y vista de controles desde pausa/ficha de juego.
5. Probar teclado, mandos, reconexión, dos dispositivos, configuraciones persistidas, partidas.
6. Integrar con biblioteca y detectar automáticamente consola y emulador disponible al importar juegos.

## Aceptación
No considerar implementado hasta probar en ejecutables Android y Windows. Catalogar por separado el estado de los núcleos y de las plataformas.
