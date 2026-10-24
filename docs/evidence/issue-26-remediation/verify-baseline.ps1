$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$baseline = '0202ae87747fee558882c464f1198deb8abec3d3'
$lines = [Collections.Generic.List[string]]::new()
$lines.Add("Reviewed remediation baseline: $baseline")
$lines.Add('Historical manifests are preserved unchanged. Compare raw Git blob bytes first, then explicit LF-to-CRLF checkout representation. No source-content normalization beyond line endings.')
$mismatches = 0
foreach ($run in @('focused', 'full', 'hosted')) {
    $count = 0
    # Read historical manifests from the baseline, never the replacement hosted capture.
    $entries = git -C $root show "${baseline}:docs/evidence/issue-26-remediation/$run-source-sha256.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read baseline manifest' }
    foreach ($entry in $entries) {
        if ($entry -notmatch '^([0-9A-Fa-f]{64})  (.+)$') { throw 'Invalid manifest entry' }
        $expected = $Matches[1]; $relative = $Matches[2]
        $start = [Diagnostics.ProcessStartInfo]::new('git', "-C `"$root`" cat-file blob ${baseline}:$relative")
        $start.UseShellExecute = $false; $start.RedirectStandardOutput = $true; $start.CreateNoWindow = $true
        $process = [Diagnostics.Process]::Start($start)
        $memory = [IO.MemoryStream]::new()
        $process.StandardOutput.BaseStream.CopyTo($memory); $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Cannot read committed blob: $relative" }
        $bytes = $memory.ToArray(); $memory.Dispose(); $process.Dispose()
        $sha = [Security.Cryptography.SHA256]::Create()
        $raw = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '')
        $text = [Text.Encoding]::UTF8.GetString($bytes)
        $checkout = [Text.Encoding]::UTF8.GetBytes($text.Replace("`r`n", "`n").Replace("`n", "`r`n"))
        $crlf = [BitConverter]::ToString($sha.ComputeHash($checkout)).Replace('-', '')
        $sha.Dispose()
        $representation = if ($raw -eq $expected) { 'raw Git blob' } elseif ($crlf -eq $expected) { 'CRLF checkout of Git blob' } else { 'MISMATCH' }
        if ($representation -eq 'MISMATCH') { $mismatches++ }
        $lines.Add("$run $representation : $relative ; recorded=$expected ; raw=$raw ; CRLF=$crlf")
        $count++
    }
    $lines.Add("$run entries verified: $count")
}
$lines.Add("Mismatches: $mismatches")
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'baseline-source-verification.txt'), $lines, [Text.UTF8Encoding]::new($false))
$lines | Write-Output
if ($mismatches -ne 0) { throw 'Historical source verification failed' }
