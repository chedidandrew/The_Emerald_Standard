[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,

    [string] $OutputDirectory,

    [switch] $RequireCompleteReviewSet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$manifestFile = (Resolve-Path -LiteralPath $ManifestPath).Path
$captureDirectory = Split-Path -Parent $manifestFile
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $captureDirectory 'carol-contact-sheets'
}
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$captureDirectoryRoot = [System.IO.Path]::GetFullPath($captureDirectory)
$directorySeparator = [System.IO.Path]::DirectorySeparatorChar
$captureDirectoryPrefix = if ($captureDirectoryRoot.EndsWith([string]$directorySeparator)) {
    $captureDirectoryRoot
} else {
    $captureDirectoryRoot + $directorySeparator
}

$rows = @(Import-Csv -LiteralPath $manifestFile)
if ($rows.Count -eq 0) {
    throw "Capture manifest contains no rows: $manifestFile"
}

$requiredColumns = @(
    'sequence',
    'coverage',
    'gallery_index',
    'view',
    'vertical_fov',
    'capture_context',
    'pixel_width',
    'pixel_height',
    'subject',
    'dialect',
    'doodads',
    'doodad_focus',
    'doodad_source_view',
    'filename',
    'status')
$availableColumns = @($rows[0].PSObject.Properties.Name)
$missingColumns = @($requiredColumns | Where-Object { $_ -notin $availableColumns })
if ($missingColumns.Count -gt 0) {
    throw "Capture manifest is missing required columns: $($missingColumns -join ', ')"
}

$allowedCaptureContexts = @('gallery', 'isolated-clone')
$allowedIsolatedCoverage = @('master', 'bank')
$allowedIsolatedViews = @('front-day', 'rear-doodads-day')
$decodedDimensionPairs = [System.Collections.Generic.HashSet[string]]::new()

foreach ($row in $rows) {
    if ($row.status -ne 'captured') {
        throw "Capture row $($row.sequence) is not complete: $($row.status)"
    }
    $captureContextIsInvalid =
        [string]::IsNullOrWhiteSpace([string]$row.capture_context) -or
        $row.capture_context -notin $allowedCaptureContexts
    if ($captureContextIsInvalid) {
        throw "Capture row $($row.sequence) has invalid context '$($row.capture_context)'"
    }
    $isolatedEvidenceIsInvalid =
        $row.capture_context -eq 'isolated-clone' -and
        ($row.coverage -notin $allowedIsolatedCoverage -or
            $row.view -notin $allowedIsolatedViews)
    if ($isolatedEvidenceIsInvalid) {
        throw "Capture row $($row.sequence) uses an isolated clone for ineligible evidence: $($row.coverage)/$($row.view)"
    }

    [double]$verticalFov = 0
    $verticalFovIsNumeric = [double]::TryParse(
        [string]$row.vertical_fov,
        [System.Globalization.NumberStyles]::Float,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref]$verticalFov)
    if (!$verticalFovIsNumeric -or $verticalFov -lt 50.0 -or $verticalFov -gt 70.0) {
        throw "Capture row $($row.sequence) has invalid vertical FOV '$($row.vertical_fov)'"
    }

    [int]$manifestWidth = 0
    [int]$manifestHeight = 0
    $manifestWidthIsNumeric = [int]::TryParse(
        [string]$row.pixel_width,
        [ref]$manifestWidth)
    $manifestHeightIsNumeric = [int]::TryParse(
        [string]$row.pixel_height,
        [ref]$manifestHeight)
    if (!$manifestWidthIsNumeric -or !$manifestHeightIsNumeric -or
            $manifestWidth -lt 1920 -or $manifestHeight -lt 1080) {
        throw "Capture row $($row.sequence) does not declare at least 1920x1080 pixels: $($row.pixel_width)x$($row.pixel_height)"
    }
    if ([long]$manifestWidth * 9 -ne [long]$manifestHeight * 16) {
        throw "Capture row $($row.sequence) is not 16:9: $($manifestWidth)x$($manifestHeight)"
    }

    $filenameIsPng = [System.IO.Path]::GetExtension([string]$row.filename).Equals(
        '.png',
        [System.StringComparison]::OrdinalIgnoreCase)
    if ([string]::IsNullOrWhiteSpace([string]$row.filename) -or !$filenameIsPng) {
        throw "Capture row $($row.sequence) does not name a PNG: '$($row.filename)'"
    }
    $imagePath = [System.IO.Path]::GetFullPath(
        (Join-Path $captureDirectory ([string]$row.filename)))
    if (!$imagePath.StartsWith(
            $captureDirectoryPrefix,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Capture row $($row.sequence) escapes its capture directory: '$($row.filename)'"
    }
    if (!(Test-Path -LiteralPath $imagePath -PathType Leaf)) {
        throw "Capture row $($row.sequence) is missing its PNG: $imagePath"
    }
    $image = [System.Drawing.Image]::FromFile($imagePath)
    try {
        $decodedWidth = $image.Width
        $decodedHeight = $image.Height
    } finally {
        $image.Dispose()
    }
    if ($decodedWidth -ne $manifestWidth -or $decodedHeight -ne $manifestHeight) {
        throw "Capture row $($row.sequence) manifest dimensions $($manifestWidth)x$($manifestHeight) do not match decoded PNG $($decodedWidth)x$($decodedHeight)"
    }
    [void]$decodedDimensionPairs.Add("$decodedWidth`x$decodedHeight")
    $row | Add-Member -NotePropertyName ImagePath -NotePropertyValue $imagePath
}

