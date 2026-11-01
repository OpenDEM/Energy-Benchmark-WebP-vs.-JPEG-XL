# This code was generated using AI and manually reviewed.

<#
.SYNOPSIS
  Compares JPEG XL (cjxl) and WebP (cwebp) using their DEFAULT SETTINGS for all PNG images
  in the "images" folder: file size, CPU time and estimated energy use (encoding + transfer).
  The encoded images are written to "output_images".

.DESCRIPTION
  Both encoders are called without any quality or effort parameters:
    cjxl  image.png image.jxl      (documented default: effort 7, distance 1.0 = "visually lossless", but lossy)
    cwebp image.png -o image.webp  (default: lossy, quality 75, method 4)
  Both receive the same PNG file as input.

  The measured value is the CPU time of the process (user + kernel, summed over all threads).
  It is the basis for the energy estimate, because cjxl uses several threads by default and
  the elapsed (wall-clock) time would then underestimate the effort.

  NOTE: The energy use is an ESTIMATE based on CPU time x an assumption (watts per core)
  and file size x an assumption (kWh per GB). Both assumptions are parameters and uncertain.
  The real power draw is not measured.

  The image quality of the default settings is NOT equivalent (different scales, both lossy).
  The output images should therefore be checked visually.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1
  powershell -ExecutionPolicy Bypass -File .\energy_benchmark.ps1 -Runs 10 -CoreWatts 10
#>
param(
    [string]$ImageDir = "$PSScriptRoot\images",
    [string]$OutDir = "$PSScriptRoot\output_images",
    [int]$Runs = 5,
    [int]$Warmup = 1,
    [double]$CoreWatts = 15,
    [double[]]$KWhPerGB = @(0.01, 0.06, 0.1),
    [string]$Cjxl = "$PSScriptRoot\libjxl_0_12_0\cjxl.exe",
    [string]$Cwebp = "$PSScriptRoot\libwebp-1_6_0\cwebp.exe"
)

$ErrorActionPreference = 'Stop'

foreach ($f in @($ImageDir, $Cjxl, $Cwebp)) {
    if (-not (Test-Path $f)) { throw "Not found: $f" }
}
$images = @(Get-ChildItem -Path $ImageDir -Filter *.png | Sort-Object Name)
if ($images.Count -eq 0) { throw "No PNG files found in $ImageDir." }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Get-Median([double[]]$values) {
    $s = @($values | Sort-Object)
    $n = $s.Count
    if ($n % 2 -eq 1) { return $s[[int](($n - 1) / 2)] }
    return ($s[$n / 2 - 1] + $s[$n / 2]) / 2
}

function Measure-Tool {
    param([string]$Name, [string]$Exe, [string[]]$ToolArgs, [string]$OutFile)

    $cpu = New-Object System.Collections.Generic.List[double]
    $wall = New-Object System.Collections.Generic.List[double]

    for ($i = 0; $i -lt ($Runs + $Warmup); $i++) {
        if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $Exe
        $psi.Arguments = (($ToolArgs | ForEach-Object { '"' + $_ + '"' }) -join ' ')
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $p = [System.Diagnostics.Process]::Start($psi)
        $null = $p.StandardOutput.ReadToEnd()
        $errText = $p.StandardError.ReadToEnd()
        $p.WaitForExit()
        $sw.Stop()

        if ($p.ExitCode -ne 0) {
            throw "$Name failed (exit code $($p.ExitCode)). Output: $errText"
        }
        if ($i -ge $Warmup) {
            $cpu.Add($p.TotalProcessorTime.TotalMilliseconds)
            $wall.Add($sw.Elapsed.TotalMilliseconds)
        }
        $p.Dispose()
    }

    $cpuMs = Get-Median $cpu.ToArray()
    $wallMs = Get-Median $wall.ToArray()
    return [pscustomobject]@{
        Name   = $Name
        Bytes  = (Get-Item $OutFile).Length
        CpuMs  = $cpuMs
        WallMs = $wallMs
        EncJ   = ($cpuMs / 1000.0) * $CoreWatts
    }
}

# Compares two results (a = JPEG XL, b = WebP) under one assumption for the transfer energy
function Get-Comparison($a, $b, [double]$jPerByte) {
    if ($a.EncJ -le $b.EncJ -and $a.Bytes -le $b.Bytes) { return 'JPEG XL better (encoding and size)' }
    if ($b.EncJ -le $a.EncJ -and $b.Bytes -le $a.Bytes) { return 'WebP better (encoding and size)' }
    if ($a.EncJ -gt $b.EncJ) { $exp = 'JPEG XL'; $e = $a; $c = $b } else { $exp = 'WebP'; $e = $b; $c = $a }
    $n = ($e.EncJ - $c.EncJ) / (($c.Bytes - $e.Bytes) * $jPerByte)
    return ('{0} from {1:N1} deliveries' -f $exp, $n)
}

Write-Host ""
Write-Host ("Images: {0} files in {1}" -f $images.Count, $ImageDir)
Write-Host ("Output: {0}" -f $OutDir)
Write-Host ("Runs: {0} (+{1} warmup) per image and encoder, assumption: {2} W per active core" -f $Runs, $Warmup, $CoreWatts)

