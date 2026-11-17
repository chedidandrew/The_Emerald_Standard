$ErrorActionPreference='Stop'
$tesRoot=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME='C:/Program Files/Java/jdk-25.0.3+9'
$tesLogs=Join-Path $tesRoot 'build'
$tesRun=Join-Path $tesLogs ('beta80-client-'+[Guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $tesRun)
foreach($tesRestart in @('false','true')) {
    $env:JAVA_TOOL_OPTIONS="-Dthe_emerald_standard.clientSmoke=true -Dthe_emerald_standard.clientTerrainOnly=true -Dthe_emerald_standard.clientSmokeRestart=$tesRestart"
    $tesLog=Join-Path $tesLogs "beta80-client-$tesRestart.log"
    & "$tesRoot/fabric/gradlew.bat" --no-daemon -p "$tesRoot/fabric" -I "$tesRoot/scripts/smoke-client.init.gradle" "-PtesSmokeGameDir=$tesRun" runClient *> $tesLog
    if($LASTEXITCODE){Get-Content $tesLog -Tail 30;throw 'Native client failed'}
    if(-not (Select-String -LiteralPath $tesLog -SimpleMatch 'The Emerald Standard updated handbook native compact and long reader checks passed' -Quiet)){Get-Content $tesLog -Tail 40;throw 'Native handbook assertions failed'}
    Write-Output "PASS native entrance/terrain handbook and preferences: restart=$tesRestart"
}
Remove-Item Env:JAVA_TOOL_OPTIONS
