param(
  [string]$Dir = 'H:\EnglishLearningSoftware\docs\design\icon-master',
  [int]$Step = 4,
  [string]$OutJson = (Join-Path $PSScriptRoot 'icon-lift-audit.json')
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

$icons = @(
  @{ i = 1;  slug = 'book';       dom = 'Learn';    batch = 'baseline' },
  @{ i = 2;  slug = 'books';      dom = 'Library';  batch = 'baseline' },
  @{ i = 3;  slug = 'clock';      dom = 'Review';   batch = 'baseline' },
  @{ i = 4;  slug = 'document';   dom = 'Settings'; batch = 'baseline' },
  @{ i = 5;  slug = 'microphone'; dom = 'AiSpeech'; batch = 'baseline' },
  @{ i = 6;  slug = 'speaker';    dom = 'Reading';  batch = 'baseline' },
  @{ i = 7;  slug = 'spark';      dom = 'AiSpeech'; batch = 'recolor'  },
  @{ i = 8;  slug = 'chart';      dom = 'Learn';    batch = 'recolor'  },
  @{ i = 9;  slug = 'camera';     dom = 'Danger';   batch = 'baseline' },
  @{ i = 10; slug = 'shield';     dom = 'Settings'; batch = 'recolor'  },
  @{ i = 11; slug = 'upload';    dom = 'Library';  batch = 'fix'      },
  @{ i = 12; slug = 'download';   dom = 'AiSpeech'; batch = 'baseline' },
  @{ i = 13; slug = 'search';     dom = 'Danger';   batch = 'remake'   },
  @{ i = 14; slug = 'list';       dom = 'Library';  batch = 'remake'   },
  @{ i = 15; slug = 'bookmark';   dom = 'Reading';  batch = 'remake'   },
  @{ i = 16; slug = 'star';       dom = 'Review';   batch = 'remake'   },
  @{ i = 17; slug = 'translate';  dom = 'Learn';    batch = 'remake'   },
  @{ i = 18; slug = 'image';      dom = 'AiSpeech'; batch = 'remake'   },
  @{ i = 19; slug = 'newspaper';  dom = 'Reading';  batch = 'remake'   },
  @{ i = 20; slug = 'gear';       dom = 'Settings'; batch = 'remake'   },
  @{ i = 21; slug = 'person';     dom = 'Settings'; batch = 'remake'   },
  @{ i = 22; slug = 'info';       dom = 'Settings'; batch = 'remake'   },
  @{ i = 23; slug = 'bell';       dom = 'Review';   batch = 'remake'   },
  @{ i = 24; slug = 'warning';    dom = 'Danger';   batch = 'remake'   },
  @{ i = 25; slug = 'trash';      dom = 'Danger';   batch = 'remake'   },
  @{ i = 26; slug = 'play';       dom = 'AiSpeech'; batch = 'remake'   },
  @{ i = 27; slug = 'pause';      dom = 'AiSpeech'; batch = 'remake'   },
  @{ i = 28; slug = 'stop';       dom = 'AiSpeech'; batch = 'remake'   }
)

function Hex2Rgb([string]$hex) {
  $h = $hex.TrimStart('#')
  @([Convert]::ToInt32($h.Substring(0,2),16), [Convert]::ToInt32($h.Substring(2,2),16), [Convert]::ToInt32($h.Substring(4,2),16))
}
function Dist($a, $b) {
  $dr = [double]$a[0]-[double]$b[0]; $dg = [double]$a[1]-[double]$b[1]; $db = [double]$a[2]-[double]$b[2]
  [math]::Sqrt($dr*$dr + $dg*$dg + $db*$db)
}
function Lum($c) { 0.299*[double]$c[0] + 0.587*[double]$c[1] + 0.114*[double]$c[2] }

$BG = @(246, 249, 246)
$rows = @()

foreach ($ic in $icons) {
  $file = Join-Path $Dir ('icon-color-calm__{0:d2}-{1}.png' -f $ic.i, $ic.slug)
  if (-not (Test-Path -LiteralPath $file)) { continue }
  $c = $pal[$ic.dom]
  $targets = @((Hex2Rgb $c[0]), (Hex2Rgb $c[1]), (Hex2Rgb $c[2]))
  $lumBase = Lum $targets[0]
  $lumDeep = Lum $targets[2]
  # "lift" band: clearly lighter than the base mass, clearly darker than the paper background
  $loT = $lumBase + 12
  $hiT = $lumBase + ([math]::Min(($lumDeep - $lumBase) * 0.0, 0))  # unused guard
  $hiT = (Lum $targets[1]) + 26

  $bmp = New-Object System.Drawing.Bitmap($file)
  $w = $bmp.Width; $h = $bmp.Height
  $gw = [int][math]::Floor(($w-1)/$Step) + 1
  $gh = [int][math]::Floor(($h-1)/$Step) + 1
  $mask = New-Object 'int[,]' $gh, $gw
  $nonbg = 0; $lift = 0

  for ($y = 0; $y -lt $h; $y += $Step) {
    $my = [int][math]::Floor($y / $Step)
    for ($x = 0; $x -lt $w; $x += $Step) {
      $mx = [int][math]::Floor($x / $Step)
      $px = $bmp.GetPixel($x, $y)
      $p = @([int]$px.R, [int]$px.G, [int]$px.B)
      $dBg = Dist $p $BG
      if ($dBg -le 14) {
        $bestD0 = [double]::MaxValue
        for ($k = 0; $k -lt 3; $k++) { $d0 = Dist $p $targets[$k]; if ($d0 -lt $bestD0) { $bestD0 = $d0 } }
        if ($dBg -le $bestD0) { continue }
      }
      $nonbg++
      $l = Lum $p
      if ($l -ge $loT -and $l -le $hiT) { $mask[$my,$mx] = 2; $lift++ }
    }
  }

  $regions = 0; $sizes = @()
  if ($lift -gt 0) {
    $seen = New-Object 'bool[,]' $gh, $gw
    for ($ry = 0; $ry -lt $gh; $ry++) {
      for ($rx = 0; $rx -lt $gw; $rx++) {
        if ($mask[$ry,$rx] -ne 2 -or $seen[$ry,$rx]) { continue }
        $size = 0
        $stack = New-Object System.Collections.Stack
        $stack.Push(@($ry,$rx)); $seen[$ry,$rx] = $true
        while ($stack.Count -gt 0) {
          $cur = $stack.Pop(); $sy = [int]$cur[0]; $sx = [int]$cur[1]; $size++
          foreach ($d in @(@(1,0),@(-1,0),@(0,1),@(0,-1))) {
            $ny = $sy + [int]$d[0]; $nx = $sx + [int]$d[1]
            if ($ny -lt 0 -or $nx -lt 0 -or $ny -ge $gh -or $nx -ge $gw) { continue }
            if ($seen[$ny,$nx] -or $mask[$ny,$nx] -ne 2) { continue }
            $seen[$ny,$nx] = $true; $stack.Push(@($ny,$nx))
          }
        }
        $regions++; $sizes += $size
      }
    }
  }
  $bmp.Dispose()

  $largest = if ($sizes.Count -gt 0) { ($sizes | Measure-Object -Maximum).Maximum } else { 0 }
  $pctLift = if ($nonbg -gt 0) { [math]::Round(100*$lift/$nonbg, 1) } else { 0 }
  $largestShareOfNonBg = if ($nonbg -gt 0) { [math]::Round(100*$largest/$nonbg, 1) } else { 0 }

  $rows += [pscustomobject]@{
    n = $ic.i; slug = $ic.slug; dom = $ic.dom; batch = $ic.batch
    pctLift = $pctLift; liftRegions = $regions; liftLargestShareOfNonBg = $largestShareOfNonBg
  }
  Write-Output ('[{0:d2}-{1}] lift {2}%  regions {3}  largest {4}% of body' -f $ic.i, $ic.slug, $pctLift, $regions, $largestShareOfNonBg)
}

$rows | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutJson -Encoding UTF8

$b = @($rows | Where-Object { $_.batch -eq 'baseline' })
Write-Output ''
Write-Output ('baseline pctLift   : ' + (($b | Measure-Object pctLift -Minimum).Minimum) + ' .. ' + (($b | Measure-Object pctLift -Maximum).Maximum))
Write-Output ('baseline largest   : ' + (($b | Measure-Object liftLargestShareOfNonBg -Minimum).Minimum) + ' .. ' + (($b | Measure-Object liftLargestShareOfNonBg -Maximum).Maximum))
Write-Output ('baseline regions   : ' + (($b | Measure-Object liftRegions -Minimum).Minimum) + ' .. ' + (($b | Measure-Object liftRegions -Maximum).Maximum))
Write-Output ("JSON -> " + $OutJson)
