param(
    [Parameter(Mandatory=$true)][ValidateSet('focused','full','hosted')][string]$Run,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
# Resolve script-scoped defaults after parameter binding (Windows PowerShell 5.1).
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) { $OutputDirectory = $PSScriptRoot }
$null = New-Item -ItemType Directory -Force -Path $OutputDirectory
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$task = if ($Run -eq 'hosted') { 'supabaseIntegrationTest' } else { 'test' }
$results = Join-Path $root "backend/build/test-results/$task"
$output = New-Object System.Xml.XmlDocument
$declaration = $output.CreateXmlDeclaration('1.0','UTF-8',$null)
[void]$output.AppendChild($declaration)
$all = $output.CreateElement('testsuites')
[void]$output.AppendChild($all)
$rows = [Collections.Generic.List[string]]::new()
$skips = [Collections.Generic.List[string]]::new()
$totals = @{tests=0; failures=0; errors=0; skipped=0}
function Sanitize-Label([string]$Value) {
    # Parameterized test names can embed credential-shaped negative-test URLs.
    $Value = [regex]::Replace($Value, '((?:https?|postgres(?:ql)?)://)[^\s/:]+:[^\s/@]+@', '$1[REDACTED]@')
    $Value = [regex]::Replace($Value, '(?i)[a-z0-9.-]+\.supabase\.(?:co|com|net)', '[REDACTED-PROVIDER-HOST]')
    return [regex]::Replace($Value, '(?i)([?&](?:password|token|api_key)=)[^\s&"<>]+', '$1[REDACTED]')
}
foreach ($file in Get-ChildItem $results -Filter 'TEST-*.xml' | Sort-Object Name) {
    [xml]$source = [IO.File]::ReadAllText($file.FullName)
    $suite = $output.CreateElement('testsuite')
    foreach ($name in @('name','tests','failures','errors','skipped','time','timestamp')) {
        $suite.SetAttribute($name,(Sanitize-Label $source.testsuite.GetAttribute($name)))
    }
    foreach ($name in @('tests','failures','errors','skipped')) { $totals[$name] += [int]$source.testsuite.GetAttribute($name) }
    foreach ($test in $source.testsuite.testcase) {
        $case = $output.CreateElement('testcase')
        foreach ($name in @('name','classname','time')) { $case.SetAttribute($name,(Sanitize-Label $test.GetAttribute($name))) }
        $status = 'passed'
        if ($test.SelectSingleNode('skipped')) { $status='skipped'; [void]$case.AppendChild($output.CreateElement('skipped')); $skips.Add($case.GetAttribute('classname') + ' :: ' + $case.GetAttribute('name')) }
        if ($test.SelectSingleNode('failure')) { $status='failed'; [void]$case.AppendChild($output.CreateElement('failure')) }
        if ($test.SelectSingleNode('error')) { $status='error'; [void]$case.AppendChild($output.CreateElement('error')) }
        [void]$suite.AppendChild($case)
        $label = [Net.WebUtility]::HtmlEncode($case.GetAttribute('classname') + ' :: ' + $case.GetAttribute('name'))
        $rows.Add("<tr><td>$label</td><td>$status</td></tr>")
    }
    [void]$all.AppendChild($suite)
}
foreach ($name in $totals.Keys) { $all.SetAttribute($name,[string]$totals[$name]) }
$output.Save((Join-Path $OutputDirectory "$Run.xml"))
$passed=$totals.tests-$totals.skipped-$totals.failures-$totals.errors
$html="<!doctype html><html lang='en'><meta charset='utf-8'><title>Issue 26 $Run</title><style>body{font:14px system-ui;margin:2rem}td,th{padding:4px 12px;text-align:left;border-bottom:1px solid #ddd}</style><h1>Gradle $Run results (sanitized)</h1><p>Passed: $passed; failed: $($totals.failures); errors: $($totals.errors); skipped: $($totals.skipped). Derived from Gradle test XML. Logs, properties, hostnames, failure messages and stack traces omitted.</p><table><tr><th>Test</th><th>Result</th></tr>$($rows -join "`n")</table></html>"
[IO.File]::WriteAllText((Join-Path $OutputDirectory "$Run.html"),$html,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllLines((Join-Path $OutputDirectory "$Run-skipped.txt"),$skips,[Text.UTF8Encoding]::new($false))
$sourceFiles = @((git -C $root diff HEAD --name-only) + (git -C $root ls-files --others --exclude-standard)) | Where-Object { $_ -match '^backend/.*\.(java|gradle)$' } | Sort-Object -Unique
$manifest = @(foreach ($relative in $sourceFiles) { (Get-FileHash -Algorithm SHA256 (Join-Path $root $relative)).Hash + '  ' + $relative })
[IO.File]::WriteAllLines((Join-Path $OutputDirectory "$Run-source-sha256.txt"),[string[]]$manifest,[Text.UTF8Encoding]::new($false))
Write-Output "${Run}: tests=$($totals.tests) passed=$passed failed=$($totals.failures) errors=$($totals.errors) skipped=$($totals.skipped)"
