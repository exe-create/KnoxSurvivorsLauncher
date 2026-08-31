$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('knox-bootstrap-' + [Guid]::NewGuid())
$originalSteamRoot = $env:KNOX_STEAM_ROOT
$launchRecords = [System.Collections.Generic.List[hashtable]]::new()

function Start-Process {
    param($FilePath, $ArgumentList, $WorkingDirectory, $WindowStyle)
    $launchRecords.Add(@{
        FilePath = $FilePath; ArgumentList = $ArgumentList
        WorkingDirectory = $WorkingDirectory; WindowStyle = $WindowStyle
    })
}

try {
    $package = Join-Path $fixtureRoot 'Knox Survivors Launcher'
    $steam = Join-Path $fixtureRoot 'Steam Library With Spaces'
    $runtime = Join-Path $steam 'steamapps\common\ProjectZomboid\jre64\bin'
    New-Item -ItemType Directory -Path (Join-Path $package 'scripts'),$runtime -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $runtime 'javaw.exe') | Out-Null
    # Exercise the VDF replacement expression even on clean CI machines without Steam.
    $vdf = Join-Path $steam 'steamapps\libraryfolders.vdf'
    ('"path" "' + $steam.Replace('\', '\\') + '"') | Set-Content -LiteralPath $vdf -Encoding ASCII
    Copy-Item -LiteralPath (Join-Path $repoRoot 'KnoxSurvivorsLauncher.jar') -Destination $package
    $wrapper = Join-Path $package 'scripts\launch-knox-survivors.ps1'
    Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts\launch-knox-survivors.ps1') -Destination $wrapper
    $env:KNOX_STEAM_ROOT = $steam
    & $wrapper
    if ($launchRecords.Count -ne 1) { throw 'Expected exactly one bootstrap launch.' }
    $launchCapture = $launchRecords[0]
    $expectedJar = Join-Path $package 'KnoxSurvivorsLauncher.jar'
    if ($launchCapture.ArgumentList -ne ('-jar "' + $expectedJar + '"')) {
        throw 'Windows bootstrap did not quote the extracted launcher path.'
    }
    if ($launchCapture.FilePath -ne (Join-Path $runtime 'javaw.exe')) {
        throw 'Windows bootstrap did not use the discovered bundled Java.'
    }
    if ($launchCapture.WindowStyle -ne 'Hidden') { throw 'Bootstrap window policy changed.' }
    Write-Output 'Windows bootstrap verification passed (spaced extraction/library paths, bundled Java, hidden launch).'
} finally {
    $env:KNOX_STEAM_ROOT = $originalSteamRoot
    $resolved = [IO.Path]::GetFullPath($fixtureRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        [IO.Path]::GetFileName($resolved).StartsWith('knox-bootstrap-')) {
        Remove-Item -LiteralPath $resolved -Recurse -Force -ErrorAction SilentlyContinue
    }
}
