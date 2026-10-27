# Compile and run on Windows PowerShell:  .\run.ps1 [server|demo|errors|bench|test]
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
[console]::OutputEncoding = [System.Text.Encoding]::UTF8

if(Test-Path out) {
    Remove-Item out -Recurse -Force
}
$sources = (Get-ChildItem -Recurse -Path src/main/java -Filter *.java).FullName
javac -encoding UTF-8 -d out $sources
if ($LASTEXITCODE -ne 0) {exit $LASTEXITCODE}

java "-Dstdout.encoding=UTF-8" -Xmx2g -cp out com.uom.engine.Main @args
exit $LASTEXITCODE