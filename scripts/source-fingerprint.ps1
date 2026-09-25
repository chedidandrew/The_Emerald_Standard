function Get-TesSourceFingerprint {
    param([Parameter(Mandatory)][string]$Repository)
    $tesSourceRoot = (Resolve-Path -LiteralPath $Repository).Path
    $tesManifest = Get-Content -Raw -LiteralPath (Join-Path $tesSourceRoot 'scripts/build-inputs.json') | ConvertFrom-Json
    $tesPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($tesDirectory in $tesManifest.directories) {
        $tesDirectoryPath = Join-Path $tesSourceRoot $tesDirectory
        if (Test-Path -LiteralPath $tesDirectoryPath) {
            foreach ($tesFile in (Get-ChildItem -LiteralPath $tesDirectoryPath -Recurse -File -Force)) {
                [void]$tesPaths.Add([IO.Path]::GetRelativePath($tesSourceRoot,$tesFile.FullName).Replace('\','/'))
            }
        }
    }
    foreach ($tesFile in $tesManifest.files) {
        if (-not (Test-Path -LiteralPath (Join-Path $tesSourceRoot $tesFile) -PathType Leaf)) {
            throw "Missing required build input: $tesFile"
        }
        [void]$tesPaths.Add($tesFile.Replace('\','/'))
    }
    $tesOrdered = [Collections.Generic.List[string]]::new($tesPaths)
    $tesOrdered.Sort([StringComparer]::Ordinal)
    $tesDigest = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    $tesBuffer = [byte[]]::new(65536)
    try {
        foreach ($tesRelative in $tesOrdered) {
            $tesDigest.AppendData([Text.Encoding]::UTF8.GetBytes($tesRelative))
            $tesDigest.AppendData([byte[]]@(0))
            $tesStream = [IO.File]::OpenRead((Join-Path $tesSourceRoot $tesRelative))
            try { while (($tesRead=$tesStream.Read($tesBuffer,0,$tesBuffer.Length)) -gt 0) {
                $tesDigest.AppendData($tesBuffer,0,$tesRead)
            }} finally { $tesStream.Dispose() }
            $tesDigest.AppendData([byte[]]@(0))
        }
        return [Convert]::ToHexString($tesDigest.GetHashAndReset()).ToLowerInvariant()
    } finally { $tesDigest.Dispose() }
}
