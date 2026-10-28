param(
  [string]$Dir = 'H:\EnglishLearningSoftware\docs\design\icon-master',
  [int]$Step = 4,
  [string]$OutJson = (Join-Path $PSScriptRoot 'icon-full-audit.json')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$pal = @{
  Learn    = @('#7BB4E6', '#CDE6F7', '#175A93')
  Library  = @('#B7A0D9', '#E4DAF2', '#65478A')
  Review   = @('#EBC68B', '#F7E7CB', '#8A5712')
  Settings = @('#A6B0AC', '#DCE4E0', '#4A5551')
  AiSpeech = @('#6CCBAB', '#CFEDE2', '#1A7A63')
  Reading  = @('#E297A4', '#F6DCE0', '#9C3B4F')
  Danger   = @('#DD8B81', '#F7DCD6', '#9E2B24')
}

# batch: baseline = the 8 untouched originals from the approved calm batch
$icons = @(
  @{ i = 1;  slug = 'book';      dom = 'Learn';    batch = 'baseline' },
  @{ i = 2;  slug = 'books';     dom = 'Library';  batch = 'baseline' },
  @{ i = 3;  slug = 'clock';     dom = 'Review';   batch = 'baseline' },
  @{ i = 4;  slug = 'document';  dom = 'Settings'; batch = 'baseline' },
  @{ i = 5;  slug = 'microphone';dom = 'AiSpeech'; batch = 'baseline' },
  @{ i = 6;  slug = 'speaker';   dom = 'Reading';  batch = 'baseline' },
  @{ i = 7;  slug = 'spark';     dom = 'AiSpeech'; batch = 'recolor'  },
  @{ i = 8;  slug = 'chart';     dom = 'Learn';    batch = 'recolor'  },
  @{ i = 9;  slug = 'camera';    dom = 'Danger';   batch = 'baseline' },
  @{ i = 10; slug = 'shield';    dom = 'Settings'; batch = 'recolor'  },
  @{ i = 11; slug = 'upload';    dom = 'Library';  batch = 'fix'      },
  @{ i = 12; slug = 'download';  dom = 'AiSpeech'; batch = 'baseline' },
  @{ i = 13; slug = 'search';    dom = 'Danger';   batch = 'remake'   },
  @{ i = 14; slug = 'list';      dom = 'Library';  batch = 'remake'   },
  @{ i = 15; slug = 'bookmark';  dom = 'Reading';  batch = 'remake'   },
  @{ i = 16; slug = 'star';      dom = 'Review';   batch = 'remake'   },
  @{ i = 17; slug = 'translate'; dom = 'Learn';    batch = 'remake'   },
  @{ i = 18; slug = 'image';     dom = 'AiSpeech'; batch = 'remake'   },
  @{ i = 19; slug = 'newspaper'; dom = 'Reading';  batch = 'remake'   },
  @{ i = 20; slug = 'gear';      dom = 'Settings'; batch = 'remake'   },
  @{ i = 21; slug = 'person';    dom = 'Settings'; batch = 'remake'   },
  @{ i = 22; slug = 'info';      dom = 'Settings'; batch = 'remake'   },
  @{ i = 23; slug = 'bell';      dom = 'Review';   batch = 'remake'   },
  @{ i = 24; slug = 'warning';   dom = 'Danger';   batch = 'remake'   },
  @{ i = 25; slug = 'trash';     dom = 'Danger';   batch = 'remake'   },
  @{ i = 26; slug = 'play';      dom = 'AiSpeech'; batch = 'remake'   },
  @{ i = 27; slug = 'pause';     dom = 'AiSpeech'; batch = 'remake'   },
  @{ i = 28; slug = 'stop';      dom = 'AiSpeech'; batch = 'remake'   }
)

function Hex2Rgb([string]$hex) {
  $h = $hex.TrimStart('#')
  @([Convert]::ToInt32($h.Substring(0,2),16), [Convert]::ToInt32($h.Substring(2,2),16), [Convert]::ToInt32($h.Substring(4,2),16))
}
function Dist($a, $b) {
  $dr = [double]$a[0]-[double]$b[0]; $dg = [double]$a[1]-[double]$b[1]; $db = [double]$a[2]-[double]$b[2]
  [math]::Sqrt($dr*$dr + $dg*$dg + $db*$db)
}
function ToHex($rgb) { '#{0:X2}{1:X2}{2:X2}' -f [int]$rgb[0], [int]$rgb[1], [int]$rgb[2] }

$BG = @(246, 249, 246)
$rows = @()

foreach ($ic in $icons) {
  $file = Join-Path $Dir ('icon-color-calm__{0:d2}-{1}.png' -f $ic.i, $ic.slug)
  if (-not (Test-Path -LiteralPath $file)) {
    $rows += [pscustomobject]@{ n = $ic.i; slug = $ic.slug; dom = $ic.dom; batch = $ic.batch; status = 'missing-file' }
    continue
  }
  $c = $pal[$ic.dom]
  $targets = @((Hex2Rgb $c[0]), (Hex2Rgb $c[1]), (Hex2Rgb $c[2]))
  $names = @('base','light','deep')

  $bmp = New-Object System.Drawing.Bitmap($file)
  $w = $bmp.Width; $h = $bmp.Height
  $gw = [int][math]::Floor(($w-1)/$Step) + 1
  $gh = [int][math]::Floor(($h-1)/$Step) + 1
  $mask = New-Object 'int[,]' $gh, $gw

  $cnt = @{ base = 0; light = 0; deep = 0; other = 0; nonbg = 0 }
  $sum = @{ base = @(0.0,0.0,0.0); light = @(0.0,0.0,0.0); deep = @(0.0,0.0,0.0) }
  $cx = @{ base = 0.0; light = 0.0 }
  $cy = @{ base = 0.0; light = 0.0 }
  $minX = $w; $maxX = -1; $minY = $h; $maxY = -1

  for ($y = 0; $y -lt $h; $y += $Step) {
    $my = [int][math]::Floor($y / $Step)
    for ($x = 0; $x -lt $w; $x += $Step) {
      $mx = [int][math]::Floor($x / $Step)
      $px = $bmp.GetPixel($x, $y)
      $p = @([int]$px.R, [int]$px.G, [int]$px.B)
      $dBg = Dist $p $BG
      $best = -1; $bestD = [double]::MaxValue
      for ($k = 0; $k -lt 3; $k++) {
        $d = Dist $p $targets[$k]
        if ($d -lt $bestD) { $bestD = $d; $best = $k }
      }
      # background wins only if it is both close to bg and closer than any palette entry
      if ($dBg -le 14 -and $dBg -le $bestD) { continue }
      $cnt.nonbg++
      if ($minX -gt $x) { $minX = $x }
      if ($x -gt $maxX) { $maxX = $x }
      if ($minY -gt $y) { $minY = $y }
      if ($y -gt $maxY) { $maxY = $y }
      if ($bestD -le 48) {
        $nm = $names[$best]
        $mask[$my,$mx] = $best + 1
        $cnt[$nm]++
        $sum[$nm][0] += $p[0]; $sum[$nm][1] += $p[1]; $sum[$nm][2] += $p[2]
        if ($nm -ne 'deep') { $cx[$nm] += $x; $cy[$nm] += $y }
      } else {
        $mask[$my,$mx] = 0
        $cnt.other++
      }
    }
  }

  # connected regions of the light cluster (4-connectivity on the sampled grid)
  $lightRegions = 0
  $lightRegionSizes = @()
  if ($cnt.light -gt 0) {
    $seen = New-Object 'bool[,]' $gh, $gw
    for ($ry = 0; $ry -lt $gh; $ry++) {
      for ($rx = 0; $rx -lt $gw; $rx++) {
        if ($mask[$ry,$rx] -ne 2 -or $seen[$ry,$rx]) { continue }
        $size = 0
        $stack = New-Object System.Collections.Stack
        $stack.Push(@($ry,$rx)); $seen[$ry,$rx] = $true
        while ($stack.Count -gt 0) {
          $cur = $stack.Pop(); $sy = $cur[0]; $sx = $cur[1]; $size++
          foreach ($d in @(@(1,0),@(-1,0),@(0,1),@(0,-1))) {
            $ny = $sy + $d[0]; $nx = $sx + $d[1]
            if ($ny -lt 0 -or $nx -lt 0 -or $ny -ge $gh -or $nx -ge $gw) { continue }
            if ($seen[$ny,$nx] -or $mask[$ny,$nx] -ne 2) { continue }
            $seen[$ny,$nx] = $true; $stack.Push(@($ny,$nx))
          }
        }
        $lightRegions++
        $lightRegionSizes += $size
      }
    }
  }
  $lightLargest = if ($lightRegionSizes.Count -gt 0) { ($lightRegionSizes | Measure-Object -Maximum).Maximum } else { 0 }
  $lightCovered = if ($cnt.light -gt 0) { [math]::Round(100 * $lightLargest / $cnt.light, 1) } else { 0 }

  # corner test: per-channel deviation from #F6F9F6, max over the four corners.
  # Calibrated on the 8 baseline icons (observed max channel deviation = 4),
  # so the gate is <= 8 per channel. Euclidean distance is NOT used here:
  # a neutral #F3F6F3 background is only 3 per channel off but 5.2 euclidean.
  $cornerOk = $true
  $cornerDev = 0
  foreach ($pt in @(@(6,6), @(($w-7),6), @(6,($h-7)), @(($w-7),($h-7)))) {
    $px = $bmp.GetPixel([int]$pt[0], [int]$pt[1])
    $d1 = [math]::Abs([int]$px.R - $BG[0])
    $d2 = [math]::Abs([int]$px.G - $BG[1])
    $d3 = [math]::Abs([int]$px.B - $BG[2])
    $dv = [math]::Max($d1, [math]::Max($d2, $d3))
    if ($dv -gt $cornerDev) { $cornerDev = $dv }
  }
  if ($cornerDev -gt 8) { $cornerOk = $false }
  $bmp.Dispose()

  $nb = $cnt.nonbg
  $pB = if ($nb -gt 0) { [math]::Round(100*$cnt.base/$nb,1) } else { 0 }
  $pL = if ($nb -gt 0) { [math]::Round(100*$cnt.light/$nb,1) } else { 0 }
  $pD = if ($nb -gt 0) { [math]::Round(100*$cnt.deep/$nb,1) } else { 0 }
  $pO = if ($nb -gt 0) { [math]::Round(100*$cnt.other/$nb,1) } else { 0 }

  $bw = if ($nb -gt 0) { $maxX - $minX } else { 0 }
  $bh = if ($nb -gt 0) { $maxY - $minY } else { 0 }
  $extW = [math]::Round(100*$bw/$w,1)
  $extH = [math]::Round(100*$bh/$h,1)
  $marginL = if ($nb -gt 0) { [math]::Round(100*$minX/$w,1) } else { 0 }
  $marginR = if ($nb -gt 0) { [math]::Round(100*($w-1-$maxX)/$w,1) } else { 0 }
  $marginT = if ($nb -gt 0) { [math]::Round(100*$minY/$h,1) } else { 0 }
  $marginB = if ($nb -gt 0) { [math]::Round(100*($h-1-$maxY)/$h,1) } else { 0 }

  $lx = if ($cnt.light -gt 0) { [math]::Round($cx.light / $cnt.light / $w, 3) } else { -1 }
  $ly = if ($cnt.light -gt 0) { [math]::Round($cy.light / $cnt.light / $h, 3) } else { -1 }
  $bx = if ($cnt.base -gt 0) { [math]::Round($cx.base / $cnt.base / $w, 3) } else { -1 }
  $by = if ($cnt.base -gt 0) { [math]::Round($cy.base / $cnt.base / $h, 3) } else { -1 }
  $lightUpLeft = ($lx -ge 0 -and $bx -ge 0 -and $lx -lt $bx -and $ly -lt $by)

  $mBase = if ($cnt.base -gt 0) { @(($sum.base[0]/$cnt.base), ($sum.base[1]/$cnt.base), ($sum.base[2]/$cnt.base)) } else { @(0,0,0) }
  $mLight = if ($cnt.light -gt 0) { @(($sum.light[0]/$cnt.light), ($sum.light[1]/$cnt.light), ($sum.light[2]/$cnt.light)) } else { @(0,0,0) }
  $mDeep = if ($cnt.deep -gt 0) { @(($sum.deep[0]/$cnt.deep), ($sum.deep[1]/$cnt.deep), ($sum.deep[2]/$cnt.deep)) } else { @(0,0,0) }
  $dB = [math]::Round((Dist $mBase $targets[0]),1)
  $dL = [math]::Round((Dist $mLight $targets[1]),1)
  $dD = [math]::Round((Dist $mDeep $targets[2]),1)

  $rows += [pscustomobject]@{
    n = $ic.i; slug = $ic.slug; dom = $ic.dom; batch = $ic.batch
    targetBase = $c[0]; targetLight = $c[1]; targetDeep = $c[2]
    measuredBase = (ToHex $mBase); measuredLight = (ToHex $mLight); measuredDeep = (ToHex $mDeep)
    dB = $dB; dL = $dL; dD = $dD
    pctBase = $pB; pctLight = $pL; pctDeep = $pD; pctOther = $pO
    lightRegions = $lightRegions; lightLargestShare = $lightCovered
    extentW = $extW; extentH = $extH
    marginL = $marginL; marginR = $marginR; marginT = $marginT; marginB = $marginB
    lightCx = $lx; lightCy = $ly; baseCx = $bx; baseCy = $by; lightUpLeft = $lightUpLeft
    cornerOk = $cornerOk; cornerDev = $cornerDev
  }
  Write-Output ('[{0:d2}-{1}] ext {2}x{3}  margin {4}/{5}/{6}/{7}  regions {8} (largest {9}%)  pB/pL/pD/pO {10}/{11}/{12}/{13}  upleft {14}' -f $ic.i,$ic.slug,$extW,$extH,$marginL,$marginR,$marginT,$marginB,$lightRegions,$lightCovered,$pB,$pL,$pD,$pO,$lightUpLeft)
}

$rows | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutJson -Encoding UTF8
Write-Output ("JSON -> " + $OutJson)