if ($decodedDimensionPairs.Count -ne 1) {
    throw "Capture PNG dimensions are not uniform: $(@($decodedDimensionPairs) -join ', ')"
}

$duplicateSequences = @($rows | Group-Object sequence | Where-Object Count -ne 1)
if ($duplicateSequences.Count -gt 0) {
    throw "Capture manifest contains duplicate sequence values: $($duplicateSequences.Name -join ', ')"
}
$duplicateFilenames = @($rows | Group-Object filename | Where-Object Count -ne 1)
if ($duplicateFilenames.Count -gt 0) {
    throw "Capture manifest contains duplicate filenames: $($duplicateFilenames.Name -join ', ')"
}

if ($RequireCompleteReviewSet) {
    $completionMarkerPath = Join-Path $captureDirectory 'capture-complete.txt'
    if (!(Test-Path -LiteralPath $completionMarkerPath -PathType Leaf)) {
        throw "Complete Carol review requires a capture completion marker: $completionMarkerPath"
    }
    $completionMarker = @{}
    foreach ($line in Get-Content -LiteralPath $completionMarkerPath) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $parts = $line -split '=', 2
        if ($parts.Count -ne 2 -or [string]::IsNullOrWhiteSpace($parts[0])) {
            throw "Malformed capture completion marker line: $line"
        }
        if ($completionMarker.ContainsKey($parts[0])) {
            throw "Duplicate capture completion marker key: $($parts[0])"
        }
        $completionMarker[$parts[0]] = $parts[1]
    }
    $requiredMarkerKeys = @(
        'layout_signature',
        'gallery_content_revision',
        'capture_schema_revision',
        'shot_count',
        'pixel_width',
        'pixel_height',
        'completed_at')
    $missingMarkerKeys = @(
        $requiredMarkerKeys | Where-Object { !$completionMarker.ContainsKey($_) }
    )
    if ($missingMarkerKeys.Count -gt 0) {
        throw "Capture completion marker is missing keys: $($missingMarkerKeys -join ', ')"
    }

    $captureRootName = Split-Path -Leaf (Split-Path -Parent $captureDirectory)
    if ($captureRootName -notmatch '^tes-structure-review-([0-9a-f]+)-capture-v3$') {
        throw "Capture root does not encode a schema-3 layout signature: $captureRootName"
    }
    $directorySignature = $Matches[1]
    if ($completionMarker.layout_signature -ne $directorySignature) {
        throw "Completion marker signature '$($completionMarker.layout_signature)' does not match capture root '$directorySignature'"
    }
    $completionContractIsInvalid =
        $completionMarker.gallery_content_revision -ne '9' -or
        $completionMarker.capture_schema_revision -ne '3' -or
        $completionMarker.shot_count -ne '267'
    if ($completionContractIsInvalid) {
        throw "Completion marker does not describe the required rev9/schema3/267-shot review set"
    }
    [datetimeoffset]$completedAt = [datetimeoffset]::MinValue
    if (![datetimeoffset]::TryParse(
            [string]$completionMarker.completed_at,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$completedAt)) {
        throw "Completion marker has an invalid completed_at instant: '$($completionMarker.completed_at)'"
    }
    $uniformDimensions = @($decodedDimensionPairs)[0]
    if ("$($completionMarker.pixel_width)x$($completionMarker.pixel_height)" -ne $uniformDimensions) {
        throw "Completion marker dimensions $($completionMarker.pixel_width)x$($completionMarker.pixel_height) do not match decoded PNG dimensions $uniformDimensions"
    }

    $expectedCoverage = [ordered]@{
        master = 181
        bank = 20
        doodad = 16
        cohesion = 50
    }
    if ($rows.Count -ne 267) {
        throw "Complete Carol review requires exactly 267 captures; found $($rows.Count)"
    }
    $parsedSequences = @()
    foreach ($row in $rows) {
        [int]$sequence = 0
        $sequenceIsNumeric = [int]::TryParse([string]$row.sequence, [ref]$sequence)
        if (!$sequenceIsNumeric -or [string]$row.sequence -ne $sequence.ToString(
                [System.Globalization.CultureInfo]::InvariantCulture)) {
            throw "Capture sequence is not a canonical integer: '$($row.sequence)'"
        }
        $parsedSequences += $sequence
    }
    $expectedSequences = @(1..267)
    $sequenceDifference = @(
        Compare-Object -ReferenceObject $expectedSequences -DifferenceObject (
            $parsedSequences | Sort-Object)
    )
    if ($sequenceDifference.Count -ne 0) {
        throw 'Complete Carol review requires the exact sequence set 1..267'
    }
    foreach ($coverage in $expectedCoverage.Keys) {
        $actual = @($rows | Where-Object coverage -eq $coverage).Count
        if ($actual -ne $expectedCoverage[$coverage]) {
            throw "Coverage '$coverage' requires $($expectedCoverage[$coverage]) captures; found $actual"
        }
    }

    $masterRowsForContract = @($rows | Where-Object coverage -eq 'master')
    $nonPlainsMasterRows = @(
        $masterRowsForContract | Where-Object dialect -ne 'plains'
    )
    if ($nonPlainsMasterRows.Count -gt 0) {
        $offendingSequences = @($nonPlainsMasterRows.sequence) -join ', '
        throw "All 181 geometry-master captures must use dialect 'plains'; offending sequences: $offendingSequences"
    }
    $masterGroups = @($masterRowsForContract | Group-Object subject)
    if ($masterGroups.Count -ne 52) {
        throw "Complete Carol review requires 52 geometry masters; found $($masterGroups.Count)"
    }
    $masterViewSet = @('front-day', 'rear-doodads-day', 'interior-primary-night')
    foreach ($group in $masterGroups) {
        foreach ($view in $masterViewSet) {
            if (@($group.Group | Where-Object view -eq $view).Count -ne 1) {
                throw "Master '$($group.Name)' does not have exactly one '$view' capture"
            }
        }
        if ($group.Count -lt 3 -or $group.Count -gt 4) {
            throw "Master '$($group.Name)' must have three or four review views; found $($group.Count)"
        }
    }
    $secondaryInteriorCount = @(
        $masterRowsForContract | Where-Object view -eq 'interior-secondary-night'
    ).Count
    if ($secondaryInteriorCount -ne 25) {
        throw "Large/Landmark second-interior coverage requires 25 captures; found $secondaryInteriorCount"
    }

    $bankRowsForContract = @($rows | Where-Object coverage -eq 'bank')
    $nonBankSubjectRows = @(
        $bankRowsForContract | Where-Object subject -ne 'bank'
    )
    if ($nonBankSubjectRows.Count -gt 0) {
        $offendingSequences = @($nonBankSubjectRows.sequence) -join ', '
        throw "All 20 Bank captures must use subject 'bank'; offending sequences: $offendingSequences"
    }
    $expectedBankDialects = @('plains', 'desert', 'savanna', 'taiga', 'snowy')
    $actualBankDialects = @($bankRowsForContract.dialect | Sort-Object -Unique)
    if (@(Compare-Object $expectedBankDialects $actualBankDialects).Count -ne 0) {
        throw "Complete Carol review requires the exact five Bank dialects; found $($actualBankDialects -join ', ')"
    }
    $requiredBankViews = @(
        'front-day',
        'rear-doodads-day',
        'interior-primary-night',
        'interior-secondary-night')
    foreach ($dialect in $expectedBankDialects) {
        $dialectRows = @($bankRowsForContract | Where-Object dialect -eq $dialect)
        foreach ($view in $requiredBankViews) {
            if (@($dialectRows | Where-Object view -eq $view).Count -ne 1) {
                throw "Bank '$dialect' does not have exactly one '$view' capture"
            }
        }
    }

    $doodadRowsForContract = @($rows | Where-Object coverage -eq 'doodad')
    $expectedDoodads = @(1..16 | ForEach-Object { "D$_" })
    $actualDoodads = @($doodadRowsForContract.doodad_focus | Sort-Object -Unique)
    $doodadDifference = @(
        Compare-Object -ReferenceObject $expectedDoodads -DifferenceObject $actualDoodads
    )
    if ($doodadDifference.Count -ne 0) {
        throw "Doodad close-ups must contain exactly D1-D16; found $($actualDoodads -join ', ')"
    }
    $invalidDoodadRows = @($doodadRowsForContract | Where-Object {
        $_.view -ne 'doodad-detail-day' -or $_.capture_context -ne 'gallery'
    })
    if ($doodadRowsForContract.Count -ne 16 -or $invalidDoodadRows.Count -gt 0) {
        throw 'D1-D16 evidence must be sixteen gallery-context doodad-detail-day captures'
    }

    $cohesionRowsForContract = @($rows | Where-Object coverage -eq 'cohesion')
    $expectedCohesionDialects = @('plains', 'desert', 'savanna', 'taiga', 'snowy')
    $actualCohesionDialects = @(
        $cohesionRowsForContract.dialect | Sort-Object -Unique
    )
    $cohesionDialectDifference = @(
        Compare-Object `
            -ReferenceObject $expectedCohesionDialects `
            -DifferenceObject $actualCohesionDialects
    )
    if ($cohesionDialectDifference.Count -ne 0) {
        throw "Biome-cohesion evidence requires exact dialects plains/desert/savanna/taiga/snowy; found $($actualCohesionDialects -join ', ')"
    }
    $cohesionSubjectCount = @($cohesionRowsForContract | Group-Object subject).Count
    if ($cohesionSubjectCount -ne 10) {
        throw "Biome-cohesion coverage requires exactly ten role subjects; found $cohesionSubjectCount"
    }
    foreach ($dialect in $expectedCohesionDialects) {
        $dialectRowCount = @(
            $cohesionRowsForContract | Where-Object dialect -eq $dialect
        ).Count
        if ($dialectRowCount -ne 10) {
            throw "Biome-cohesion dialect '$dialect' requires exactly ten role captures; found $dialectRowCount"
        }
    }
    foreach ($subjectGroup in @($cohesionRowsForContract | Group-Object subject)) {
        if ($subjectGroup.Count -ne 5) {
            throw "Biome-cohesion subject '$($subjectGroup.Name)' requires exactly five dialect captures; found $($subjectGroup.Count)"
        }
    }
    if (@($cohesionRowsForContract | Where-Object {
                $_.view -ne 'front-day' -or $_.capture_context -ne 'gallery'
            }).Count -gt 0) {
        throw 'Biome-cohesion evidence must use gallery-context front-day captures'
    }
    $cohesionPairs = @(
        $cohesionRowsForContract | ForEach-Object { "$($_.subject)|$($_.dialect)" }
    )
    if (@($cohesionPairs | Sort-Object -Unique).Count -ne 50) {
        throw 'Biome-cohesion evidence must contain one unique subject-by-dialect pair'
    }
}

