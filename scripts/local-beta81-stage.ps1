$ErrorActionPreference='Stop'
$tesRoot=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME='C:/Program Files/Java/jdk-25.0.3+9'
$env:PYTHON='C:/Users/Andrew/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$tesRelease=Join-Path $tesRoot 'build/release-beta81'
& "$tesRoot/scripts/verify-candidate.ps1" *> "$tesRoot/build/beta81-candidate.log"
foreach($tesLoader in @('fabric','neoforge')) {
    & "$tesRoot/scripts/verify-approved-architecture-jars.ps1" -Loader $tesLoader *> "$tesRoot/build/beta81-$tesLoader-assets.log"
    $tesStage=Join-Path $tesRelease $tesLoader
    if(Test-Path -LiteralPath $tesStage){throw "Release stage already exists: $tesStage"}
    [void](New-Item -ItemType Directory -Path $tesStage)
    foreach($tesSuffix in @('','-sources')) {
        $tesName="the-emerald-standard-$tesLoader-0.4.0-beta.81$tesSuffix.jar"
        Copy-Item -LiteralPath "$tesRoot/$tesLoader/build/libs/$tesName" -Destination $tesStage
        if((Get-FileHash "$tesRoot/$tesLoader/build/libs/$tesName").Hash -ne (Get-FileHash "$tesStage/$tesName").Hash){throw 'Staging hash mismatch'}
    }
    $env:TES_JAR_VERIFY_DIR=$tesStage.Replace('\','/')
    & 'C:/Program Files/Git/bin/bash.exe' "$tesRoot/scripts/verify-built-jar.sh" $tesLoader *> "$tesRoot/build/beta81-$tesLoader-jar.log"
    if($LASTEXITCODE){Get-Content "$tesRoot/build/beta81-$tesLoader-jar.log" -Tail 25;throw 'Staged JAR validation failed'}
    Write-Output "PASS staged $tesLoader beta81 binaries, sources, metadata and SHA256SUMS"
}
Copy-Item -LiteralPath "$tesRoot/docs/releases/beta81.md" -Destination "$tesRelease/README.md"
Copy-Item -LiteralPath "$tesRoot/docs/reviews/2026-10-07-beta81-flora-and-shutdown.md" -Destination "$tesRelease/validation.md"
Remove-Item Env:TES_JAR_VERIFY_DIR
Remove-Item Env:PYTHON
