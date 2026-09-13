$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'source-fingerprint.ps1')
$tesRepo=Split-Path -Parent $PSScriptRoot
$tesFixture=Join-Path $tesRepo ('build/fingerprint-test-'+[Guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $tesFixture)
function Write-TesFixtureFile([string]$Relative,[string]$Text) {
    $tesTarget=Join-Path $tesFixture $Relative
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($tesTarget))
    [IO.File]::WriteAllText($tesTarget,$Text,[Text.UTF8Encoding]::new($false))
}
Write-TesFixtureFile 'scripts/build-inputs.json' '{"directories":["common/src/main"],"files":["fabric/gradle.properties","neoforge/gradle.properties","scripts/build-inputs.json"]}'
Write-TesFixtureFile 'fabric/gradle.properties' "mod_version=fixture"
Write-TesFixtureFile 'neoforge/gradle.properties' "mod_version=fixture"
Write-TesFixtureFile 'common/src/main/Z.txt' 'one'
Write-TesFixtureFile 'common/src/main/nested/a.txt' 'two'
$tesHash=Get-TesSourceFingerprint $tesFixture
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($tesLoader in @('fabric','neoforge')){
    $tesJar=Join-Path $tesFixture "$tesLoader/build/libs/the-emerald-standard-$tesLoader-fixture.jar"
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($tesJar))
    $tesZip=[IO.Compression.ZipFile]::Open($tesJar,[IO.Compression.ZipArchiveMode]::Create)
    try {
        $tesEntry=$tesZip.CreateEntry('tes-build.properties')
        $tesWriter=[IO.StreamWriter]::new($tesEntry.Open())
        try{$tesWriter.Write("version=fixture`nsourceSha256=$tesHash`n")}finally{$tesWriter.Dispose()}
        [void]$tesZip.CreateEntry('com/chedidandrew/emeraldstandard/minecraft/BankerBriefings.class')
    }finally{$tesZip.Dispose()}
}
& (Join-Path $PSScriptRoot 'verify-candidate.ps1') -Repository $tesFixture | Out-Null
Write-TesFixtureFile 'common/src/main/Z.txt' 'changed after both jars were built'
$tesRejected=$false
try {& (Join-Path $PSScriptRoot 'verify-candidate.ps1') -Repository $tesFixture | Out-Null}
catch {if($_.Exception.Message -like '*Stale*candidate*'){$tesRejected=$true}else{throw}}
if(-not $tesRejected){throw 'Two equally stale artifacts were accepted'}
Write-Output "PASS fresh fixture accepted; two equally stale artifacts rejected. Isolated fixture: $tesFixture"
