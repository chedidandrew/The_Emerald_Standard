[CmdletBinding()]
param(
    [ValidateSet('fabric', 'neoforge')]
    [string]$Loader = 'fabric',
    [string]$GameDirectory,
    [switch]$ArchitecturePreview,
    [switch]$FullCatalog,
    [switch]$Capture,
    [switch]$StopAfterCapture,
    [string]$JavaDirectory = 'C:\Program Files\Java\jdk-25.0.3+9'
)

$ErrorActionPreference = 'Stop'
if ($FullCatalog -and -not $ArchitecturePreview) { throw '-FullCatalog requires -ArchitecturePreview.' }
$comparisonRepository = Split-Path -Parent $PSScriptRoot
$comparisonProject = Join-Path $comparisonRepository $Loader
if ([string]::IsNullOrWhiteSpace($GameDirectory)) {
    $GameDirectory = Join-Path $comparisonProject $(if ($FullCatalog) { 'run\biome-catalog-26.2' } elseif ($ArchitecturePreview) { 'run\biome-preview-26.2' } else { 'run\comparison-26.2' })
}
$comparisonProfile = [IO.Path]::GetFullPath($GameDirectory)
$comparisonWorld = if ($ArchitecturePreview) { 'TES_Biome_Architecture_Preview' } else { 'TES_Village_Comparison' }
$comparisonSave = Join-Path $comparisonProfile "saves\$comparisonWorld"
$comparisonInit = Join-Path $PSScriptRoot 'village-comparison-client.init.gradle'
$comparisonJava = Join-Path $JavaDirectory 'bin\java.exe'
if (-not (Test-Path -LiteralPath $comparisonJava -PathType Leaf)) {
    throw "Java 25 was not found at $comparisonJava. Pass -JavaDirectory with your JDK 25 folder."
}
if (-not (Test-Path -LiteralPath (Join-Path $comparisonSave 'level.dat') -PathType Leaf)) {
    throw "No comparison save at $comparisonSave. Create a NEW Superflat/Creative world named $comparisonWorld in this dedicated profile first. See docs/BIOME_ARCHITECTURE_PREVIEW.md or docs/VILLAGE_COMPARISON_GALLERY.md. No existing world was copied, replaced, or removed."
}
$comparisonOldJava = $env:JAVA_HOME
$comparisonOldPath = $env:Path
try {
    $env:JAVA_HOME = $JavaDirectory
    $env:Path = (Join-Path $JavaDirectory 'bin') + [IO.Path]::PathSeparator + $comparisonOldPath
    Push-Location -LiteralPath $comparisonProject
    try {
        & (Join-Path $comparisonProject 'gradlew.bat') --no-daemon '-I' $comparisonInit "-PtesComparisonGameDir=$comparisonProfile" "-PtesArchitecturePreview=$($ArchitecturePreview.IsPresent.ToString().ToLowerInvariant())" "-PtesArchitectureFullCatalog=$($FullCatalog.IsPresent.ToString().ToLowerInvariant())" "-PtesComparisonCapture=$($Capture.IsPresent.ToString().ToLowerInvariant())" "-PtesComparisonStopWhenComplete=$($StopAfterCapture.IsPresent.ToString().ToLowerInvariant())" runClient
        if ($LASTEXITCODE -ne 0) {
            throw "Comparison client exited with code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $comparisonOldJava
    $env:Path = $comparisonOldPath
}
