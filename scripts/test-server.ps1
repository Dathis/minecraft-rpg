# Поднимает локальный тестовый сервер Paper 1.21.8 с плагином MinecraftRPG, Geyser и Floodgate.
# Запуск: двойной клик по test-server.bat в корне проекта.
# Повторный запуск пересобирает плагин и обновляет его на сервере. Мир и настройки сохраняются.

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$McVersion = "1.21.8"
$UserAgent = "minecraft-rpg-test-server (https://github.com/Dathis/minecraft-rpg)"
$Root = Split-Path -Parent $PSScriptRoot
$Server = Join-Path $Root "test-server"

function Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }

function Find-Java21 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu")) {
        if (Test-Path $base) {
            $candidates += Get-ChildItem $base -Directory | Where-Object { $_.Name -match "^(jdk|zulu)-?2[1-9]" } | ForEach-Object { $_.FullName }
        }
    }
    foreach ($dir in $candidates) {
        $exe = Join-Path $dir "bin\java.exe"
        if (Test-Path $exe) {
            $out = & cmd /c "`"$exe`" -version 2>&1"
            if ($out -match 'version "(2[1-9]|[3-9][0-9])') { return $dir }
        }
    }
    return $null
}

Step "Ищу Java 21"
$Jdk = Find-Java21
if (-not $Jdk) {
    Write-Host "Java 21 не найдена, устанавливаю Eclipse Temurin 21 через winget..."
    winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-package-agreements --accept-source-agreements
    $Jdk = Find-Java21
    if (-not $Jdk) { throw "Не удалось найти Java 21 после установки. Установите вручную: https://adoptium.net/temurin/releases/?version=21" }
}
$env:JAVA_HOME = $Jdk
$env:Path = "$Jdk\bin;$env:Path"
$Java = Join-Path $Jdk "bin\java.exe"
Write-Host "Java: $Jdk"

Step "Собираю плагин"
Push-Location $Root
try {
    & .\gradlew.bat --stop | Out-Null
    & .\gradlew.bat build
    if ($LASTEXITCODE -ne 0) { throw "Сборка плагина не удалась, смотрите вывод выше." }
} finally { Pop-Location }

New-Item -ItemType Directory -Force -Path (Join-Path $Server "plugins") | Out-Null

$PaperJar = Join-Path $Server "paper.jar"
if (-not (Test-Path $PaperJar)) {
    Step "Скачиваю Paper $McVersion"
    $build = Invoke-RestMethod -UserAgent $UserAgent "https://fill.papermc.io/v3/projects/paper/versions/$McVersion/builds/latest"
    Invoke-WebRequest -UserAgent $UserAgent -Uri $build.downloads.'server:default'.url -OutFile $PaperJar
}

$Geyser = Join-Path $Server "plugins\Geyser-Spigot.jar"
if (-not (Test-Path $Geyser)) {
    Step "Скачиваю Geyser"
    Invoke-WebRequest -UserAgent $UserAgent -Uri "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot" -OutFile $Geyser
}
$Floodgate = Join-Path $Server "plugins\floodgate-spigot.jar"
if (-not (Test-Path $Floodgate)) {
    Step "Скачиваю Floodgate"
    Invoke-WebRequest -UserAgent $UserAgent -Uri "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot" -OutFile $Floodgate
}

Step "Копирую плагин на сервер"
Get-ChildItem (Join-Path $Server "plugins") -Filter "minecraft-rpg-*.jar" | Remove-Item -Force
$PluginJar = Get-ChildItem (Join-Path $Root "build\libs") -Filter "minecraft-rpg-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Copy-Item $PluginJar.FullName (Join-Path $Server "plugins") -Force

Set-Content -Path (Join-Path $Server "eula.txt") -Value "eula=true" -Encoding ASCII

$ip = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.PrefixOrigin -ne "WellKnown" } |
        Select-Object -First 1).IPAddress

Write-Host ""
Write-Host "Сервер запускается. Когда появится 'Done':" -ForegroundColor Green
Write-Host "  1. Напишите здесь: op ВашНик"
Write-Host "  2. Java 1.21.8:  адрес localhost"
if ($ip) { Write-Host "  3. Bedrock:      адрес $ip, порт 19132 (с телефона в той же Wi-Fi)" }
Write-Host "  4. В игре: /create-item"
Write-Host "  Остановить сервер: stop"
Write-Host "  Если Windows спросит про брандмауэр, разрешите Java в частных сетях."
Write-Host ""

Push-Location $Server
try { & $Java -Xms1G -Xmx2G -jar paper.jar nogui } finally { Pop-Location }
