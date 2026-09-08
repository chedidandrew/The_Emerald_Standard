[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$verifier = Join-Path $repoRoot 'scripts\carol-structure-contact-sheets.ps1'
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'build'))
$separator = [System.IO.Path]::DirectorySeparatorChar
$buildPrefix = if ($buildRoot.EndsWith([string]$separator)) {
    $buildRoot
} else {
    $buildRoot + $separator
}
$temporaryRoot = [System.IO.Path]::GetFullPath((Join-Path `
    $buildRoot `
    ('carol-verifier-regression-' + [guid]::NewGuid().ToString('N'))))
if (!$temporaryRoot.StartsWith(
        $buildPrefix,
        [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Synthetic verifier directory escaped the repository build directory: $temporaryRoot"
}

function New-Row {
    param(
        [int] $Sequence,
        [string] $Coverage,
        [int] $GalleryIndex,
        [string] $View,
        [string] $Subject,
        [string] $Dialect,
        [string] $Doodads = '',
        [string] $DoodadFocus = '',
        [string] $DoodadSourceView = '')

    [pscustomobject][ordered]@{
        sequence = $Sequence
        coverage = $Coverage
        gallery_index = $GalleryIndex
        view = $View
        vertical_fov = '70.0'
        capture_context = 'gallery'
        pixel_width = '1920'
        pixel_height = '1080'
        subject = $Subject
        dialect = $Dialect
        doodads = $Doodads
        doodad_focus = $DoodadFocus
        doodad_source_view = $DoodadSourceView
        filename = ('{0:d3}_synthetic.png' -f $Sequence)
        status = 'captured'
    }
}

function Assert-ManifestRejected {
    param(
        [string] $Name,
        [scriptblock] $Mutator,
        [string] $ExpectedMessage,
        [string] $Manifest,
        [string] $OriginalManifest,
        [string] $CaptureDirectory)

    $testRows = @(Import-Csv -LiteralPath $Manifest)
    & $Mutator $testRows
    $testRows | Export-Csv -LiteralPath $Manifest -NoTypeInformation
    $negativeOutput = Join-Path $CaptureDirectory "negative-$Name"
    try {
        & $verifier `
            -ManifestPath $Manifest `
            -OutputDirectory $negativeOutput `
            -RequireCompleteReviewSet | Out-Null
        throw "Negative case '$Name' unexpectedly passed"
    } catch {
        if ($_.Exception.Message -notlike "*$ExpectedMessage*") {
            throw "Negative case '$Name' failed for the wrong reason: $($_.Exception.Message)"
        }
    } finally {
        [System.IO.File]::WriteAllText(
            $Manifest,
            $OriginalManifest,
            [System.Text.UTF8Encoding]::new($false))
    }
}

try {
    [System.IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    $captureRoot = Join-Path $temporaryRoot 'tes-structure-review-deadbeef-capture-v3'
    $captureDirectory = Join-Path $captureRoot 'complete-pass-99'
    [System.IO.Directory]::CreateDirectory($captureDirectory) | Out-Null

    $templatePng = Join-Path $temporaryRoot 'template.png'
    $bitmap = [System.Drawing.Bitmap]::new(1920, 1080)
    try {
        $bitmap.Save($templatePng, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }

    $rows = [System.Collections.Generic.List[object]]::new()
    $sequence = 1
    $standardViews = @('front-day', 'rear-doodads-day', 'interior-primary-night')
    for ($master = 1; $master -le 52; $master++) {
        $views = @($standardViews)
        if ($master -le 25) {
            $views += 'interior-secondary-night'
        }
        foreach ($view in $views) {
            $rows.Add((New-Row `
                -Sequence $sequence `
                -Coverage 'master' `
                -GalleryIndex $master `
                -View $view `
                -Subject ('master_{0:d2}' -f $master) `
                -Dialect 'plains'))
            $sequence++
        }
    }

    $dialects = @('plains', 'desert', 'savanna', 'taiga', 'snowy')
    $bankViews = @(
        'front-day',
        'rear-doodads-day',
        'interior-primary-night',
        'interior-secondary-night')
    for ($dialectIndex = 0; $dialectIndex -lt $dialects.Count; $dialectIndex++) {
        foreach ($view in $bankViews) {
            $rows.Add((New-Row `
                -Sequence $sequence `
                -Coverage 'bank' `
                -GalleryIndex (272 + $dialectIndex) `
                -View $view `
                -Subject 'bank' `
                -Dialect $dialects[$dialectIndex]))
            $sequence++
        }
    }

    for ($doodad = 1; $doodad -le 16; $doodad++) {
        $id = "D$doodad"
        $rows.Add((New-Row `
            -Sequence $sequence `
            -Coverage 'doodad' `
            -GalleryIndex $doodad `
            -View 'doodad-detail-day' `
            -Subject 'master_01' `
            -Dialect 'plains' `
            -Doodads $id `
            -DoodadFocus $id `
            -DoodadSourceView 'front-day'))
        $sequence++
    }

    for ($role = 1; $role -le 10; $role++) {
        foreach ($dialect in $dialects) {
            $rows.Add((New-Row `
                -Sequence $sequence `
                -Coverage 'cohesion' `
                -GalleryIndex $role `
                -View 'front-day' `
                -Subject ('role_{0:d2}' -f $role) `
                -Dialect $dialect))
            $sequence++
        }
    }

    if ($rows.Count -ne 267 -or $sequence -ne 268) {
        throw "Synthetic fixture generated $($rows.Count) rows instead of 267"
    }
    foreach ($row in $rows) {
        Copy-Item `
            -LiteralPath $templatePng `
            -Destination (Join-Path $captureDirectory $row.filename)
    }

    $manifest = Join-Path $captureDirectory 'manifest.csv'
    $rows | Export-Csv -LiteralPath $manifest -NoTypeInformation
    $completedAt = [datetimeoffset]::UtcNow.ToString('o')
    $marker = @(
        'layout_signature=deadbeef',
        'gallery_content_revision=9',
        'capture_schema_revision=3',
        'shot_count=267',
        'pixel_width=1920',
        'pixel_height=1080',
        "completed_at=$completedAt")
    [System.IO.File]::WriteAllLines(
        (Join-Path $captureDirectory 'capture-complete.txt'),
        $marker,
        [System.Text.UTF8Encoding]::new($false))

    $passOutput = Join-Path $captureDirectory 'pass-contact-sheets'
    & $verifier `
        -ManifestPath $manifest `
        -OutputDirectory $passOutput `
        -RequireCompleteReviewSet | Out-Null
    if (@(Get-ChildItem -LiteralPath $passOutput -Filter '*.png').Count -eq 0) {
        throw 'Canonical synthetic manifest produced no contact sheets'
    }

    $originalManifest = [System.IO.File]::ReadAllText($manifest)
    Assert-ManifestRejected `
        -Name 'master-dialect' `
        -Manifest $manifest `
        -OriginalManifest $originalManifest `
        -CaptureDirectory $captureDirectory `
        -ExpectedMessage "must use dialect 'plains'" `
        -Mutator { param($testRows) $testRows[0].dialect = 'desert' }
    Assert-ManifestRejected `
        -Name 'bank-subject' `
        -Manifest $manifest `
        -OriginalManifest $originalManifest `
        -CaptureDirectory $captureDirectory `
        -ExpectedMessage "must use subject 'bank'" `
        -Mutator { param($testRows) $testRows[181].subject = 'not_bank' }
    Assert-ManifestRejected `
        -Name 'cohesion-dialect' `
        -Manifest $manifest `
        -OriginalManifest $originalManifest `
        -CaptureDirectory $captureDirectory `
        -ExpectedMessage 'requires exact dialects' `
        -Mutator { param($testRows) $testRows[217].dialect = 'jungle' }

    Write-Host 'Carol evidence verifier regression: PASS'
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
        if (!$resolvedTemporaryRoot.StartsWith(
                $buildPrefix,
                [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unexpected regression path: $resolvedTemporaryRoot"
        }
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}
