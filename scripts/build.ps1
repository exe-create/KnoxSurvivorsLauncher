$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$testClasses = Join-Path $build 'test-classes'
$dist = Join-Path $root 'dist'

Remove-Item -LiteralPath $build -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes,$testClasses,$dist | Out-Null
$sources = Get-ChildItem -LiteralPath (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName
$tests = Get-ChildItem -LiteralPath (Join-Path $root 'src\test\java') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac --release 17 -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
$resources = Join-Path $root 'src\main\resources'
if (Test-Path -LiteralPath $resources) {
    Get-ChildItem -LiteralPath $resources -File | Where-Object { $_.Name -ne 'README.txt' } | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $classes $_.Name) -Force
    }
}
@("Manifest-Version: 1.0", "Main-Class: com.knoxsurvivors.launcher.Main", "Implementation-Version: 0.3.1", "Knox-Update-Protocol: 1", "") | Set-Content (Join-Path $build 'MANIFEST.MF') -Encoding ascii
& jar --create --file (Join-Path $root 'KnoxSurvivorsLauncher.jar') --manifest (Join-Path $build 'MANIFEST.MF') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
& javac --release 17 -cp $classes -d $testClasses $tests
if ($LASTEXITCODE -ne 0) { throw 'Launcher verifier compilation failed.' }
& java -cp "$classes;$testClasses" com.knoxsurvivors.launcher.LauncherVerifier
if ($LASTEXITCODE -ne 0) { throw 'Launcher verification failed.' }
& (Join-Path $PSScriptRoot 'verify-bootstrap.ps1')

$windowsStage = Join-Path $build 'windows\Knox Survivors Launcher'
$linuxStage = Join-Path $build 'linux\Knox Survivors Launcher'
$macStage = Join-Path $build 'macos\Knox Survivors Launcher'
New-Item -ItemType Directory -Force -Path (Join-Path $windowsStage 'scripts'),(Join-Path $linuxStage 'scripts'),(Join-Path $macStage 'scripts') | Out-Null
Copy-Item (Join-Path $root 'KnoxSurvivorsLauncher.jar') $windowsStage
Copy-Item (Join-Path $root 'Launch Knox Survivors.cmd') $windowsStage
Copy-Item (Join-Path $root 'scripts\launch-knox-survivors.ps1') (Join-Path $windowsStage 'scripts')
Copy-Item (Join-Path $root 'KnoxSurvivorsLauncher.jar') $linuxStage
Copy-Item (Join-Path $root 'scripts\launch-knox-survivors.sh') (Join-Path $linuxStage 'scripts')
Copy-Item (Join-Path $root 'KnoxSurvivorsLauncher.jar') $macStage
Copy-Item (Join-Path $root 'Launch Knox Survivors.command') $macStage
Copy-Item (Join-Path $root 'scripts\launch-knox-survivors.sh') (Join-Path $macStage 'scripts')
Copy-Item (Join-Path $root 'README.txt') $windowsStage
Copy-Item (Join-Path $root 'README.txt') $linuxStage
Copy-Item (Join-Path $root 'README.txt') $macStage
Compress-Archive -Path $windowsStage -DestinationPath (Join-Path $dist 'KnoxSurvivorsLauncher-windows.zip') -Force
Compress-Archive -Path $linuxStage -DestinationPath (Join-Path $dist 'KnoxSurvivorsLauncher-linux.zip') -Force
Compress-Archive -Path $macStage -DestinationPath (Join-Path $dist 'KnoxSurvivorsLauncher-macos.zip') -Force
Copy-Item (Join-Path $root 'KnoxSurvivorsLauncher.jar') (Join-Path $dist 'KnoxSurvivorsLauncher.jar')
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    Get-ChildItem -Path (Join-Path $dist '*.zip'),(Join-Path $dist 'KnoxSurvivorsLauncher.jar') -File | ForEach-Object {
        $stream = [IO.File]::OpenRead($_.FullName)
        try {
            $hash = ([BitConverter]::ToString($sha256.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
        } finally {
            $stream.Dispose()
        }
        "$hash  $($_.Name)"
    } | Set-Content (Join-Path $dist 'SHA256SUMS.txt') -Encoding ASCII
} finally {
    $sha256.Dispose()
}
