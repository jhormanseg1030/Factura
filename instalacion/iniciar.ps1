# Arranque manual, con ventana. El uso normal es el servicio: servicio.bat como administrador.
# Los errores quedan en logs\errores.txt.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

$jar = Get-ChildItem -LiteralPath $root -Filter 'factura-*.jar' | Select-Object -First 1
if (-not $jar) {
    Write-Error 'No esta el JAR en esta carpeta.'
}

java -jar $jar.FullName @args
