$ErrorActionPreference = 'Stop'

$launcherRoot = Split-Path -Parent $PSScriptRoot
$launcherJar = Join-Path $launcherRoot 'KnoxSurvivorsLauncher.jar'
if (-not (Test-Path -LiteralPath $launcherJar)) {
    throw 'KnoxSurvivorsLauncher.jar is missing. Extract the complete launcher ZIP first.'
}

$steamRoots = [System.Collections.Generic.List[string]]::new()
$steamPath = $null
try {
    $steamPath = (Get-ItemProperty -LiteralPath 'HKCU:\Software\Valve\Steam' -ErrorAction Stop).SteamPath
} catch {}
foreach ($candidate in @(
    $env:KNOX_STEAM_ROOT,
    $steamPath,
    (Join-Path ${env:ProgramFiles(x86)} 'Steam'),
    (Join-Path $env:ProgramFiles 'Steam')
)) {
    if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
        $steamRoots.Add([IO.Path]::GetFullPath($candidate))
    }
}

$libraries = [System.Collections.Generic.List[string]]::new()
foreach ($root in $steamRoots) {
    $libraries.Add($root)
    $vdf = Join-Path $root 'steamapps\libraryfolders.vdf'
    if (Test-Path -LiteralPath $vdf) {
        foreach ($line in Get-Content -LiteralPath $vdf) {
            if ($line -match '"path"\s+"([^"]+)"') {
                $libraries.Add(($Matches[1] -replace '\\\\', '\'))
            }
        }
    }
}

$javaw = $null
foreach ($library in $libraries) {
    $candidate = Join-Path $library 'steamapps\common\ProjectZomboid\jre64\bin\javaw.exe'
    if (Test-Path -LiteralPath $candidate) {
        $javaw = $candidate
        break
    }
}
if ($null -eq $javaw) {
    throw 'Project Zomboid bundled Java was not found. Install or verify Project Zomboid through Steam.'
}

if ($steamRoots.Count -gt 0) {
    $env:KNOX_STEAM_ROOT = $steamRoots[0]
}
# Start-Process joins argument arrays into one Windows command line. Quote the
# JAR explicitly: the distributed folder name itself contains spaces.
Start-Process -FilePath $javaw -ArgumentList ('-jar "' + $launcherJar + '"') -WorkingDirectory $launcherRoot -WindowStyle Hidden
