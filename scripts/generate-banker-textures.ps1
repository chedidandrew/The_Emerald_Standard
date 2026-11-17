param([string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Exact Minecraft 26.2 profession UVs. FULL hat metadata hides biome hats;
# only glasses are opaque on the hat, preserving native skin and the bald scalp.
$front = @(
    '12899821','127BB721','123BC321','124AB421','124BC421',
    '124BC421','123BC321','123BC321','124AB421','124BB421',
    '125AA521','12544521','12544521','12544521','12544521',
    '12544521','12344321','12322321','01122110','DDDDDDDD'
)
$villagerPalette = @{
    '0'='#111419'; '1'='#181D23'; '2'='#20262D'; '3'='#293139'
    '4'='#313A43'; '5'='#3A454F'; '6'='#465360'; '7'='#BAC7CD'
    '8'='#E0E8E9'; '9'='#F7FAF6'; 'A'='#064C35'; 'B'='#078C50'
    'C'='#21C779'; 'D'='#0C1014'; 'G'='#05090D'; 'H'='#526975'
}
$zombiePalette = @{
    '0'='#161D1C'; '1'='#1C2726'; '2'='#273333'; '3'='#354444'
    '4'='#3E5150'; '5'='#4C605D'; '6'='#5C706B'; '7'='#98AB9A'
    '8'='#BEC9B3'; '9'='#D7DDC3'; 'A'='#153F2C'; 'B'='#376B43'
    'C'='#619664'; 'D'='#111918'; 'G'='#0B1414'; 'H'='#53685E'
}
$zombieTears = [System.Collections.Generic.HashSet[string]]::new([string[]]@(
    '1,52','2,52','1,53','18,57','17,58','18,58','25,60','26,60','26,61'
))
function Paint-Rows($Bitmap,$Palette,[int]$OriginX,[int]$OriginY,[string[]]$Rows,$Tears) {
    for ($row=0; $row -lt $Rows.Count; $row++) {
        for ($column=0; $column -lt $Rows[$row].Length; $column++) {
            $symbol=[string]$Rows[$row][$column]
            $x=$OriginX+$column; $y=$OriginY+$row
            if ($symbol -eq '.' -or ($null -ne $Tears -and $Tears.Contains("$x,$y"))) { continue }
            $Bitmap.SetPixel($x,$y,[System.Drawing.ColorTranslator]::FromHtml($Palette[$symbol]))
        }
    }
}
function Write-BankerTexture([string]$Kind,$Palette,[bool]$Zombie) {
    $bitmap=[System.Drawing.Bitmap]::new(64,64,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $tears=if ($Zombie) { $zombieTears } else { $null }
    try {
        # Charcoal jacket, white shirt and emerald tie.
        Paint-Rows $bitmap $Palette 6 38 @('00000000','01233210','01244210','01255210','01122110','00000000') $tears
        Paint-Rows $bitmap $Palette 14 38 @('00000000','01122110','01122110','01122110','DDDDDDDD','00000000') $tears
        Paint-Rows $bitmap $Palette 0 44 (@('000000','011110','012230','012340')+@('012450')*12+@('012340','011230','000000','DDDDDD')) $tears
        Paint-Rows $bitmap $Palette 6 44 $front $tears
        Paint-Rows $bitmap $Palette 14 44 (@('000000','011110','032210','043210')+@('054210')*12+@('043210','032110','000000','DDDDDD')) $tears
        Paint-Rows $bitmap $Palette 20 44 (@('00000000','01111110','01255210','01344310')+@('01466410')*12+@('01322310','01222210','00000000','DDDDDDDD')) $tears
        # Body beneath the jacket; trousers and black shoes hide biome clothing.
        Paint-Rows $bitmap $Palette 22 20 (@('12222221')*6) $null
        Paint-Rows $bitmap $Palette 30 20 (@('12222221')*6) $null
        Paint-Rows $bitmap $Palette 16 26 (@('122221')*12) $null
        Paint-Rows $bitmap $Palette 22 26 $front[0..11] $null
        Paint-Rows $bitmap $Palette 30 26 (@('122221')*12) $null
        Paint-Rows $bitmap $Palette 36 26 (@('12344321')*12) $null
        Paint-Rows $bitmap $Palette 4 22 (@('1221')*4) $null
        Paint-Rows $bitmap $Palette 8 22 (@('DDDD')*4) $null
        Paint-Rows $bitmap $Palette 0 26 (@('1221122112211221')*10+@('DDDDDDDDDDDDDDDD')*2) $null
        # Mirrored arms; zombie sleeves use the native longer arm UVs.
        Paint-Rows $bitmap $Palette 48 22 (@('2332')*4) $null
        Paint-Rows $bitmap $Palette 52 22 (@('2332')*4) $null
        $sleeveRows=if ($Zombie) { 8 } else { 6 }
        Paint-Rows $bitmap $Palette 44 26 (@('1231123112311231')*$sleeveRows+@('7887788778877887','8998899889988998')) $null
        if (-not $Zombie) {
            # The biome layer also paints the folded forearm bridge; cover it with suit fabric.
            Paint-Rows $bitmap $Palette 44 38 (@('18233281')*4) $null
            Paint-Rows $bitmap $Palette 52 38 (@('18233281')*4) $null
            Paint-Rows $bitmap $Palette 40 42 (@('1231')*4) $null
            Paint-Rows $bitmap $Palette 44 42 (@('18233281')*4) $null
            Paint-Rows $bitmap $Palette 52 42 (@('1321')*4) $null
            Paint-Rows $bitmap $Palette 56 42 (@('18233281')*4) $null
        }
        # Glasses align with native eye row 5; nose, brow and scalp remain clear.
        Paint-Rows $bitmap $Palette 40 13 @('GGGGGGGG','GHGGGGHG','GGG..GGG') $null
        Paint-Rows $bitmap $Palette 32 13 @('GGGGGGGG') $null
        Paint-Rows $bitmap $Palette 48 13 @('GGGGGGGG') $null
        $path=Join-Path $RepositoryRoot "common/src/main/resources/assets/the_emerald_standard/textures/entity/$Kind/profession/banker.png"
        $bitmap.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $bitmap.Dispose() }
}
Write-BankerTexture 'villager' $villagerPalette $false
Write-BankerTexture 'zombie_villager' $zombiePalette $true
Write-Host 'Generated icon-matching Banker suit, emerald tie, cuffs and sunglasses in native UVs.'
