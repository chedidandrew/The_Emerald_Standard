param([ValidateSet('fabric','neoforge')][string]$Loader='fabric')
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
$version=((Get-Content -LiteralPath "$repoRoot/$Loader/gradle.properties" | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
$jarPath="$repoRoot/$Loader/build/libs/the-emerald-standard-$Loader-$version.jar"

# Match the ordinal, slash-normalized canonical build-input manifest used by Gradle.
$spec=Get-Content -Raw -LiteralPath "$PSScriptRoot/build-inputs.json" | ConvertFrom-Json
$paths=[System.Collections.Generic.List[string]]::new()
foreach($directory in $spec.directories) {
    if(-not (Test-Path -LiteralPath "$repoRoot/$directory")){continue} # Optional Gradle fileTree.
    Get-ChildItem -LiteralPath "$repoRoot/$directory" -File -Recurse -Force | ForEach-Object {
        $paths.Add([IO.Path]::GetRelativePath($repoRoot,$_.FullName).Replace('\','/'))
    }
}
foreach($file in $spec.files) {$paths.Add($file)}
$paths.Sort([StringComparer]::Ordinal)
$digest=[Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
foreach($relative in $paths) {
    $digest.AppendData([Text.Encoding]::UTF8.GetBytes($relative));$digest.AppendData([byte[]]@(0))
    $digest.AppendData([IO.File]::ReadAllBytes("$repoRoot/$relative"));$digest.AppendData([byte[]]@(0))
}
$sourceHash=[Convert]::ToHexString($digest.GetHashAndReset()).ToLowerInvariant();$digest.Dispose()
Add-Type -AssemblyName System.IO.Compression
$zip=[IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    function Read-JarText([string]$name) {
        $entry=$zip.GetEntry($name);if($null -eq $entry){throw "Missing packaged $name"}
        $reader=[IO.StreamReader]::new($entry.Open());try {$reader.ReadToEnd()} finally {$reader.Dispose()}
    }
    $identity=Read-JarText 'tes-build.properties'
    if(($identity -notmatch "(?m)^version=$([regex]::Escape($version))\r?$") -or
            ($identity -notmatch "(?m)^sourceSha256=$sourceHash\r?$")) {throw 'Packaged build does not match current canonical source inputs'}
    foreach($revision in @(12,13)) {
    $prefix="data/the_emerald_standard/architecture/v$revision/"
    $rows=(Read-JarText ($prefix+'catalog.tsv')) -split '\r?\n' | Where-Object {$_ -and -not $_.StartsWith('#')}
    if($rows.Count -ne 375){throw "Incomplete catalog: $($rows.Count)"}
    $packaged=@($zip.Entries | Where-Object {$_.FullName.StartsWith($prefix) -and $_.FullName.EndsWith('.bin.gz')})
    if($packaged.Count -ne 375){throw "Unexpected packaged asset count: $($packaged.Count)"}
    $ordinary=0;$banks=0
    foreach($row in $rows) {
        $fields=$row -split "`t";if($fields.Count -ne 9){throw 'Malformed manifest'}
        $entry=$zip.GetEntry($prefix+$fields[0]+'.bin.gz');if($null -eq $entry){throw "Missing $($fields[0])"}
        $gzip=[IO.Compression.GZipStream]::new($entry.Open(),[IO.Compression.CompressionMode]::Decompress)
        $memory=[IO.MemoryStream]::new()
        try {
            $gzip.CopyTo($memory)
            if($memory.Length -gt 4000000){throw 'Oversized native asset'}
            $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($memory.ToArray())).ToLowerInvariant()
            if($hash -ne $fields[8]){throw "Packaged native geometry differs: $($fields[0])"}
        } finally {$gzip.Dispose();$memory.Dispose()}
        if($fields[1] -eq 'BANK'){$banks++}else{$ordinary++}
    }
    if($ordinary -ne 360 -or $banks -ne 15){throw 'Ordinary/Bank coverage mismatch'}
    }
    foreach($class in @('core/ApprovedArchitectureCatalog','minecraft/ApprovedVillageStructures','minecraft/ApprovedBankStructures','minecraft/NativeStructureSupport','minecraft/NativeDoorwayClearance')) {
        if($null -eq $zip.GetEntry('com/chedidandrew/emeraldstandard/'+$class+'.class')){throw "Missing production adapter $class"}
    }
    "PASS $Loader ${version}: current source identity, 750 exact native SHA-256 assets (375 each for archived v12 and current v13), production adapters"
    "sourceSha256=$sourceHash"
    "jarSha256=$((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant())"
} finally {$zip.Dispose()}
