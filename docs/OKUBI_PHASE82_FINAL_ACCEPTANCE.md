# OKUBI 遊 — Fase 82 · Aceptación final de los dos paquetes

**Estado: solo QA. No se ha publicado una versión estable ni fusionado a `main`.**

## Objetivo

Unir las evidencias de las pruebas automáticas, sin confundir un PASS de
compilación con una autorización para publicar o actualizar la instalación
existente. La Fase 82 ejecuta una auditoría de **artefactos completos reales**:

| Paquete | Run aprobado | Qué se revisa |
| --- | --- | --- |
| Windows 81B, núcleos desde código fuente | [43615378972](https://github.com/Cobybm/okubi-windows/actions/runs/43615378972) | SHA-256 del ZIP; tres DLL y sus hashes; manifiesto DLL→fuente Git; scripts y licencias; lanzador aislado |
| Android QA57 instalado en paralelo | [43612388104](https://github.com/Cobybm/okubi-windows/actions/runs/43612388104) | SHA-256 del APK; validación criptográfica con `apksigner`; nombre de paquete QA separado; 12 núcleos en 4 arquitecturas; licencias |
| Cross Save sintético | [43605014667](https://github.com/Cobybm/okubi-windows/actions/runs/43605014667) | Evidencia de las pruebas sintéticas anteriores; **no implica** intercambio físico en dispositivos |

Los archivos se descargan solamente en un runner de GitHub Actions y se
inspeccionan en modo **solo lectura**. Ninguna comprobación instala una APK,
ejecuta juegos, modifica datos personales ni utiliza claves privadas.

Script: `tools/okubi_phase82_final_audit.py`.

Workflow: `.github/workflows/okubi-phase82-acceptance.yml`.

Registro de puertas pendientes:
`tools/okubi_phase82_release_evidence.json`.

## Criterio Go/No-Go de OKUBI 1.0

Estas **seis evidencias siguen pendientes** en el registro inicial:

- [ ] Windows en dispositivo real: Inicio/Juegos/Descubre, CINEMA, controles, audio, pantalla completa y guardados nuevos.
- [ ] Android en dispositivo real: A05s o equivalente, cruceta multitáctil, orientación, controles, sonido y guardados, sin tocar el paquete canónico.
- [ ] Cross Save **físico** Windows ↔ Android con archivo de prueba y respaldo, sin sustituir partidas reales.
- [ ] Firma original de Android y posibilidad de actualización compatibles: verificar en privado certificado y `versionCode`; QA57 con identificador separado no es una actualización canónica.
- [ ] Revisión de las obligaciones de licencias, avisos, documentación, fuentes correspondientes y dependencias de **toda** la distribución (MIT/MPL/GPL y componentes adicionales).
- [ ] Autorización expresa del propietario para publicar la versión estable, **independiente** de autorizar un futuro merge.

### Firma Android original

La tarea de GitHub **no conoce la firma de la APK canónica** ni debe
conocer la clave privada. La inspección local no destructiva puede hacerse
en Windows con las herramientas Android SDK:

```powershell
apksigner verify --verbose --print-certs "OKUBI-original.apk"
aapt dump badging "OKUBI-original.apk"
```

Se debe comparar una futura APK con el *mismo applicationId* y certificado
de actualización, y un `versionCode` mayor, usando el verificador del
repositorio `tools/okubi_release_guard.py` con la huella **pública** del
certificado original. Nunca subir contraseñas, keystore, APKs personales,
ROMs ni partidas al repositorio.

### Pruebas Windows

El ZIP de Fase 81B debe usarse en carpeta **nueva** y ejecutarse solamente
por `OKUBI Desktop\app\RUN-QA57-ISOLATED.cmd`. Los guardados existentes
permanecen fuera de estas pruebas. Es recomendable respaldar datos antes
de cualquier prueba física y utilizar únicamente juegos propios de prueba.

### Política de publicación

El flag `--require-release` **devuelve 2 (rechazado)** incluso cuando
pasan los dos archivos de QA, porque faltan pruebas manuales y revisión
de licencias/firma. Esa es una prueba negativa intencional; el workflow
considera FAIL si la publicación fuese permitida.

El informe JSON contiene únicamente hashes, condiciones automáticas y
nombres genéricos de evidencias pendientes. Las salidas de `apksigner`,
las APKs, ZIPs, claves o rutas de usuario **no se re-suben** dentro del
informe público.

La Fase 82 no altera `main`, la app Android original, el repositorio
privado previo ni VIDAS. El siguiente objetivo ya no es sumar funciones:
**resolver las seis evidencias sin perder partidas** y decidir si puede
autorizarse OKUBI 1.0.
