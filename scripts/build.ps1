$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$testClasses = Join-Path $build 'test-classes'
$dist = Join-Path $root 'dist'

Remove-Item -LiteralPath $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes,$testClasses,$dist | Out-Null
$sources = Get-ChildItem -LiteralPath (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName
$tests = Get-ChildItem -LiteralPath (Join-Path $root 'src\test\java') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac --release 17 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
& jar --create --file (Join-Path $root 'KnoxSurvivorsLauncher.jar') --main-class com.knoxsurvivors.launcher.Main -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
& javac --release 17 -cp $classes -d $testClasses $tests
if ($LASTEXITCODE -ne 0) { throw 'Launcher verifier compilation failed.' }
& java -cp "$classes;$testClasses" com.knoxsurvivors.launcher.LauncherVerifier
if ($LASTEXITCODE -ne 0) { throw 'Launcher verification failed.' }

$windowsStage = Join-Path $build 'windows\Knox Survivors Launcher'
$unixStage = Join-Path $build 'unix\Knox Survivors Launcher'
New-Item -ItemType Directory -Force -Path (Join-Path $windowsStage 'scripts'),(Join-Path $unixStage 'scripts') | Out-Null
Copy-Item (Join-Path $root 'KnoxSurvivorsLauncher.jar') $windowsStage
Copy-Item (Join-Path $root 'Launch Knox Survivors.cmd') $windowsStage
Copy-Item (Join-Path $root 'scripts\launch-knox-survivors.ps1') (Join-Path $windowsStage 'scripts')
Copy-Item (Join-Path $root 'KnoxSurvivorsLauncher.jar') $unixStage
Copy-Item (Join-Path $root 'Launch Knox Survivors.command') $unixStage
Copy-Item (Join-Path $root 'scripts\launch-knox-survivors.sh') (Join-Path $unixStage 'scripts')
Copy-Item (Join-Path $root 'README.txt') $windowsStage
Copy-Item (Join-Path $root 'README.txt') $unixStage
Compress-Archive -Path $windowsStage -DestinationPath (Join-Path $dist 'KnoxSurvivorsLauncher-windows.zip') -Force
Compress-Archive -Path $unixStage -DestinationPath (Join-Path $dist 'KnoxSurvivorsLauncher-linux-macos.zip') -Force
Get-FileHash (Join-Path $dist '*.zip') -Algorithm SHA256 | ForEach-Object {
    "$($_.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($_.Path))"
} | Set-Content (Join-Path $dist 'SHA256SUMS.txt') -Encoding ASCII
