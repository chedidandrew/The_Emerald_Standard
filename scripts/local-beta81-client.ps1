param([ValidateSet('fabric','neoforge')][string]$Loader='fabric')
$ErrorActionPreference='Stop'
$tesRoot=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME='C:/Program Files/Java/jdk-25.0.3+9'
$tesRun=Join-Path $tesRoot ('build/beta81-client-'+[Guid]::NewGuid().ToString('N'))
$tesSaves=Join-Path $tesRun 'saves'
[void](New-Item -ItemType Directory -Path $tesSaves -Force)
# Test-owned stopped server world, never a live profile or a player's save.
$tesSource=Join-Path $tesRoot 'build/server-smoke/fabric-run.1Iw8tm/world'
if(-not(Test-Path -LiteralPath (Join-Path $tesSource 'level.dat'))){throw 'Missing disposable native world'}
Copy-Item -LiteralPath $tesSource -Destination (Join-Path $tesSaves 'TES-chat-shutdown-fixture') -Recurse
foreach($tesMode in @('shutdown','handbook','handbook-restart')) {
    $tesFlag=if($tesMode -eq 'shutdown'){'clientShutdownOnly'}else{'clientConstructionOnly'}
    $tesRestart=($tesMode -eq 'handbook-restart').ToString().ToLowerInvariant()
    $env:JAVA_TOOL_OPTIONS="-Dthe_emerald_standard.clientSmoke=true -Dthe_emerald_standard.$tesFlag=true -Dthe_emerald_standard.clientSmokeRestart=$tesRestart"
    $tesPrefix=if($Loader -eq 'fabric'){'beta81-client'}else{'beta81-neoforge-client'}
    $tesLog=Join-Path $tesRoot "build/$tesPrefix-$tesMode.log"
    & "$tesRoot/$Loader/gradlew.bat" --no-daemon -p "$tesRoot/$Loader" -I "$tesRoot/scripts/smoke-client.init.gradle" "-PtesSmokeGameDir=$tesRun" runClient *> $tesLog
    if($LASTEXITCODE){Get-Content $tesLog -Tail 40;throw 'Native client failed'}
    $tesMarker=if($tesMode -eq 'shutdown'){'The Emerald Standard integrated chat shutdown checks passed'}else{'The Emerald Standard updated handbook native compact and long reader checks passed'}
    if(-not(Select-String -LiteralPath $tesLog -SimpleMatch $tesMarker -Quiet)){Get-Content $tesLog -Tail 55;throw "Native $tesMode assertions failed"}
    Write-Output "PASS native client $tesMode"
}
Remove-Item Env:JAVA_TOOL_OPTIONS
