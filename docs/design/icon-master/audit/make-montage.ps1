param(
  [string]$Dir = 'H:\EnglishLearningSoftware\docs\design\icon-master',
  [string]$Out = 'C:\Users\STEVEL~1\AppData\Local\Temp\opencode\montage-28.png',
  [int]$Cell = 200,
  [int]$Cols = 7,
  [int]$Pad = 6,
  [int]$LabH = 22
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$slugs = @('book','books','clock','document','microphone','speaker','spark','chart','camera','shield','upload','download',
           'search','list','bookmark','star','translate','image','newspaper','gear','person','info','bell','warning','trash','play','pause','stop')
$rows = [math]::Ceiling($slugs.Count / $Cols)
$labH = $LabH
$pad = $Pad
$W = $Cols * ($Cell + $pad) + $pad
$H = $rows * ($Cell + $labH + $pad) + $pad

$bmp = New-Object System.Drawing.Bitmap($W, $H)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.Clear([System.Drawing.Color]::FromArgb(246,249,246))
$font = New-Object System.Drawing.Font('Segoe UI', 11, [System.Drawing.FontStyle]::Regular)
$br = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(74,85,81))
$penBase = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(238,242,238), 1)

for ($k = 0; $k -lt $slugs.Count; $k++) {
  $n = $k + 1
  $col = $k % $Cols
  $row = [math]::Floor($k / $Cols)
  $x = $pad + $col * ($Cell + $pad)
  $y = $pad + $row * ($Cell + $labH + $pad)
  $g.DrawRectangle($penBase, $x, $y, $Cell, $Cell)
  $f = Join-Path $Dir ('icon-color-calm__{0:d2}-{1}.png' -f $n, $slugs[$k])
  if (Test-Path -LiteralPath $f) {
    $src = [System.Drawing.Image]::FromFile($f)
    $g.DrawImage($src, $x, $y, $Cell, $Cell)
    $src.Dispose()
  }
  $label = ('{0:d2} {1}' -f $n, $slugs[$k])
  $g.DrawString($label, $font, $br, [single]($x + 2), [single]($y + $Cell + 2))
}
$g.Dispose()
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"montage -> $Out  ($W x $H)"

