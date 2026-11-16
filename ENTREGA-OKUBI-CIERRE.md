# OKUBI 遊 — Entrega de cierre (versiones de prueba)

Este documento reúne las entregas actuales. **No se crean más fases**. No usar la palabra “estable” hasta completar las pruebas indicadas.

## Windows x64

- La aplicación de escritorio soporta importación de ROMs propias GB/GBC/GBA/SNES y reproduce música del juego mientras se acelera (F6).
- El panel multimedia (F8) controla reproducción externa y el volumen del juego de manera separada.
- El instalador de cierre `OKUBI-Windows-Cierre-QA.exe` debe provenir del artifact `OKUBI-CIERRE-WINDOWS-QA-INSTALLER-NOT-STABLE` y estar acompañado de `SHA256SUMS.txt`.
- Solo usar cuando GitHub Actions haya aprobado **compilación Java y JNI, integridad de cores, instalación en Windows, arranque real, manipulación detectada y desinstalación**.
- El instalador continúa el perfil de ensayo QA86 (sin migrar ni tocar la instalación habitual y sus partidas). **No ejecutar dos versiones QA86 en paralelo usando la misma partida**.
- Archivo de prueba anterior: `OKUBI-Windows-Instalar-QA89.exe`. No confundir con el instalador consolidado de cierre.
- El instalador no está firmado con certificado de distribución público; Windows SmartScreen puede mostrar advertencia. Verificar el origen y el SHA-256 antes de ejecutarlo.

## Android (Android 8 / API 26 en adelante)

- APK universal de prueba: `OKUBI-Android-QA93-Universal.apk`, **solo después de GitHub Actions SUCCESS** en compilación, integridad y prueba de instalación.
- Identificador aislado: `bo.nexoplay.app.unified.qa93`. No elimina ni reemplaza QA57, QA88, QA89 o la app canónica.
- ABIs verificadas antes de empaquetar: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- Se incluyen núcleos compilados desde las fuentes fijadas de SameBoy, mGBA y Supafaust con sus licencias. **No se distribuyen juegos, BIOS ni archivos de guardado**.
- El motor ofrece velocidades ×0.5, ×1, ×1.25, ×1.5, ×2, ×3, ×4. El audio del juego conserva su música con la velocidad; se corrige el búfer para ×0.5 y la cola de audio estéreo.
- Las diferentes variantes QA usan espacio privado **separado**: no traen automáticamente las partidas de otra variante. Conservar el APK anterior y usar exportar/importar copia mediante el menú de guardados cuando corresponda.
- No se ha validado universalmente “cualquier móvil Android”: aunque se soporten estas 4 arquitecturas y SDK 26+, la compatibilidad real depende del dispositivo, versión y rendimiento.
- Android 15 (API 35) exige prueba de instalación completa; una compilación exitosa no equivale a aprobación de arranque.
- Firmas de depuración de CI no ofrecen actualizaciones estables entre compilaciones. La publicación definitiva exige firma del propietario.

## Protección y estado

1. **No desinstalar la aplicación anterior** ni borrar su almacenamiento mientras haya partidas valiosas.
2. Descargar exclusivamente desde los artefactos de las ejecuciones de GitHub de la rama de cierre, verificar `SHA256SUMS.txt` y hacer copia de partidas antes de probar.
3. No fusionar PR pendientes ni marcar versión estable hasta que la compilación consolidada pase y se pruebe con hardware real.
4. El proyecto VIDAS y el repositorio histórico de OKUBI permanecen completamente separados.

## Comprobación SHA-256 en Windows

En PowerShell, dentro de la carpeta con el archivo:

```powershell
(Get-FileHash .\OKUBI-Windows-Cierre-QA.exe -Algorithm SHA256).Hash.ToLowerInvariant()
```

Comparar el resultado con la línea correspondiente del `SHA256SUMS.txt`. Si no coincide, **no instalar**.
