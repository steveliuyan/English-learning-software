param(
  [string]$FullJson  = (Join-Path $PSScriptRoot 'icon-full-audit.json'),
  [string]$LiftJson  = (Join-Path $PSScriptRoot 'icon-lift-audit.json'),
  [string]$OutJson   = (Join-Path $PSScriptRoot 'icon-verdict.json')
)

# 把 STYLE-CONTRACT.md「数字化的判据（v3）」逐条落成可执行判定。
# 放行区间全部由基准 8 枚（01 02 03 04 05 06 09 12）的实测分布反推，见合同文件。
# 只读 audit-full.ps1 / audit-lift.ps1 的产物，不自己读图。

$ErrorActionPreference = 'Stop'

$gate = @{
  cornerDevMax   = 8      # 四角每通道与 #F6F9F6 之差
  minMarginMin   = 10     # 四边最小留白 %
  maxSideMin     = 24     # 主体包围盒长边 %
  maxSideMax     = 78
  pctBaseMin     = 25     # 主面簇占比 %
  pctDeepMin     = 8      # 暗色簇占比 %
  pctDeepMax     = 46
  pctOtherMax    = 36     # 域外色占比 %
  dBMax          = 42     # 主面色距
  dLMax          = 45     # 亮面色距
  dDMax          = 45     # 暗色色距
  pctLiftMin     = 2.5    # 抬亮带存在性 %
  liftLargestMin = 2      # 最大连通块占体量 %
}

$full = Get-Content -LiteralPath $FullJson -Raw -Encoding UTF8 | ConvertFrom-Json
$lift = @{}
foreach ($r in (Get-Content -LiteralPath $LiftJson -Raw -Encoding UTF8 | ConvertFrom-Json)) {
  $lift[[int]$r.n] = $r
}

$out = @()
foreach ($f in $full) {
  if ($f.status -eq 'missing-file') {
    $out += [pscustomobject]@{ n = $f.n; slug = $f.slug; dom = $f.dom; batch = $f.batch; verdict = 'MISSING'; why = 'png not found' }
    continue
  }
  $n = [int]$f.n
  $l = $lift[$n]
  $maxSide = [math]::Max([double]$f.extentW, [double]$f.extentH)
  $minMargin = @([double]$f.marginL, [double]$f.marginR, [double]$f.marginT, [double]$f.marginB) |
              Measure-Object -Minimum | Select-Object -ExpandProperty Minimum
  $pctLift = if ($null -ne $l) { [double]$l.pctLift } else { 0 }
  $liftLargest = if ($null -ne $l) { [double]$l.liftLargestShareOfNonBg } else { 0 }

  $why = @()
  if ([double]$f.cornerDev -gt $gate.cornerDevMax) { $why += ('corner {0}>{1}' -f $f.cornerDev, $gate.cornerDevMax) }
  if ($minMargin -lt $gate.minMarginMin) { $why += ('margin {0}<{1}' -f $minMargin, $gate.minMarginMin) }
  if ($maxSide -lt $gate.maxSideMin) { $why += ('side {0}<{1}' -f $maxSide, $gate.maxSideMin) }
  if ($maxSide -gt $gate.maxSideMax) { $why += ('side {0}>{1}' -f $maxSide, $gate.maxSideMax) }
  if ([double]$f.pctBase -lt $gate.pctBaseMin) { $why += ('base {0}%<{1}' -f $f.pctBase, $gate.pctBaseMin) }
  if ([double]$f.pctDeep -lt $gate.pctDeepMin) { $why += ('deep {0}%<{1}' -f $f.pctDeep, $gate.pctDeepMin) }
  if ([double]$f.pctDeep -gt $gate.pctDeepMax) { $why += ('deep {0}%>{1}' -f $f.pctDeep, $gate.pctDeepMax) }
  if ([double]$f.pctOther -gt $gate.pctOtherMax) { $why += ('offpalette {0}%>{1}' -f $f.pctOther, $gate.pctOtherMax) }
  if ([double]$f.dB -gt $gate.dBMax) { $why += ('dB {0}>{1}' -f $f.dB, $gate.dBMax) }
  if ([double]$f.dL -gt $gate.dLMax) { $why += ('dL {0}>{1}' -f $f.dL, $gate.dLMax) }
  if ([double]$f.dD -gt $gate.dDMax) { $why += ('dD {0}>{1}' -f $f.dD, $gate.dDMax) }
  if ($pctLift -lt $gate.pctLiftMin) { $why += ('lift {0}%<{1}' -f $pctLift, $gate.pctLiftMin) }
  if ($liftLargest -lt $gate.liftLargestMin) { $why += ('liftLargest {0}%<{1}' -f $liftLargest, $gate.liftLargestMin) }

  $out += [pscustomobject]@{
    n = $n; slug = $f.slug; dom = $f.dom; batch = $f.batch
    cornerDev = $f.cornerDev; minMargin = $minMargin; maxSide = $maxSide
    pctBase = $f.pctBase; pctLift = $pctLift; liftLargest = $liftLargest
    pctDeep = $f.pctDeep; pctOther = $f.pctOther
    dB = $f.dB; dL = $f.dL; dD = $f.dD
    measuredBase = $f.measuredBase; measuredLight = $f.measuredLight; measuredDeep = $f.measuredDeep
    targetBase = $f.targetBase; targetLight = $f.targetLight; targetDeep = $f.targetDeep
    verdict = if ($why.Count -eq 0) { 'pass' } else { 'FAIL' }
    why = ($why -join '; ')
  }
}

$out | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutJson -Encoding UTF8

$pass = @($out | Where-Object { $_.verdict -eq 'pass' })
$bad  = @($out | Where-Object { $_.verdict -ne 'pass' })
Write-Output ('PASS {0} / {1}' -f $pass.Count, $out.Count)
foreach ($b in $bad) { Write-Output ('  FAIL [{0:d2}-{1}] {2}: {3}' -f $b.n, $b.slug, $b.dom, $b.why) }
Write-Output ("JSON -> " + $OutJson)
