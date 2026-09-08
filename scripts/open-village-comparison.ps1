[CmdletBinding()]
param(
    [ValidateSet('fabric', 'neoforge')]
    [string]$Loader = 'fabric',
    [string]$GameDirectory,
    [string]$JavaDirectory = 'C:\Program Files\Java\jdk-25.0.3+9'
)

$ErrorActionPreference = 'Stop'
$comparisonRepository = Split-Path -Parent $PSScriptRoot
$comparisonProject = Join-Path $comparisonRepository $Loader
if ([string]::IsNullOrWhiteSpace($GameDirectory)) {
    $GameDirectory = Join-Path $comparisonProject 'run\comparison-26.2'
}
$comparisonProfile = [IO.Path]::GetFullPath($GameDirectory)
$comparisonSave = Join-Path $comparisonProfile 'saves\TES_Village_Comparison'
$comparisonInit = Join-Path $PSScriptRoot 'village-comparison-client.init.gradle'
$comparisonJava = Join-Path $JavaDirectory 'bin\java.exe'
if (-not (Test-Path -LiteralPath $comparisonJava -PathType Leaf)) {
    throw "Java 25 was not found at $comparisonJava. Pass -JavaDirectory with your JDK 25 folder."
}
if (-not (Test-Path -LiteralPath (Join-Path $comparisonSave 'level.dat') -PathType Leaf)) {
    throw "No comparison save at $comparisonSave. Create a NEW Superflat/Creative world named TES_Village_Comparison in this dedicated profile first. See docs/VILLAGE_COMPARISON_GALLERY.md. No existing world was copied, replaced, or removed."
}
$comparisonOldJava = $env:JAVA_HOME
$comparisonOldPath = $env:Path
try {
    $env:JAVA_HOME = $JavaDirectory
    $env:Path = (Join-Path $JavaDirectory 'bin') + [IO.Path]::PathSeparator + $comparisonOldPath
    Push-Location -LiteralPath $comparisonProject
    try {
        & (Join-Path $comparisonProject 'gradlew.bat') --no-daemon '-I' $comparisonInit "-PtesComparisonGameDir=$comparisonProfile" runClient
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
