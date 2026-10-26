$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
$assetDir = Join-Path $projectRoot 'app\src\main\res\drawable-xhdpi'
New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
$bitmap = New-Object Drawing.Bitmap 320,180
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$graphics.Clear([Drawing.ColorTranslator]::FromHtml('#111719'))
$accent = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#5DE0C3'))
$white = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#F4F2EB'))
$markFont = New-Object Drawing.Font 'Segoe UI',56,([Drawing.FontStyle]::Bold)
$wordFont = New-Object Drawing.Font 'Segoe UI',20,([Drawing.FontStyle]::Bold)
$format = New-Object Drawing.StringFormat
$format.Alignment = [Drawing.StringAlignment]::Center
$graphics.DrawString('R',$markFont,$accent,[Drawing.RectangleF]::new(0,12,320,100),$format)
$graphics.DrawString('RAPOPLAYER',$wordFont,$white,[Drawing.RectangleF]::new(0,110,320,48),$format)
$bitmap.Save((Join-Path $assetDir 'tv_banner.png'),[Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose(); $bitmap.Dispose(); $accent.Dispose(); $white.Dispose(); $markFont.Dispose(); $wordFont.Dispose(); $format.Dispose()
