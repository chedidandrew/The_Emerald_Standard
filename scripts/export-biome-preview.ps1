[CmdletBinding()]
param([Parameter(Mandatory)][string]$SourceDirectory,
      [Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$previewSource = [IO.Path]::GetFullPath($SourceDirectory)
$previewOutput = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $previewOutput) { throw "Refusing existing output: $previewOutput" }
if (-not (Test-Path -LiteralPath (Join-Path $previewSource 'complete.txt'))) { throw 'Incomplete native capture' }
$previewRows = @(Import-Csv -LiteralPath (Join-Path $previewSource 'manifest.csv'))
if ($previewRows.Count -ne 65) { throw 'Expected 13 prototypes with five views each' }
foreach ($previewRow in $previewRows) {
    if ($previewRow.file -notmatch '^\d{3}-(pair|mod|vanilla|interior-entry|interior-reverse)\.png$') {
        throw "Unexpected native filename: $($previewRow.file)"
    }
    $previewNative = Join-Path $previewSource $previewRow.file
    if ((Get-FileHash -LiteralPath $previewNative -Algorithm SHA256).Hash -ne $previewRow.sha256) {
        throw "Native checksum mismatch: $previewNative"
    }
}
[void](New-Item -ItemType Directory -Path $previewOutput)
$previewCodec = [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$previewParameters = [Drawing.Imaging.EncoderParameters]::new(1)
$previewParameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new([Drawing.Imaging.Encoder]::Quality, [long]90)
$previewEvidence = foreach ($previewRow in $previewRows) {
    $previewImage = [Drawing.Image]::FromFile((Join-Path $previewSource $previewRow.file))
    try {
        if ($previewImage.Width -ne 1920 -or $previewImage.Height -ne 1080) { throw 'Unexpected native viewport' }
        $previewFilename = [IO.Path]::ChangeExtension($previewRow.file, '.jpg')
        $previewFile = Join-Path $previewOutput $previewFilename
        $previewImage.Save($previewFile, $previewCodec, $previewParameters)
        [pscustomobject]@{pair=$previewRow.pair; view=$previewRow.view; file=$previewFilename;
            source_png_sha256=$previewRow.sha256; jpeg_sha256=(Get-FileHash -LiteralPath $previewFile).Hash.ToLowerInvariant();
            eye_x=$previewRow.eye_x; eye_y=$previewRow.eye_y; eye_z=$previewRow.eye_z;
            yaw=$previewRow.yaw; pitch=$previewRow.pitch; fov=$previewRow.fov}
    } finally { $previewImage.Dispose() }
}
$previewEvidence | Export-Csv -LiteralPath (Join-Path $previewOutput 'capture-evidence.csv') -NoTypeInformation
$previewParameters.Dispose()

function New-PreviewSheet([string]$Name, [string[]]$Files, [string[]]$Labels, [bool]$CropExterior) {
    $previewTileWidth=660; $previewTileHeight=410
    $previewSheet=[Drawing.Bitmap]::new(1320, $previewTileHeight * [int][Math]::Ceiling($Files.Count/2))
    $previewGraphics=[Drawing.Graphics]::FromImage($previewSheet)
    $previewFont=[Drawing.Font]::new('Segoe UI',18,[Drawing.FontStyle]::Bold)
    try {
        $previewGraphics.Clear([Drawing.Color]::FromArgb(24,36,30))
        $previewGraphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        for ($previewIndex=0; $previewIndex -lt $Files.Count; $previewIndex++) {
            $previewX=($previewIndex%2)*$previewTileWidth
            $previewY=[int][Math]::Floor($previewIndex/2)*$previewTileHeight
            $previewGraphics.DrawString($Labels[$previewIndex],$previewFont,[Drawing.Brushes]::White,$previewX+14,$previewY+8)
            $previewImage=[Drawing.Image]::FromFile((Join-Path $previewOutput $Files[$previewIndex]))
            try {
                $previewCrop=if ($CropExterior) { [Drawing.Rectangle]::new(600,260,720,405) }
                             else { [Drawing.Rectangle]::new(0,0,1920,1080) }
                $previewDestination=[Drawing.Rectangle]::new($previewX+10,$previewY+44,640,360)
                $previewGraphics.DrawImage($previewImage,$previewDestination,$previewCrop,[Drawing.GraphicsUnit]::Pixel)
            } finally { $previewImage.Dispose() }
        }
        $previewSheet.Save((Join-Path $previewOutput $Name),[Drawing.Imaging.ImageFormat]::Png)
    } finally { $previewFont.Dispose(); $previewGraphics.Dispose(); $previewSheet.Dispose() }
}
New-PreviewSheet 'homes-and-banks.png' @('001-mod.jpg','002-mod.jpg','004-mod.jpg','005-mod.jpg','008-mod.jpg','009-mod.jpg','010-mod.jpg','011-mod.jpg','012-mod.jpg','013-mod.jpg') @('Plains home','Plains Bank','Desert home','Desert Bank','Savanna home','Savanna Bank','Taiga home','Taiga Bank','Snowy home','Snowy Bank') $true
New-PreviewSheet 'interiors.png' @('001-interior-entry.jpg','002-interior-entry.jpg','004-interior-entry.jpg','005-interior-entry.jpg','006-interior-entry.jpg','007-interior-entry.jpg') @('Plains: hearth, dining, beds','Plains: teller hall','Desert: hearth home','Desert: teller hall','Desert: guesthouse taproom','Desert: forge work range') $false
New-PreviewSheet 'plains-oak-roofs.png' @('001-mod.jpg','002-mod.jpg','003-mod.jpg','001-vanilla.jpg') @('TES Plains house: oak roof','TES Plains Bank: oak roof','TES Plains inn: oak roof','Actual vanilla Plains reference') $true
Write-Output "Exported 65 checksum-verified native views as JPEGs and three labeled contact sheets to $previewOutput"