if (Test-Path -LiteralPath $outputPath) {
    $existing = @(Get-ChildItem -LiteralPath $outputPath -Force)
    if ($existing.Count -gt 0) {
        throw "Contact-sheet output is not empty; choose a fresh directory: $outputPath"
    }
} else {
    New-Item -ItemType Directory -Path $outputPath | Out-Null
}

$tileWidth = 512
$tileHeight = 288
$labelHeight = 34
$headerHeight = 52
$background = [System.Drawing.Color]::FromArgb(18, 20, 24)
$tileBackground = [System.Drawing.Color]::FromArgb(35, 38, 44)
$labelColor = [System.Drawing.Color]::FromArgb(238, 240, 244)
$mutedColor = [System.Drawing.Color]::FromArgb(165, 174, 184)
$font = [System.Drawing.Font]::new('Segoe UI', 13.0)
$headerFont = [System.Drawing.Font]::new('Segoe UI Semibold', 18.0)
$brush = [System.Drawing.SolidBrush]::new($labelColor)
$mutedBrush = [System.Drawing.SolidBrush]::new($mutedColor)
$backgroundBrush = [System.Drawing.SolidBrush]::new($background)
$tileBrush = [System.Drawing.SolidBrush]::new($tileBackground)

function Draw-CaptureTile {
    param(
        [System.Drawing.Graphics] $Graphics,
        [object] $Row,
        [int] $Column,
        [int] $GridRow
    )

    $x = $Column * $script:tileWidth
    $y = $script:headerHeight + $GridRow * ($script:labelHeight + $script:tileHeight)
    $Graphics.FillRectangle(
        $script:tileBrush,
        $x,
        $y,
        $script:tileWidth,
        $script:labelHeight + $script:tileHeight)

    if ($null -eq $Row) {
        $Graphics.DrawString('not required', $script:font, $script:mutedBrush, $x + 10, $y + 7)
        return
    }

    $focusValue = if ($null -eq $Row.PSObject.Properties['doodad_focus']) {
        ''
    } else {
        [string]$Row.doodad_focus
    }
    $focus = if ([string]::IsNullOrWhiteSpace($focusValue)) {
        ''
    } else {
        " | focus $focusValue"
    }
    $label = "#$($Row.sequence) $($Row.subject) | $($Row.dialect) | $($Row.view)$focus"
    $Graphics.DrawString($label, $script:font, $script:brush, $x + 8, $y + 6)

    $image = [System.Drawing.Image]::FromFile($Row.ImagePath)
    try {
        $scale = [Math]::Min(
            $script:tileWidth / [double]$image.Width,
            $script:tileHeight / [double]$image.Height)
        $drawWidth = [int][Math]::Round($image.Width * $scale)
        $drawHeight = [int][Math]::Round($image.Height * $scale)
        $drawX = $x + [int](($script:tileWidth - $drawWidth) / 2)
        $drawY = $y + $script:labelHeight + [int](($script:tileHeight - $drawHeight) / 2)
        $Graphics.DrawImage($image, $drawX, $drawY, $drawWidth, $drawHeight)
    } finally {
        $image.Dispose()
    }
}

