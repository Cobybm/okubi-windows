# OKUBI 遊 - rendimiento y estabilidad (Windows + ADB)
# Requiere un APK de depuración instalado y depuración USB autorizada.
# Este script NO instala nada, no requiere root y no modifica opciones del teléfono.

param([string]$Package = "bo.nexoplay.app")

$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "ADB no está disponible en PATH. Instala Android Platform Tools."
}
$devices = & adb devices
$ready = @($devices | Where-Object { $_ -match '^\S+\s+device$' })
if ($ready.Count -ne 1) {
    throw "Conecta exactamente un dispositivo Android autorizado por ADB. Estado: $($devices -join '; ')"
}

Write-Host "Lanzando OKUBI Retro sin instalar o modificar otros archivos..."
& adb shell am start -W -n "$Package/bo.nexoplay.app.retro.OkubiRetroActivity" | Out-Host
if ($LASTEXITCODE -ne 0) { throw "No fue posible abrir la pantalla retro; verifica el APK." }

Start-Sleep -Seconds 2
& adb shell dumpsys gfxinfo $Package reset | Out-Null

# Ejercita el desplazamiento vertical del catálogo; no abre juegos.
for ($i = 0; $i -lt 6; $i++) {
    & adb shell input swipe 420 1450 420 460 330 | Out-Null
    Start-Sleep -Milliseconds 180
}
$raw = & adb shell dumpsys gfxinfo $Package framestats
$inside = $false
$durations = New-Object 'System.Collections.Generic.List[double]'
foreach ($line in $raw) {
    if ($line -match '^---PROFILEDATA---') {
        if ($inside) { break }
        $inside = $true
        continue
    }
    if (-not $inside -or $line -match '^Flags,' -or $line -notmatch '^[0-9]+,') { continue }
    $fields = $line.Split(',')
    if ($fields.Count -lt 14) { continue }
    $intended = [long]0
    $completed = [long]0
    if ([long]::TryParse($fields[1], [ref]$intended) -and
        [long]::TryParse($fields[13], [ref]$completed) -and
        $completed -gt $intended) {
        $durations.Add(($completed - $intended) / 1000000.0)
    }
}

if ($durations.Count -eq 0) {
    Write-Warning "Sin framestats suficientes. Repite el desplazamiento manual y ejecuta otra vez."
} else {
    $sorted = @($durations | Sort-Object)
    $idx95 = [Math]::Min($sorted.Count - 1, [Math]::Floor(($sorted.Count - 1) * 0.95))
    $slow = @($durations | Where-Object { $_ -gt 33.3 }).Count
    $frozen = @($durations | Where-Object { $_ -gt 700 }).Count
    $percent = [Math]::Round(100.0 * $slow / $durations.Count, 1)
    Write-Host ("Frames: {0}; p95: {1:N1} ms; >33.3ms: {2}% ; >700ms: {3}" -f
            $durations.Count, $sorted[$idx95], $percent, $frozen)
    Write-Host "Nota: medición de la UI únicamente; NO mide velocidad ni FPS de la emulación."
}

Write-Host "Para revisar fallos de cierre, usa: adb logcat -d -s AndroidRuntime:E ActivityManager:E"
