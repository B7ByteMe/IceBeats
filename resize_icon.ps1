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

$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($folder in $sizes.Keys) {
    $size = $sizes[$folder]
    $destPath = Join-Path -Path $resDir -ChildPath "$folder\ic_launcher.png"
    $destPathForeground = Join-Path -Path $resDir -ChildPath "$folder\ic_launcher_foreground.png"
    $destPathRound = Join-Path -Path $resDir -ChildPath "$folder\ic_launcher_round.png"

    try {
        $bitmap = New-Object System.Drawing.Bitmap($size, $size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.DrawImage($img, 0, 0, $size, $size)
        $graphics.Dispose()
        
        $bitmap.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Save($destPathForeground, [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Save($destPathRound, [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Dispose()
        Write-Host "Resized and saved for $($folder) ($size x $size)"
    } catch {
        Write-Host "Failed to process $($folder): $_"
    }
}
$img.Dispose()
Write-Host "Done"