function Save-GroupedPages {
    param(
        [object[]] $Rows,
        [string] $Prefix,
        [string] $Title,
        [string[]] $Columns,
        [scriptblock] $ColumnSelector,
        [int] $SubjectsPerPage,
        [string] $GroupProperty = 'subject'
    )

    if ($Rows.Count -eq 0) {
        return
    }
    $groups = @($Rows | Group-Object -Property $GroupProperty | Sort-Object {
        [int](($_.Group | Measure-Object -Property sequence -Minimum).Minimum)
    })
    for ($offset = 0; $offset -lt $groups.Count; $offset += $SubjectsPerPage) {
        $last = [Math]::Min($groups.Count - 1, $offset + $SubjectsPerPage - 1)
        $pageGroups = @($groups[$offset..$last])
        $width = $Columns.Count * $script:tileWidth
        $height = $script:headerHeight + $pageGroups.Count * (
            $script:labelHeight + $script:tileHeight)
        $bitmap = [System.Drawing.Bitmap]::new(
            $width,
            $height,
            [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.InterpolationMode =
                [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode =
                [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.SmoothingMode =
                [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $graphics.FillRectangle($script:backgroundBrush, 0, 0, $width, $height)
            $page = [int]($offset / $SubjectsPerPage) + 1
            $graphics.DrawString(
                "$Title | page $page | source $(Split-Path -Leaf $script:manifestFile)",
                $script:headerFont,
                $script:brush,
                12,
                10)

            for ($rowIndex = 0; $rowIndex -lt $pageGroups.Count; $rowIndex++) {
                $group = $pageGroups[$rowIndex]
                for ($column = 0; $column -lt $Columns.Count; $column++) {
                    $wanted = $Columns[$column]
                    $capture = @($group.Group | Where-Object {
                        (& $ColumnSelector $_) -eq $wanted
                    } | Select-Object -First 1)
                    $tileArguments = @{
                        Graphics = $graphics
                        Row = $(if ($capture.Count -eq 0) { $null } else { $capture[0] })
                        Column = $column
                        GridRow = $rowIndex
                    }
                    Draw-CaptureTile @tileArguments
                }
            }

            $filename = Join-Path $script:outputPath ('{0}-{1:d2}.png' -f $Prefix, $page)
            $bitmap.Save($filename, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }
}

function Save-DoodadPages {
    param([object[]] $Rows)

    if ($Rows.Count -eq 0) {
        return
    }
    $ordered = @($Rows | Sort-Object { [int]$_.sequence })
    $columns = 4
    $rowsPerPage = 4
    $perPage = $columns * $rowsPerPage
    for ($offset = 0; $offset -lt $ordered.Count; $offset += $perPage) {
        $last = [Math]::Min($ordered.Count - 1, $offset + $perPage - 1)
        $pageRows = @($ordered[$offset..$last])
        $width = $columns * $script:tileWidth
        $height = $script:headerHeight + $rowsPerPage * (
            $script:labelHeight + $script:tileHeight)
        $bitmap = [System.Drawing.Bitmap]::new(
            $width,
            $height,
            [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.InterpolationMode =
                [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.FillRectangle($script:backgroundBrush, 0, 0, $width, $height)
            $page = [int]($offset / $perPage) + 1
            $graphics.DrawString(
                "Carol doodad close-ups | page $page | D1-D16",
                $script:headerFont,
                $script:brush,
                12,
                10)
            for ($index = 0; $index -lt $pageRows.Count; $index++) {
                $tileArguments = @{
                    Graphics = $graphics
                    Row = $pageRows[$index]
                    Column = $index % $columns
                    # PowerShell's direct [int] cast rounds fractional values; contact-sheet rows
                    # need integer division so later tiles never overwrite earlier evidence.
                    GridRow = [int][Math]::Floor($index / [double]$columns)
                }
                Draw-CaptureTile @tileArguments
            }
            $filename = Join-Path $script:outputPath ('doodads-{0:d2}.png' -f $page)
            $bitmap.Save($filename, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }
}

try {
    $masterRows = @($rows | Where-Object coverage -eq 'master')
    $masterArguments = @{
        Rows = $masterRows
        Prefix = 'masters'
        Title = 'Carol master review: front / rear / primary interior / secondary interior'
        Columns = @(
            'front-day',
            'rear-doodads-day',
            'interior-primary-night',
            'interior-secondary-night')
        ColumnSelector = { param($row) $row.view }
        SubjectsPerPage = 4
    }
    Save-GroupedPages @masterArguments

    $bankRows = @($rows | Where-Object coverage -eq 'bank')
    $bankArguments = @{
        Rows = $bankRows
        Prefix = 'banks'
        Title = 'Carol standalone Bank review: front / rear / public zone / secure zone'
        Columns = @(
            'front-day',
            'rear-doodads-day',
            'interior-primary-night',
            'interior-secondary-night')
        ColumnSelector = { param($row) $row.view }
        SubjectsPerPage = 5
        GroupProperty = 'dialect'
    }
    Save-GroupedPages @bankArguments

    Save-DoodadPages -Rows @($rows | Where-Object coverage -eq 'doodad')

    $cohesionRows = @($rows | Where-Object coverage -eq 'cohesion')
    $biomeArguments = @{
        Rows = $cohesionRows
        Prefix = 'biomes'
        Title = 'Carol biome cohesion: plains / desert / savanna / taiga / snowy'
        Columns = @('plains', 'desert', 'savanna', 'taiga', 'snowy')
        ColumnSelector = { param($row) $row.dialect }
        SubjectsPerPage = 5
    }
    Save-GroupedPages @biomeArguments
} finally {
    $font.Dispose()
    $headerFont.Dispose()
    $brush.Dispose()
    $mutedBrush.Dispose()
    $backgroundBrush.Dispose()
    $tileBrush.Dispose()
}

$sheets = @(Get-ChildItem -LiteralPath $outputPath -Filter '*.png')
Write-Host "Carol contact sheets: $($sheets.Count)"
Write-Host "Capture rows: $($rows.Count)"
Write-Host "Output: $outputPath"
