param([string]$Destination)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $Destination) {
    $Destination = Join-Path (Split-Path -Parent $projectRoot) 'ScheduleBoard-Magisk-0.1.0.zip'
}
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
$sourcePath = Join-Path $projectRoot 'magisk'
if (Test-Path -LiteralPath $destinationPath) {
    Remove-Item -LiteralPath $destinationPath
}
$zip = [System.IO.Compression.ZipFile]::Open($destinationPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $sourcePath -File) {
        $entry = $zip.CreateEntry($file.Name)
        $stream = $entry.Open()
        try {
            $value = [System.IO.File]::ReadAllText($file.FullName).Replace("`r`n", "`n")
            $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($value)
            $stream.Write($bytes, 0, $bytes.Length)
        } finally {
            $stream.Dispose()
        }
    }
} finally {
    $zip.Dispose()
}
Write-Output $destinationPath