$all = @()
$idx = 0
foreach ($img in $images) {
    $idx++
    $base = $img.BaseName
    Write-Host ("[{0}/{1}] {2} ..." -f $idx, $images.Count, $img.Name)

    $jxlOut = Join-Path $OutDir "$base.jxl"
    $webpOut = Join-Path $OutDir "$base.webp"

    $jxl = Measure-Tool -Name 'JPEG XL' -Exe $Cjxl -ToolArgs @($img.FullName, $jxlOut) -OutFile $jxlOut
    $webp = Measure-Tool -Name 'WebP' -Exe $Cwebp -ToolArgs @($img.FullName, '-o', $webpOut) -OutFile $webpOut

    $all += [pscustomobject]@{ Image = $base; PngBytes = $img.Length; Jxl = $jxl; Webp = $webp }
}

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== Per image: file size and encoding effort (default settings) ==="
$table = foreach ($r in $all) {
    [pscustomobject]@{
        Image           = $r.Image
        'PNG [KB]'      = ('{0:N1}' -f ($r.PngBytes / 1KB))
        'JXL [KB]'      = ('{0:N1}' -f ($r.Jxl.Bytes / 1KB))
        'JXL %'         = ('{0:N0}%' -f (100.0 * $r.Jxl.Bytes / $r.PngBytes))
        'WebP [KB]'     = ('{0:N1}' -f ($r.Webp.Bytes / 1KB))
        'WebP %'        = ('{0:N0}%' -f (100.0 * $r.Webp.Bytes / $r.PngBytes))
        'JXL CPU [ms]'  = ('{0:N0}' -f $r.Jxl.CpuMs)
        'WebP CPU [ms]' = ('{0:N0}' -f $r.Webp.CpuMs)
        'JXL [J]'       = ('{0:N2}' -f $r.Jxl.EncJ)
        'WebP [J]'      = ('{0:N2}' -f $r.Webp.EncJ)
    }
}
$table | Format-Table -AutoSize

$pngTotal = ($all | ForEach-Object { $_.PngBytes } | Measure-Object -Sum).Sum
$tj = [pscustomobject]@{
    Name   = 'JPEG XL'
    Bytes  = ($all | ForEach-Object { $_.Jxl.Bytes } | Measure-Object -Sum).Sum
    CpuMs  = ($all | ForEach-Object { $_.Jxl.CpuMs } | Measure-Object -Sum).Sum
    WallMs = ($all | ForEach-Object { $_.Jxl.WallMs } | Measure-Object -Sum).Sum
    EncJ   = ($all | ForEach-Object { $_.Jxl.EncJ } | Measure-Object -Sum).Sum
}
$tw = [pscustomobject]@{
    Name   = 'WebP'
    Bytes  = ($all | ForEach-Object { $_.Webp.Bytes } | Measure-Object -Sum).Sum
    CpuMs  = ($all | ForEach-Object { $_.Webp.CpuMs } | Measure-Object -Sum).Sum
    WallMs = ($all | ForEach-Object { $_.Webp.WallMs } | Measure-Object -Sum).Sum
    EncJ   = ($all | ForEach-Object { $_.Webp.EncJ } | Measure-Object -Sum).Sum
}

Write-Host "=== Total over all images ==="
$sum = @(
    [pscustomobject]@{ Variant = 'PNG (input)'; 'Size [KB]' = ('{0:N1}' -f ($pngTotal / 1KB)); 'vs. PNG' = '100%'; 'CPU [ms]' = '-'; 'Runtime [ms]' = '-'; 'Encoding [J]' = '-' }
)
foreach ($t in @($tj, $tw)) {
    $sum += [pscustomobject]@{
        Variant        = $t.Name
        'Size [KB]'    = ('{0:N1}' -f ($t.Bytes / 1KB))
        'vs. PNG'      = ('{0:N0}%' -f (100.0 * $t.Bytes / $pngTotal))
        'CPU [ms]'     = ('{0:N0}' -f $t.CpuMs)
        'Runtime [ms]' = ('{0:N0}' -f $t.WallMs)
        'Encoding [J]' = ('{0:N2}' -f $t.EncJ)
    }
}
$sum | Format-Table -AutoSize
Write-Host ("CPU time / runtime: JPEG XL {0:N1}, WebP {1:N1} (values above 1 = several cores used)" -f ($tj.CpuMs / $tj.WallMs), ($tw.CpuMs / $tw.WallMs))
Write-Host "Note: The resolution of the process CPU time on Windows is a few milliseconds."

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== Break-even per image: from how many deliveries does the more expensive encoder pay off? ==="
Write-Host "The transfer energy per byte is an assumption (kWh/GB), not a measurement."
$be = foreach ($r in $all) {
    $row = [ordered]@{ Image = $r.Image }
    foreach ($k in $KWhPerGB) {
        $jPerByte = $k * 3.6e6 / 1e9
        $row["$k kWh/GB"] = Get-Comparison $r.Jxl $r.Webp $jPerByte
    }
    [pscustomobject]$row
}
$be | Format-Table -AutoSize

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== Total energy for all images: encode once + N deliveries ==="
$nList = 1, 10, 100, 1000, 10000
foreach ($k in $KWhPerGB) {
    $jPerByte = $k * 3.6e6 / 1e9
    Write-Host ""
    Write-Host ("--- Transfer assumption: {0} kWh/GB ---" -f $k)

    $rows = foreach ($n in $nList) {
        $ej = $tj.EncJ + $n * $tj.Bytes * $jPerByte
        $ew = $tw.EncJ + $n * $tw.Bytes * $jPerByte
        $better = 'equal'
        if ($ej -lt $ew) { $better = 'JPEG XL' } elseif ($ew -lt $ej) { $better = 'WebP' }
        [pscustomobject]@{
            Deliveries    = $n
            'JPEG XL [J]' = ('{0:N2}' -f $ej)
            'WebP [J]'    = ('{0:N2}' -f $ew)
            Cheaper       = $better
        }
    }
    $rows | Format-Table -AutoSize
    Write-Host ("Overall: " + (Get-Comparison $tj $tw $jPerByte))
}

Write-Host ""
Write-Host ("Encoded images are in: {0}" -f $OutDir)
Write-Host ""
