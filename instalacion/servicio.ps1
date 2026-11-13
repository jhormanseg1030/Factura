# Instala Factura Zona K como servicio de Windows. No abre una ventana de CMD.
# Ejecutar como administrador: servicio.bat
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

function Test-Administrador {
    $identidad = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identidad)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Find-Java {
    $comando = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($comando -and $comando.Source) {
        return $comando.Source
    }
    $carpetas = @(
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Zulu",
        "$env:ProgramFiles\Amazon Corretto"
    )
    foreach ($carpeta in $carpetas) {
        if (-not (Test-Path -LiteralPath $carpeta)) {
            continue
        }
        $hallado = Get-ChildItem -LiteralPath $carpeta -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
            Select-Object -First 1
        if ($hallado) {
            return $hallado.FullName
        }
    }
    return $null
}

function Escape-Xml([string]$texto) {
    return $texto.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;')
}

if (-not (Test-Administrador)) {
    Write-Error 'Ejecuta servicio.bat con clic derecho y "Ejecutar como administrador".'
}

$jar = Get-ChildItem -LiteralPath $root -Filter 'factura-*.jar' | Select-Object -First 1
if (-not $jar) {
    Write-Error 'No esta el JAR en la carpeta instalacion.'
}

$java = Find-Java
if (-not $java) {
    Write-Error 'No se encontro java.exe. Instala Java 21 y vuelve a ejecutar servicio.bat.'
}

New-Item -ItemType Directory -Force -Path (Join-Path $root 'logs') | Out-Null

$exe = Join-Path $root 'FacturaZonaK.exe'
if (-not (Test-Path -LiteralPath $exe)) {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $url = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe'
    Write-Host 'Descargando el programa que registra el servicio...'
    Invoke-WebRequest -Uri $url -OutFile $exe
}

$xml = @"
<service>
  <id>FacturaZonaK</id>
  <name>Factura Zona K</name>
  <description>Facturacion electronica del punto de venta. Los errores quedan en logs\errores.txt.</description>
  <executable>$(Escape-Xml $java)</executable>
  <arguments>-Dfile.encoding=UTF-8 -jar "$(Escape-Xml $jar.FullName)"</arguments>
  <workingdirectory>$(Escape-Xml $root)</workingdirectory>
  <log mode="none"/>
  <startmode>Automatic</startmode>
  <delayedAutoStart>true</delayedAutoStart>
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="30 sec"/>
  <onfailure action="none"/>
</service>
"@
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Join-Path $root 'FacturaZonaK.xml'), $xml, $utf8)

Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -like '*factura-*.jar*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$servicio = Get-Service -Name FacturaZonaK -ErrorAction SilentlyContinue
if ($servicio) {
    if ($servicio.Status -eq 'Running') {
        & $exe stop
    }
    & $exe uninstall
    Start-Sleep -Seconds 2
}

& $exe install
if ($LASTEXITCODE -ne 0) {
    Write-Error 'No se pudo instalar el servicio.'
}
& $exe start
if ($LASTEXITCODE -ne 0) {
    Write-Error 'El servicio quedo instalado, pero no arranco. Revisa instalacion\logs\errores.txt.'
}

Write-Host ''
Write-Host 'El servicio Factura Zona K quedo en inicio automatico.'
Write-Host 'No hace falta dejar abierta una ventana de CMD.'
Write-Host "Los errores del dia se guardan en $root\logs\errores.txt y se borran al cambiar de fecha."
