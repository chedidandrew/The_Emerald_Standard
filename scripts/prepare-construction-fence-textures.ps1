# Mechanical nearest-neighbor import of the approved generated swatches.
# Creative material design is kept in art/construction-fence/*-master.png.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$tesRoot = Split-Path -Parent $PSScriptRoot
$tesArt = Join-Path $tesRoot 'art/construction-fence'
$tesTextures = Join-Path $tesRoot 'common/src/main/resources/assets/the_emerald_standard/textures/block'
New-Item -ItemType Directory -Path $tesTextures -Force | Out-Null
foreach ($tesMaterial in @('yellow', 'black')) {
    $tesInput = [Drawing.Bitmap]::new((Join-Path $tesArt "$tesMaterial-master.png"))
    $tesOutput = [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($tesY = 0; $tesY -lt 16; $tesY++) {
            for ($tesX = 0; $tesX -lt 16; $tesX++) {
                $tesSampleX = [int][Math]::Floor(($tesX + 0.5) * $tesInput.Width / 16)
                $tesSampleY = [int][Math]::Floor(($tesY + 0.5) * $tesInput.Height / 16)
                $tesColor = $tesInput.GetPixel($tesSampleX, $tesSampleY)
                $tesOutput.SetPixel($tesX, $tesY, [Drawing.Color]::FromArgb(255, $tesColor.R, $tesColor.G, $tesColor.B))
            }
        }
        $tesOutput.Save((Join-Path $tesTextures "construction_fence_$tesMaterial.png"), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $tesOutput.Dispose()
        $tesInput.Dispose()
    }
}
