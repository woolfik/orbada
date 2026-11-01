# Budowanie Orbady ze zrodel (src\) przy uzyciu samego JDK - bez Anta/Mavena.
#
# Wynik: build\orbada\ - kompletna, uruchamialna dystrybucja (kopia dist-1.2.6\orbada
# z podmienionymi JAR-ami zbudowanymi ze zrodel).
#
# Uzycie:
#   .\build.ps1            # pelne budowanie
#   .\build.ps1 -Run       # zbuduj i uruchom
param(
  [switch]$Run,
  [string]$Release = "8"
)

$ErrorActionPreference = "Stop"
$root  = $PSScriptRoot
$src   = Join-Path $root "src"
$dist  = Join-Path $root "dist-1.2.6\orbada"
$work  = Join-Path $root "build\tmp"
$out   = Join-Path $root "build\orbada"

# Moduly, ktore nie trafiaja do dystrybucji (drink-master to osobny dodatek "drinks-pl")
$skipModules = @("drink-master")
# Pliki zrodlowe wylaczone z kompilacji (test JUnit biblioteki json)
$skipSources = @("json\org\json\Test.java")

# Gdzie laduje JAR danego modulu w dystrybucji
function Get-JarTarget([string]$module) {
  switch ($module) {
    "startup"            { "startup.jar" }
    "orbada"             { "Orbada.jar" }
    "json"               { "lib\json.jar" }
    "orbada-plugins"     { "lib\orbada-plugins.jar" }
    "orbada-db-objects"  { "lib\orbada-db-objects.jar" }
    "orbada-reports-db"  { "lib\orbada-reports-db.jar" }
    # biblioteki autora przeniesione ze zrodel z JAR-ow do src\ (ROADMAP T3)
    { $_ -like "pl.mpak.*" } { "lib\$module.jar" }
    default              { "plugins\$module.jar" }
  }
}

# Narzedzia JDK: JAVA_HOME, a jesli brak - java.home z "java" na PATH (shim Oracle ma tylko java/javac, bez jar)
$jdk = $env:JAVA_HOME
if (-not $jdk) {
  $ErrorActionPreference = "Continue"
  $jdk = (java -XshowSettings:properties -version 2>&1 | ForEach-Object { "$_" } |
    Where-Object { $_ -match "^\s*java\.home\s*=" }) -replace "^\s*java\.home\s*=\s*", ""
  $ErrorActionPreference = "Stop"
}
$javac = Join-Path $jdk "bin\javac.exe"
$jar   = Join-Path $jdk "bin\jar.exe"
$java  = Join-Path $jdk "bin\java.exe"
if (-not (Test-Path $jar)) { throw "Nie znaleziono JDK (jar.exe) w '$jdk' - ustaw JAVA_HOME" }
Write-Host "== JDK: $jdk"

function Invoke-Tool([string]$exe, [string[]]$toolArgs) {
  # javac pisze uwagi na stderr - w PowerShell 5.1 przy "Stop" bylyby traktowane jak blad
  $ErrorActionPreference = "Continue"
  & $exe @toolArgs 2>&1 | ForEach-Object { "$_" }
  if ($LASTEXITCODE -ne 0) { throw "$exe zakonczyl sie bledem ($LASTEXITCODE)" }
}

