# OKUBI UNIFIED · local Windows release keystore creation
# Run by the owner on their Windows computer. NEVER upload the .p12 file.
# The keytool prompt collects passwords interactively (not in commands/history).
[CmdletBinding()]
param(
    [string]$KeyFolder = (Join-Path $env:USERPROFILE 'OKUBI-Release-Keys'),
    [string]$Alias = 'okubi-unified'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if ($null -eq $keytool) {
    throw 'No se encontró keytool. Instala un JDK 17 o superior y agrega su carpeta bin a PATH.'
}
if ($Alias -notmatch '^[A-Za-z0-9_-]{3,60}$') {
    throw 'Alias inválido.'
}
New-Item -ItemType Directory -Path $KeyFolder -Force | Out-Null
$target = Join-Path $KeyFolder 'okubi-unified-release.p12'
if (Test-Path -LiteralPath $target) {
    throw "Ya existe la keystore: $target. NO reemplaces una clave de release existente."
}
Write-Host 'Creando la única clave oficial de OKUBI en tu ordenador.'
Write-Host 'La contraseña se solicita por keytool. No se guarda en este script.'
Write-Host 'Guarda la keystore y su contraseña en dos ubicaciones privadas y seguras.'
& $keytool.Source -genkeypair -keystore $target -storetype PKCS12 -alias $Alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=OKUBI UNIFIED, OU=Personal, O=OKUBI, C=BO'
if ($LASTEXITCODE -ne 0) {
    throw 'La creación de la clave no terminó correctamente. Revisa keytool.'
}
if (!(Test-Path -LiteralPath $target)) {
    throw 'No se creó la keystore.'
}
Write-Host ''
Write-Host 'Clave de release creada en:' $target
Write-Host 'No la publiques, no la adjuntes al chat y no la subas a GitHub.'
Write-Host ''
Write-Host 'Para ver la huella pública SHA-256 (sin mostrar la clave privada), ejecuta:'
Write-Host ('keytool -list -v -keystore "{0}" -alias {1}' -f $target,$Alias)
Write-Host ''
Write-Host 'La huella SHA256 pública deberá registrarse como variable de GitHub:'
Write-Host 'OKUBI_RELEASE_CERT_SHA256'
Write-Host 'Los cuatro secretos de firma se configuran por separado en GitHub.'
Write-Host 'Después podrás lanzar el workflow MANUAL desde main, cuando el PR esté aprobado.'
