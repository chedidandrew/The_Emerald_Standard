param([ValidateSet('fabric','neoforge')][string]$Loader='fabric')
$ErrorActionPreference='Stop'
$tesRoot=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME='C:/Program Files/Java/jdk-25.0.3+9'
$tesRun=Join-Path $tesRoot ('build/beta82-client-'+[Guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $tesRun)
foreach($tesMode in @('handbook','handbook-restart')) {
    $tesRestart=($tesMode -eq 'handbook-restart').ToString().ToLowerInvariant()
    $env:JAVA_TOOL_OPTIONS="-Dthe_emerald_standard.clientSmoke=true -Dthe_emerald_standard.clientConstructionOnly=true -Dthe_emerald_standard.clientSmokeRestart=$tesRestart"
    $tesLog=Join-Path $tesRoot "build/beta82-$Loader-client-$tesMode.log"
    & "$tesRoot/$Loader/gradlew.bat" --no-daemon -p "$tesRoot/$Loader" -I "$tesRoot/scripts/smoke-client.init.gradle" "-PtesSmokeGameDir=$tesRun" runClient *> $tesLog
    if($LASTEXITCODE){Get-Content $tesLog -Tail 40;throw 'Native client failed'}
    if(-not(Select-String -LiteralPath $tesLog -SimpleMatch 'The Emerald Standard updated handbook native compact and long reader checks passed' -Quiet)) {Get-Content $tesLog -Tail 55;throw "Native $tesMode assertions failed"}
    Write-Output "PASS native $Loader client $tesMode"
}
Remove-Item Env:JAVA_TOOL_OPTIONS
