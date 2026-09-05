param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$regions = [ordered]@{
    top = @(
        '00000000',
        '01233210',
        '01244210',
        '01255210',
        '01122110',
        '00000000'
    )
    bottom = @(
        '00000000',
        '01122110',
        '01122110',
        '01122110',
        'DDDDDDDD',
        '00000000'
    )
    right = @(
        '000000',
        '011110',
        '012230',
        '012340',
        '012340',
        '012450',
        '012450',
        '012450',
        '012450',
        '012450',
        '012450',
        '012450',
        '012450',
        '012450',
        '012450',
        '012450',
        '012340',
        '011230',
        '000000',
        'DDDDDD'
    )
    front = @(
        '01888810',
        '02999920',
        '03899830',
        '04388340',
        '05477450',
        '05477450',
        '064B3460',
        '05423550',
        '064234B0',
        '054B35B0',
        '054234C0',
        '06423B50',
        '054B3460',
        '05423550',
        '06423450',
        '054B3450',
        '05423460',
        '04322340',
        '00000000',
        'DDDDDDDD'
    )
    left = @(
        '000000',
        '011110',
        '032210',
        '043210',
        '043210',
        '054210',
        '054210',
        '054210',
        '054210',
        '054210',
        '054210',
        '054210',
        '054210',
        '054210',
        '054210',
        '054210',
        '043210',
        '032110',
        '000000',
        'DDDDDD'
    )
    back = @(
        '00000000',
        '01111110',
        '01255210',
        '01344310',
        '01344310',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01433410',
        '01322310',
        '01222210',
        '00000000',
        'DDDDDDDD'
    )
}

$placements = [ordered]@{
    top = @(6, 38)
    bottom = @(14, 38)
    right = @(0, 44)
    front = @(6, 44)
    left = @(14, 44)
    back = @(20, 44)
}

$villagerPalette = [ordered]@{
    '0' = '#031A14'
    '1' = '#05271D'
    '2' = '#073728'
    '3' = '#0A4932'
    '4' = '#0F6040'
    '5' = '#187450'
    '6' = '#25875B'
    '7' = '#B7A679'
    '8' = '#E2D3A5'
    '9' = '#F5EAC8'
    'A' = '#8F6115'
    'B' = '#D7A62C'
    'C' = '#F1D15B'
    'D' = '#202722'
}

$zombiePalette = [ordered]@{
    '0' = '#12251F'
    '1' = '#1B3028'
    '2' = '#274438'
    '3' = '#315448'
    '4' = '#3D6656'
    '5' = '#527A68'
    '6' = '#698C78'
    '7' = '#8F876B'
    '8' = '#B2A680'
    '9' = '#CBC19B'
    'A' = '#67521D'
    'B' = '#A58629'
    'C' = '#C4A846'
    'D' = '#2D302B'
}

$zombieTears = [System.Collections.Generic.HashSet[string]]::new([string[]]@(
    '1,52', '2,52', '1,53',
    '18,57', '17,58', '18,58',
    '25,60', '26,60', '26,61'
))

function Convert-HexColor([string]$Hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function Write-BankerTexture(
    [string]$RelativePath,
    [System.Collections.IDictionary]$Palette,
    [System.Collections.Generic.HashSet[string]]$TransparentPixels
) {
    $outputPath = Join-Path $RepositoryRoot $RelativePath
    $outputDirectory = Split-Path -Parent $outputPath
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

    $bitmap = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        foreach ($regionName in $regions.Keys) {
            $origin = $placements[$regionName]
            $rows = $regions[$regionName]
            for ($row = 0; $row -lt $rows.Count; $row++) {
                for ($column = 0; $column -lt $rows[$row].Length; $column++) {
                    $x = $origin[0] + $column
                    $y = $origin[1] + $row
                    if ($null -ne $TransparentPixels -and $TransparentPixels.Contains("$x,$y")) {
                        continue
                    }
                    $symbol = [string]$rows[$row][$column]
                    $bitmap.SetPixel($x, $y, (Convert-HexColor $Palette[$symbol]))
                }
            }
        }
        $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $bitmap.Dispose()
    }
}

Write-BankerTexture `
    'common/src/main/resources/assets/the_emerald_standard/textures/entity/villager/profession/banker.png' `
    $villagerPalette `
    $null

Write-BankerTexture `
    'common/src/main/resources/assets/the_emerald_standard/textures/entity/zombie_villager/profession/banker.png' `
    $zombiePalette `
    $zombieTears

Write-Host 'Generated detailed villager and zombie-villager Banker textures.'
