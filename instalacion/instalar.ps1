# Elige el puerto de esta app y muestra la URL que hay que pegar en Harmony.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$configDir = Join-Path $root 'config'
$configFile = Join-Path $configDir 'harmony.properties'
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

function Read-Puerto([int]$puertoActual) {
    while ($true) {
        $raw = Read-Host "Puerto de esta aplicacion [$puertoActual]"
        if ([string]::IsNullOrWhiteSpace($raw)) {
            $raw = "$puertoActual"
        }
        $puerto = 0
        if (-not [int]::TryParse($raw, [ref]$puerto) -or $puerto -lt 1 -or $puerto -gt 65535) {
            Write-Host 'Escribe un puerto entre 1 y 65535.'
            continue
        }
        if ($puerto -ne $puertoActual) {
            $enUso = Get-NetTCPConnection -LocalPort $puerto -ErrorAction SilentlyContinue
            if ($enUso) {
                Write-Host "El puerto $puerto ya esta en uso. Elige otro."
                continue
            }
        }
        return $puerto
    }
}

function Get-HostPorDefecto {
    $direccion = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
        Select-Object -First 1 -ExpandProperty IPAddress
    if ([string]::IsNullOrWhiteSpace($direccion)) {
        return 'localhost'
    }
    return $direccion
}

function Get-ValorActual([string]$clave, [string]$defecto) {
    if (-not (Test-Path $configFile)) {
        return $defecto
    }
    foreach ($linea in Get-Content -Path $configFile -Encoding UTF8) {
        if ($linea -match "^$([regex]::Escape($clave))=(.*)$") {
            return $Matches[1].Replace('\\', '\')
        }
    }
    return $defecto
}

function ConvertTo-ValorPropiedad([string]$valor) {
    if ($null -eq $valor) {
        return ''
    }
    return $valor.Replace('\', '\\')
}

function Read-TextoPlano([Security.SecureString]$seguro) {
    if ($null -eq $seguro -or $seguro.Length -eq 0) {
        return ''
    }
    $puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero)
    }
}

