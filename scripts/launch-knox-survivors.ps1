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

# Prefer Steam's configured install first, then common install locations.
foreach ($candidate in @(
    $env:KNOX_STEAM_ROOT,
    $steamPath,
    (Join-Path ${env:ProgramFiles(x86)} 'Steam'),
    (Join-Path $env:ProgramFiles 'Steam')
)) {
    if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
        $full = [IO.Path]::GetFullPath($candidate)
        if (-not $steamRoots.Contains($full)) { $steamRoots.Add($full) }
    }
}

# Fallback for Steam/game installs on D:, E:, etc. This avoids requiring users
# to edit the launcher script when Steam's registry entry is missing or stale.
foreach ($drive in Get-PSDrive -PSProvider FileSystem -ErrorAction SilentlyContinue) {
    foreach ($relative in @('Steam', 'SteamLibrary', 'Program Files (x86)\Steam', 'Program Files\Steam')) {
        $candidate = Join-Path $drive.Root $relative
        if (Test-Path -LiteralPath (Join-Path $candidate 'steamapps')) {
            $full = [IO.Path]::GetFullPath($candidate)
            if (-not $steamRoots.Contains($full)) { $steamRoots.Add($full) }
        }
    }
}

$libraries = [System.Collections.Generic.List[string]]::new()
foreach ($root in $steamRoots) {
    if (-not $libraries.Contains($root)) { $libraries.Add($root) }
    $vdf = Join-Path $root 'steamapps\libraryfolders.vdf'
    if (Test-Path -LiteralPath $vdf) {
        foreach ($line in Get-Content -LiteralPath $vdf) {
            if ($line -match '"path"\s+"([^"]+)"') {
                $library = ($Matches[1] -replace '\\\\', '\')
                if (Test-Path -LiteralPath $library) {
                    $full = [IO.Path]::GetFullPath($library)
                    if (-not $libraries.Contains($full)) { $libraries.Add($full) }
                }
            }
        }
    }
}

$javaw = $null
foreach ($library in $libraries) {
    $game = Join-Path $library 'steamapps\common\ProjectZomboid'
    if (-not (Test-Path -LiteralPath $game)) { continue }

    foreach ($relative in @('jre64\bin\javaw.exe', 'jre\bin\javaw.exe')) {
        $candidate = Join-Path $game $relative
        if (Test-Path -LiteralPath $candidate) {
            $javaw = $candidate
            break
        }
    }
    if ($null -ne $javaw) { break }

    $found = Get-ChildItem -LiteralPath $game -Filter javaw.exe -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.DirectoryName -match '[\\/]bin$' } |
        Select-Object -First 1
    if ($null -ne $found) {
        $javaw = $found.FullName
        break
    }
}
if ($null -eq $javaw) {
    throw 'Project Zomboid bundled Java was not found. The launcher checked Steam libraries on all local drives. Install or verify Project Zomboid through Steam.'
}

if ($steamRoots.Count -gt 0) {
    $env:KNOX_STEAM_ROOT = $steamRoots[0]
}
# Start-Process joins argument arrays into one Windows command line. Quote the
# JAR explicitly: the distributed folder name itself contains spaces.
Start-Process -FilePath $javaw -ArgumentList ('-jar "' + $launcherJar + '"') -WorkingDirectory $launcherRoot -WindowStyle Hidden