Write-Host "== Przygotowanie katalogow"
# Uruchomiona Orbada trzyma pliki z build\orbada (JAR-y) - sprawdzamy przed usuwaniem czegokolwiek, inaczej build
# kasowalby czesc plikow i przerywal sie na pierwszym zajetym (polowiczna dystrybucja)
if (Test-Path $out) {
  foreach ($f in Get-ChildItem -Recurse -File $out) {
    try {
      [System.IO.File]::Open($f.FullName, 'Open', 'ReadWrite', 'None').Close()
    } catch {
      throw "Plik $($f.FullName) jest uzywany - zamknij Orbade uruchomiona z build\orbada i zbuduj ponownie"
    }
  }
}
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
# Czyscimy zawartosc, nie sam katalog - bywa otwarty w Eksploratorze/terminalu i wtedy Remove-Item go nie usunie
if (Test-Path $out)  { Get-ChildItem -Force $out | Remove-Item -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null
New-Item -ItemType Directory -Force "$work\all" | Out-Null

$modules = Get-ChildItem -Directory $src | Where-Object { $skipModules -notcontains $_.Name }

# Classpath: biblioteki zewnetrzne i pl.mpak.* z oryginalnej dystrybucji - bez JAR-ow Orbady i bibliotek budowanych ze zrodel
$libJars = Get-ChildItem -Recurse -Filter *.jar "$dist\lib", "$dist\jdbc", "$dist\tools" |
  Where-Object { $_.Name -notlike "orbada-*.jar" -and ($modules.Name -notcontains $_.BaseName) } | ForEach-Object { $_.FullName }
$libCp = $libJars -join ";"

$javacOpts = @("--release", $Release, "-encoding", "Cp1250", "-nowarn", "-Xlint:-options", "-proc:none")

# Krok 1: kompilacja wszystkiego naraz - rozwiazuje zaleznosci miedzy modulami bez ustalania kolejnosci
Write-Host "== Kompilacja wszystkich modulow"
$allSources = foreach ($m in $modules) {
  Get-ChildItem -Recurse -Filter *.java $m.FullName | Where-Object {
    $rel = $_.FullName.Substring($src.Length + 1)
    $skipSources -notcontains $rel
  } | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
}
$allSources | Set-Content -Encoding ascii "$work\all-sources.txt"
Invoke-Tool $javac ($javacOpts + @("-cp", $libCp, "-d", "$work\all", "@$work\all-sources.txt"))

# Krok 2: kazdy modul osobno (z wynikiem kroku 1 na classpath), zeby w JAR-ze byly dokladnie jego klasy
Copy-Item -Recurse "$dist\*" $out
$moduleCp = "$work\all;$libCp"
foreach ($m in $modules) {
  $name = $m.Name
  $classes = "$work\mod\$name"
  New-Item -ItemType Directory -Force $classes | Out-Null
  Write-Host "== Modul $name"

  $sources = Get-ChildItem -Recurse -Filter *.java $m.FullName | Where-Object {
    $skipSources -notcontains $_.FullName.Substring($src.Length + 1)
  } | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
  if ($sources) {
    $sources | Set-Content -Encoding ascii "$work\$name-sources.txt"
    Invoke-Tool $javac ($javacOpts + @("-cp", $moduleCp, "-d", $classes, "@$work\$name-sources.txt"))
  }

  # Zasoby: wszystko poza zrodlami, plikami NetBeans GUI i smieciami edytora
  Get-ChildItem -Recurse -File $m.FullName | Where-Object {
    $_.Extension -notin @(".java", ".form", ".bak", ".xcf") -and $_.Name -notlike "*~"
  } | ForEach-Object {
    $dest = Join-Path $classes $_.FullName.Substring($m.FullName.Length + 1)
    New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
    Copy-Item $_.FullName $dest
  }

  $target = Join-Path $out (Get-JarTarget $name)
  $origJar = Join-Path $dist (Get-JarTarget $name)
  $jarArgs = @("--create", "--file", $target)
  if (Test-Path $origJar) {
    # Manifest bierzemy z oryginalnego JAR-a (Main-Class, Class-Path, SplashScreen-Image)
    $mf = "$work\$name.MF"
    Push-Location $work
    try { Invoke-Tool $jar @("--extract", "--file", $origJar, "META-INF/MANIFEST.MF") } finally { Pop-Location }
    Move-Item -Force "$work\META-INF\MANIFEST.MF" $mf
    $jarArgs += @("--manifest", $mf)
  }
  if ($name -eq "orbada") {
    # Orbada.jar trzyma splash w META-INF (manifest: SplashScreen-Image: META-INF/splashscreen.jpg)
    New-Item -ItemType Directory -Force "$classes\META-INF" | Out-Null
    Copy-Item "$src\startup\res\splashscreen.jpg" "$classes\META-INF\splashscreen.jpg"
  }
  Invoke-Tool $jar ($jarArgs + @("-C", $classes, "."))
}

# Nowe sterowniki JDBC (dist-1.2.6 ma tylko stare): pobierane raz z Maven Central do _pobrane\, sprawdzane SHA-1
$jdbcDrivers = @(
  # Oracle 12c-26ai; ojdbc6/ojdbc14 z 1.2.6 nowe bazy odrzucaja (ORA-28040)
  @{ Url = "https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar"
     Sha1 = "6936aba69cb7142aac36a53ed920af563b0e1dd1"; Plik = "ojdbc11.jar" }
  # Oracle Wallet (F2): bez oraclepki ojdbc11 po cichu pomija wallet (ORA-01017). Wystarczy, ze lezy obok
  # ojdbc11.jar - manifest ojdbc11 ma "Class-Path: oraclepki.jar", wiec wpis w DRIVERS.DRV_EXTRA_LIBRARY zbedny
  @{ Url = "https://repo1.maven.org/maven2/com/oracle/database/security/oraclepki/23.26.3.0.0/oraclepki-23.26.3.0.0.jar"
     Sha1 = "20a9247b60b3575248ea223483ec3b2dc89ec2c4"; Plik = "oraclepki.jar" }
)
$pobrane = Join-Path $root "_pobrane"
foreach ($d in $jdbcDrivers) {
  $lokalny = Join-Path $pobrane (Split-Path -Leaf $d.Url)
  if (-not (Test-Path $lokalny)) {
    Write-Host "== Pobieranie $($d.Url)"
    New-Item -ItemType Directory -Force $pobrane | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $d.Url -OutFile "$lokalny.part"
    Move-Item -Force "$lokalny.part" $lokalny
  }
  if ((Get-FileHash -Algorithm SHA1 $lokalny).Hash -ne $d.Sha1) {
    Remove-Item -Force $lokalny
    throw "Zla suma SHA-1 pliku $lokalny - usuniety, uruchom build ponownie"
  }
  Copy-Item $lokalny (Join-Path $out "jdbc\$($d.Plik)")
}

# Historia wersji i zmian (Pomoc > Ostatnie zmiany, gdy GitHub niedostepny): changes.json z repo zamiast pliku autora
New-Item -ItemType Directory -Force "$out\doc" | Out-Null
Copy-Item -Force (Join-Path $root "changes.json") "$out\doc\changes.json"

# Brak katalogu fonts nie jest bledem (Application.initTTFFonts), ale tworzymy go dla porzadku
New-Item -ItemType Directory -Force "$out\fonts" | Out-Null

# Java 9+: pl.mpak.sky siega do wewnetrznej klasy JDK (sun.swing.SwingUtilities2) - do czasu poprawki otwieramy ja flaga
# (pl.mpak.usedb budowany juz ze zrodel bez sun.security.util.BitArray - ROADMAP T4)
$jvmOpts = @("-Xms64M", "-Xmx1024M",
  "--add-exports", "java.desktop/sun.swing=ALL-UNNAMED",
  "-Djava.system.class.loader=pl.mpak.startup.StartupClassLoader")
"start javaw $($jvmOpts -join ' ') -jar startup.jar %1 %2 %3 %4" |
  Set-Content -Encoding ascii "$out\orbada.bat"
Write-Host "== Gotowe: $out"

if ($Run) {
  Push-Location $out
  try {
    & $java @jvmOpts -jar startup.jar
  } finally { Pop-Location }
}