function Get-UrlJdbc([string]$instancia, [string]$base) {
    $texto = $instancia.Trim()
    $servidor = $texto
    $nombre = ''
    $puertoSql = ''
    $barra = $texto.IndexOf('\')
    if ($barra -ge 0) {
        $servidor = $texto.Substring(0, $barra)
        $nombre = $texto.Substring($barra + 1)
    }
    $coma = $servidor.IndexOf(',')
    if ($coma -ge 0) {
        $puertoSql = $servidor.Substring($coma + 1)
        $servidor = $servidor.Substring(0, $coma)
    }
    if ($servidor -eq '.' -or $servidor -eq '(local)') {
        $servidor = 'localhost'
    }
    $url = "jdbc:sqlserver://$servidor"
    if (-not [string]::IsNullOrWhiteSpace($puertoSql)) {
        $url += ":$puertoSql"
    }
    if (-not [string]::IsNullOrWhiteSpace($nombre)) {
        $url += ";instanceName=$nombre"
    }
    return "$url;databaseName=$base;encrypt=true;trustServerCertificate=true"
}

function New-BaseZonak([string]$instancia, [string]$usuario, [string]$clave) {
    Add-Type -AssemblyName System.Data
    $claveSql = "'" + $clave.Replace("'", "''") + "'"
    $cadena = "Server=$instancia;Database=master;User ID=$usuario;Password=$claveSql;Encrypt=True;TrustServerCertificate=True;Connect Timeout=15"
    $conexion = New-Object System.Data.SqlClient.SqlConnection $cadena
    $conexion.Open()
    try {
        $comando = $conexion.CreateCommand()
        $comando.CommandText = "IF DB_ID(N'Zonak') IS NULL BEGIN CREATE DATABASE [Zonak]; SELECT 'creada'; END ELSE SELECT 'existia';"
        return [string]$comando.ExecuteScalar()
    } finally {
        $conexion.Close()
    }
}

function Read-UrlFirma([string]$actual) {
    while ($true) {
        $etiqueta = if ([string]::IsNullOrWhiteSpace($actual)) {
            'URL de la app que firma (Enter para omitir)'
        } else {
            "URL de la app que firma [$actual]"
        }
        $raw = Read-Host $etiqueta
        if ([string]::IsNullOrWhiteSpace($raw)) {
            return $actual
        }
        $url = $raw.Trim()
        if ($url -notmatch '^https?://' -or $url -match '\s') {
            Write-Host 'La URL debe empezar por http:// o https:// y no llevar espacios.'
            continue
        }
        return $url
    }
}

$hostInicial = Get-ValorActual 'app.harmony.advertised-host' (Get-HostPorDefecto)
$puertoGuardado = 8080
$puertoTexto = Get-ValorActual 'server.port' '8080'
if ($puertoTexto -match '^\d+$') {
    $puertoGuardado = [int]$puertoTexto
}
$urlActual = Get-ValorActual 'app.target.url' ''
$llaveActual = Get-ValorActual 'app.api.key' ''
$emisionActual = Get-ValorActual 'app.emission.point.id' ''
$instanciaActual = Get-ValorActual 'app.sql.instancia' 'localhost\SQLEXPRESS'
$usuarioSqlActual = Get-ValorActual 'spring.datasource.username' 'sa'
$claveSqlActual = Get-ValorActual 'spring.datasource.password' ''

$puertoElegido = Read-Puerto $puertoGuardado
$hostElegido = Read-Host "IP o host que Harmony usara [$hostInicial]"
if ([string]::IsNullOrWhiteSpace($hostElegido)) {
    $hostElegido = $hostInicial
}
$hostElegido = $hostElegido.Trim()
if ($hostElegido -match '[\s:/]') {
    Write-Error 'Escribe solo la IP o el host, sin http ni puerto.'
}

$urlFirma = Read-UrlFirma $urlActual
$llaveNueva = Read-Host 'API key de la app que firma (Enter para conservar la actual)'
if ([string]::IsNullOrWhiteSpace($llaveNueva)) {
    $llaveNueva = $llaveActual
} else {
    $llaveNueva = $llaveNueva.Trim()
    if ($llaveNueva -match '\s') {
        Write-Error 'La API key no puede tener espacios.'
    }
}
$emisionNueva = Read-Host "Punto de emision [$(if ([string]::IsNullOrWhiteSpace($emisionActual)) { 'vacio' } else { $emisionActual })]"
if ([string]::IsNullOrWhiteSpace($emisionNueva)) {
    $emisionNueva = $emisionActual
} else {
    $emisionNueva = $emisionNueva.Trim()
    if ($emisionNueva -match '\s') {
        Write-Error 'El punto de emision no puede tener espacios.'
    }
}

$instanciaSql = ''
$usuarioSql = ''
$claveSql = ''
$estadoBase = ''
while ($true) {
    $instanciaSql = Read-Host "Instancia de SQL Server [$instanciaActual]"
    if ([string]::IsNullOrWhiteSpace($instanciaSql)) {
        $instanciaSql = $instanciaActual
    }
    $instanciaSql = $instanciaSql.Trim()
    if ($instanciaSql -match '\s' -or $instanciaSql.EndsWith('\') -or $instanciaSql.StartsWith('\')) {
        Write-Host 'Escribe la instancia sin espacios, por ejemplo localhost\SQLEXPRESS.'
        continue
    }
    $usuarioSql = Read-Host "Usuario de SQL Server [$usuarioSqlActual]"
    if ([string]::IsNullOrWhiteSpace($usuarioSql)) {
        $usuarioSql = $usuarioSqlActual
    }
    $usuarioSql = $usuarioSql.Trim()
    if ([string]::IsNullOrWhiteSpace($usuarioSql) -or $usuarioSql -match '\s') {
        Write-Host 'El usuario de SQL Server no puede estar vacio ni tener espacios.'
        continue
    }
    $etiquetaClave = if ([string]::IsNullOrEmpty($claveSqlActual)) {
        'Contrasena de SQL Server'
    } else {
        'Contrasena de SQL Server (Enter para conservar la actual)'
    }
    $claveSql = Read-TextoPlano (Read-Host $etiquetaClave -AsSecureString)
    if ([string]::IsNullOrEmpty($claveSql)) {
        $claveSql = $claveSqlActual
    }
    if ([string]::IsNullOrEmpty($claveSql)) {
        Write-Host 'La contrasena de SQL Server no puede estar vacia.'
        continue
    }
    try {
        $estadoBase = New-BaseZonak $instanciaSql $usuarioSql $claveSql
        break
    } catch {
        Write-Host "No se pudo conectar a $instanciaSql para crear la base Zonak."
        Write-Host 'Si es una instancia con nombre, el servicio SQL Server Browser tiene que estar iniciado.'
        Write-Host $_.Exception.Message
    }
}

$urlJdbc = Get-UrlJdbc $instanciaSql 'Zonak'
$contenido = @"
server.port=$puertoElegido
app.harmony.advertised-host=$hostElegido
app.target.url=$urlFirma
app.webhook.url=$urlFirma
app.api.key=$llaveNueva
app.emission.point.id=$emisionNueva
app.sql.instancia=$(ConvertTo-ValorPropiedad $instanciaSql)
spring.datasource.url=$(ConvertTo-ValorPropiedad $urlJdbc)
spring.datasource.username=$(ConvertTo-ValorPropiedad $usuarioSql)
spring.datasource.password=$(ConvertTo-ValorPropiedad $claveSql)
"@
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($configFile, $contenido, $utf8)

$url = "http://${hostElegido}:${puertoElegido}/api/harmony/col"
Write-Host ''
if ($estadoBase -eq 'creada') {
    Write-Host 'Se creo la base Zonak.'
} else {
    Write-Host 'La base Zonak ya existia.'
}
Write-Host ''
Write-Host 'Listo. En el EMC, PartnerCommunication/REST/URL debe quedar:'
Write-Host $url
if (-not [string]::IsNullOrWhiteSpace($urlFirma)) {
    Write-Host 'La app que firma queda en:'
    Write-Host $urlFirma
} else {
    Write-Host 'No se configuro app que firma. Se usara la URL que ya trae el programa.'
}
Write-Host ''
Write-Host "Quedo guardado en $configFile"
Write-Host 'Reinicia el servicio Factura Zona K para que tome estos valores.'
Write-Host 'Si el servicio aun no existe, ejecuta servicio.bat como administrador.'
Write-Host 'Si quedo mal, edita ese archivo o vuelve a ejecutar instalar.ps1.'
