param([string]$Repository = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$tesRoot = (Resolve-Path -LiteralPath $Repository).Path
. (Join-Path $PSScriptRoot 'source-fingerprint.ps1')
$tesExpected = Get-TesSourceFingerprint -Repository $tesRoot
$tesFingerprints = @()
$tesVersions = @()
$tesResults = @()
foreach ($tesLoader in @('fabric','neoforge')) {
    $tesProperties = Get-Content -LiteralPath (Join-Path $tesRoot "$tesLoader/gradle.properties")
    $tesVersion = ($tesProperties | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=',''
    if (-not $tesVersion -or @($tesVersion).Count -ne 1) { throw "Missing or ambiguous version for $tesLoader" }
    $tesVersions += $tesVersion
    $tesJar = Join-Path $tesRoot "$tesLoader/build/libs/the-emerald-standard-$tesLoader-$tesVersion.jar"
    $tesZip = [IO.Compression.ZipFile]::OpenRead($tesJar)
    try {
        $tesEntry = $tesZip.GetEntry('tes-build.properties')
        if ($null -eq $tesEntry) { throw "Missing build identity: $tesJar" }
        $tesReader = [IO.StreamReader]::new($tesEntry.Open())
        try { $tesIdentity = ConvertFrom-StringData $tesReader.ReadToEnd() } finally { $tesReader.Dispose() }
        if ($tesIdentity.version -ne $tesVersion -or $tesIdentity.sourceSha256 -notmatch '^[0-9a-f]{64}$') {
            throw "Invalid or stale packaged identity for $tesLoader"
        }
        if ($null -eq $tesZip.GetEntry('com/chedidandrew/emeraldstandard/minecraft/BankerBriefings.class')) {
            throw "Candidate is missing priority reports"
        }
        if ($tesIdentity.sourceSha256 -ne $tesExpected) {
            throw "Stale $tesLoader candidate: packaged source fingerprint does not match current source. Rebuild both loaders."
        }
        $tesFingerprints += $tesIdentity.sourceSha256
        $tesResults += [PSCustomObject]@{ Loader=$tesLoader; Version=$tesVersion; SourceSha256=$tesIdentity.sourceSha256;
            JarSha256=(Get-FileHash -LiteralPath $tesJar -Algorithm SHA256).Hash; Jar=$tesJar }
    } finally { $tesZip.Dispose() }
}
if ($tesFingerprints[0] -ne $tesFingerprints[1]) { throw 'Loader source fingerprints do not match; rebuild both from the same inputs.' }
if (@($tesVersions | Select-Object -Unique).Count -ne 1) { throw 'Loader versions differ.' }
if ((Get-TesSourceFingerprint -Repository $tesRoot) -ne $tesExpected) { throw 'Source changed during verification; retry after the edits finish.' }
$tesResults
Write-Output 'PASS current-source fingerprint, packaged versions, required report class and cross-loader parity'
