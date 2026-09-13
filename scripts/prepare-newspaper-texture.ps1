param(
    [Parameter(Mandatory=$true)][string]$SourcePath,
    [ValidateSet(32,64,128,256)][int]$Size = 256,
    [ValidateSet('newspaper','newspaper_open')][string]$AssetName = 'newspaper',
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$tesSource = [System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $SourcePath).Path)
$tesSprite = [System.Drawing.Bitmap]::new($Size,$Size,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    # Uniform nearest-neighbor sizing: a non-square cutout must not be squeezed to a square.
    $tesFactor=$Size/[Math]::Max($tesSource.Width,$tesSource.Height)
    $tesLeft=($Size-$tesSource.Width*$tesFactor)/2
    $tesTop=($Size-$tesSource.Height*$tesFactor)/2
    $tesClear=0
    for ($tesY=0; $tesY -lt $Size; $tesY++) {
        for ($tesX=0; $tesX -lt $Size; $tesX++) {
            $tesSX=[int][Math]::Floor(($tesX+0.5-$tesLeft)/$tesFactor)
            $tesSY=[int][Math]::Floor(($tesY+0.5-$tesTop)/$tesFactor)
            if($tesSX -lt 0 -or $tesSY -lt 0 -or $tesSX -ge $tesSource.Width -or $tesSY -ge $tesSource.Height) { $tesClear++; continue }
            if($tesSource.GetPixel($tesSX,$tesSY).A -eq 0) { $tesClear++ }
            $tesSprite.SetPixel($tesX,$tesY,$tesSource.GetPixel($tesSX,$tesSY))
        }
    }
    if($tesClear -lt $Size*$Size*0.15) { throw 'Newspaper source has no usable transparent exterior; refusing a baked background.' }
    $tesAssetDir=Join-Path $RepositoryRoot 'common/src/main/resources/assets/the_emerald_standard/textures/item'
    [void][IO.Directory]::CreateDirectory($tesAssetDir)
    $tesSprite.Save((Join-Path $tesAssetDir ($AssetName+'.png')),[System.Drawing.Imaging.ImageFormat]::Png)
    $tesPreviewDir=Join-Path $RepositoryRoot 'build/newspaper-art'
    [void][IO.Directory]::CreateDirectory($tesPreviewDir)
    $tesPreview=[System.Drawing.Bitmap]::new(384,384,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($tesY=0; $tesY -lt 384; $tesY++) {
            for ($tesX=0; $tesX -lt 384; $tesX++) {
                $tesPreview.SetPixel($tesX,$tesY,$tesSprite.GetPixel([int][Math]::Floor($tesX*$Size/384),[int][Math]::Floor($tesY*$Size/384)))
            }
        }
        $tesPreview.Save((Join-Path $tesPreviewDir ($AssetName+'-preview.png')),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $tesPreview.Dispose() }
    Write-Output "Prepared $Size px RGBA $AssetName texture and nearest-neighbor preview."
} finally { $tesSprite.Dispose(); $tesSource.Dispose() }
