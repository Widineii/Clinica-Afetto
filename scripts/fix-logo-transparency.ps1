Add-Type -AssemblyName System.Drawing

$sourcePath = Join-Path $PSScriptRoot "..\src\main\resources\static\images\afetto-logo.png"
$sourcePath = [System.IO.Path]::GetFullPath($sourcePath)

$bitmap = [System.Drawing.Bitmap]::FromFile($sourcePath)
Write-Host "Original: $($bitmap.Width)x$($bitmap.Height) $($bitmap.PixelFormat)"

function Test-IsBackgroundColor([System.Drawing.Color]$color) {
    if ($color.A -lt 16) { return $true }
    $isLightGray = ($color.R -gt 190 -and $color.G -gt 190 -and $color.B -gt 190 -and $color.R -lt 250)
    $isWhite = ($color.R -gt 245 -and $color.G -gt 245 -and $color.B -gt 245)
    return ($isLightGray -or $isWhite)
}

$processed = New-Object System.Drawing.Bitmap $bitmap.Width, $bitmap.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$processed.MakeTransparent()

for ($y = 0; $y -lt $bitmap.Height; $y++) {
    for ($x = 0; $x -lt $bitmap.Width; $x++) {
        $color = $bitmap.GetPixel($x, $y)
        if (Test-IsBackgroundColor $color) {
            $processed.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        } else {
            $processed.SetPixel($x, $y, $color)
        }
    }
}

$bitmap.Dispose()
$processed.Save($sourcePath, [System.Drawing.Imaging.ImageFormat]::Png)
$processed.Dispose()

Write-Host "Logo atualizada com fundo transparente: $sourcePath"
