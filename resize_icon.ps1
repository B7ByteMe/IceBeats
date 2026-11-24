Add-Type -AssemblyName System.Drawing

$defaultLogo = Join-Path -Path $PSScriptRoot -ChildPath "app\src\main\res\drawable\splash_logo.png"
if (-not $srcPath -or -not (Test-Path $srcPath)) {
    $srcPath = $defaultLogo
}
$resDir = Join-Path -Path $PSScriptRoot -ChildPath "app\src\main\res"

try {
    $img = [System.Drawing.Image]::FromFile($srcPath)
} catch {
    Write-Host "Failed to load image: $_"
    exit 1
}

function Draw-CenteredScaledImage {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.Image]$SourceImage,
        [int]$CanvasSize,
        [double]$Scale
    )
    $drawSize = [double]$CanvasSize * $Scale
    $offset = ([double]$CanvasSize - $drawSize) / 2.0
    $Graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $Graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $Graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $Graphics.DrawImage($SourceImage, [float]$offset, [float]$offset, [float]$drawSize, [float]$drawSize)
}

function Convert-ToMonochrome {
    param([System.Drawing.Bitmap]$SourceBitmap)
    $mono = New-Object System.Drawing.Bitmap($SourceBitmap.Width, $SourceBitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $SourceBitmap.Width, $SourceBitmap.Height)
    $srcData = $SourceBitmap.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $dstData = $mono.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $byteCount = [Math]::Abs($srcData.Stride) * $SourceBitmap.Height
    $bytes = New-Object byte[] $byteCount
    [System.Runtime.InteropServices.Marshal]::Copy($srcData.Scan0, $bytes, 0, $byteCount)
    for ($i = 0; $i -lt $byteCount; $i += 4) {
        if ($bytes[$i + 3] -gt 0) {
            $bytes[$i] = 255     # B
            $bytes[$i + 1] = 255 # G
            $bytes[$i + 2] = 255 # R
        }
    }
    [System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $dstData.Scan0, $byteCount)
    $SourceBitmap.UnlockBits($srcData)
    $mono.UnlockBits($dstData)
    return $mono
}

# 1. Adaptive Icon Foreground & Monochrome (108dp base)
$adaptiveSizes = @{
    "mipmap-mdpi"    = 108
    "mipmap-hdpi"    = 162
    "mipmap-xhdpi"   = 216
    "mipmap-xxhdpi"  = 324
    "mipmap-xxxhdpi" = 432
}

foreach ($folder in $adaptiveSizes.Keys) {
    $size = $adaptiveSizes[$folder]
    $destFg = Join-Path -Path $resDir -ChildPath "$folder\ic_launcher_foreground.png"
    $destMono = Join-Path -Path $resDir -ChildPath "$folder\ic_launcher_monochrome.png"

    $bitmap = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    # 0.58x of 108dp canvas -> headphone content is ~38.6% of canvas (~58% of visible 72dp cropped icon)
    Draw-CenteredScaledImage -Graphics $graphics -SourceImage $img -CanvasSize $size -Scale 0.58
    $graphics.Dispose()

    $bitmap.Save($destFg, [System.Drawing.Imaging.ImageFormat]::Png)
    $mono = Convert-ToMonochrome -SourceBitmap $bitmap
    $mono.Save($destMono, [System.Drawing.Imaging.ImageFormat]::Png)
    $mono.Dispose()
    $bitmap.Dispose()
    Write-Host "Saved adaptive foreground & monochrome for $folder ($size x $size)"
}

# 2. Legacy Launcher Icons (48dp base) with dark #0B0E0C circular background
$legacySizes = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

$bgColor = [System.Drawing.ColorTranslator]::FromHtml("#0B0E0C")
$bgBrush = New-Object System.Drawing.SolidBrush($bgColor)

foreach ($folder in $legacySizes.Keys) {
    $size = $legacySizes[$folder]
    $destLauncher = Join-Path -Path $resDir -ChildPath "$folder\ic_launcher.png"
    $destRound = Join-Path -Path $resDir -ChildPath "$folder\ic_launcher_round.png"

    $bitmap = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $margin = [float]($size * 0.05)
    $diameter = [float]($size * 0.90)
    $graphics.FillEllipse($bgBrush, $margin, $margin, $diameter, $diameter)

    Draw-CenteredScaledImage -Graphics $graphics -SourceImage $img -CanvasSize $size -Scale 0.68
    $graphics.Dispose()

    $bitmap.Save($destLauncher, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Save($destRound, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
    Write-Host "Saved legacy ic_launcher & ic_launcher_round for $folder ($size x $size)"
}

# 3. Play Store 512x512 icons
$playstoreTargets = @(
    (Join-Path -Path $PSScriptRoot -ChildPath "app\src\main\ic_launcher-playstore.png"),
    (Join-Path -Path $PSScriptRoot -ChildPath "app\src\main\logoxd-playstore.png"),
    (Join-Path -Path $PSScriptRoot -ChildPath "assets\ic_launcher-playstore.png")
)
foreach ($target in $playstoreTargets) {
    if (Test-Path (Split-Path $target -Parent)) {
        $bmp512 = New-Object System.Drawing.Bitmap(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g512 = [System.Drawing.Graphics]::FromImage($bmp512)
        $g512.Clear($bgColor)
        Draw-CenteredScaledImage -Graphics $g512 -SourceImage $img -CanvasSize 512 -Scale 0.72
        $g512.Dispose()
        $bmp512.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp512.Dispose()
        Write-Host "Saved Play Store icon: $target"
    }
}

$bgBrush.Dispose()
$img.Dispose()
Write-Host "Done"
