[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $Name"
    }
}

function Get-FreePort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

function Assert-PortAvailable([int]$Port) {
    $listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if ($listeners | Where-Object { $_.Port -eq $Port }) {
        throw "Required port $Port is already in use. Stop the existing listener before starting the engine stack."
    }
}

function Assert-NativeCommand([string]$Name) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE"
    }
}

function Assert-ProcessRunning([System.Diagnostics.Process]$Process, [string]$Name) {
    Start-Sleep -Milliseconds 250
    $Process.Refresh()
    if ($Process.HasExited) {
        throw "$Name exited during startup with exit code $($Process.ExitCode)"
    }
}

function Start-LoggedProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [hashtable]$Environment,
        [string]$LogDirectory
    )

    $before = @{}
    foreach ($key in $Environment.Keys) {
        $before[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, [string]$Environment[$key], 'Process')
    }
    try {
        $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -PassThru `
            -RedirectStandardOutput (Join-Path $LogDirectory "$Name.out.log") `
            -RedirectStandardError (Join-Path $LogDirectory "$Name.err.log")
        Assert-ProcessRunning $process $Name
        return $process
    }
    finally {
        foreach ($key in $Environment.Keys) {
            [Environment]::SetEnvironmentVariable($key, $before[$key], 'Process')
        }
    }
}

function Wait-HttpJson {
    param([string]$Url, [scriptblock]$Validate, [string]$Name, [System.Diagnostics.Process]$Process)

    $deadline = (Get-Date).AddSeconds(60)
    do {
        if ($Process) { Assert-ProcessRunning $Process $Name }
        try {
            $response = Invoke-RestMethod -Uri $Url -TimeoutSec 3
            if (& $Validate $response) { return $response }
        }
        catch { }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$Name did not become ready at $Url"
}

function Wait-TcpPort {
    param([int]$Port, [string]$Name, [System.Diagnostics.Process]$Process)

    $deadline = (Get-Date).AddSeconds(90)
    do {
        if ($Process) { Assert-ProcessRunning $Process $Name }
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $connected = $client.ConnectAsync('127.0.0.1', $Port).Wait(1000)
            if ($connected -and $client.Connected) { return }
        }
        finally { $client.Dispose() }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$Name did not become reachable on port $Port"
}

function Wait-LogText {
    param([string]$Path, [string]$Text, [string]$Name, [System.Diagnostics.Process]$Process)

    $deadline = (Get-Date).AddSeconds(60)
    do {
        if ($Process) { Assert-ProcessRunning $Process $Name }
        if ((Test-Path $Path) -and (Get-Content -Raw $Path -ErrorAction SilentlyContinue) -match [regex]::Escape($Text)) { return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$Name did not report readiness; inspect $Path"
}

function Quote-Argument([string]$Value) { '"' + $Value.Replace('"', '\"') + '"' }

$repoRoot = Split-Path -Parent $PSScriptRoot
$children = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$succeeded = $false
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("agentic-worker-engine-" + [guid]::NewGuid())
$logs = Join-Path $tempRoot 'logs'

try {
    foreach ($command in @('docker', 'git', 'python', 'uv', 'npm', 'node', 'pwsh')) { Require-Command $command }
    foreach ($port in @(8000, 3001)) { Assert-PortAvailable $port }
    New-Item -ItemType Directory -Path $logs -Force | Out-Null

    $fixtureRoot = Join-Path $tempRoot 'git-fixture'
    $seed = Join-Path $tempRoot 'git-seed'
    $bare = Join-Path $fixtureRoot 'engine-smoke.git'
    New-Item -ItemType Directory -Path $fixtureRoot, $seed -Force | Out-Null
    & git init --initial-branch=main $seed *> (Join-Path $logs 'git-init.log')
    Assert-NativeCommand 'Git fixture initialization'
    Set-Content -Path (Join-Path $seed 'README.md') -Value '# Engine smoke fixture'
    & git -C $seed add README.md
    Assert-NativeCommand 'Git fixture staging'
    & git -C $seed -c user.email=engine@local.test -c user.name=engine-smoke commit -m 'fixture: initial commit' *> (Join-Path $logs 'git-commit.log')
    Assert-NativeCommand 'Git fixture commit'
    & git init --bare $bare *> (Join-Path $logs 'git-bare.log')
    Assert-NativeCommand 'Bare Git fixture initialization'
    & git -C $seed remote add origin $bare
    Assert-NativeCommand 'Git fixture remote configuration'
    & git -C $seed push origin main *> (Join-Path $logs 'git-push.log')
    Assert-NativeCommand 'Git fixture push'
    & git --git-dir=$bare symbolic-ref HEAD refs/heads/main
    Assert-NativeCommand 'Git fixture default branch configuration'
    & git --git-dir=$bare update-server-info
    Assert-NativeCommand 'Git fixture HTTP metadata generation'

    $fakeAgent = Join-Path $tempRoot 'fake-agent.ps1'
    @'
$ErrorActionPreference = 'Stop'
if (-not $env:PLAN_PATH) { throw 'PLAN_PATH is required' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $env:PLAN_PATH) | Out-Null
Set-Content -Path $env:PLAN_PATH -Value ("# Plan " + $env:WORKFLOW_RUN_ID)
'@ | Set-Content -Path $fakeAgent

    $gitPort = Get-FreePort
    $gitServer = Start-LoggedProcess 'git-http' 'python' @('-m', 'http.server', $gitPort, '--bind', '127.0.0.1', '--directory', (Quote-Argument $fixtureRoot)) $repoRoot @{} $logs
    $children.Add($gitServer)
    Wait-TcpPort $gitPort 'Git fixture server' $gitServer
    & git ls-remote "http://127.0.0.1:$gitPort/engine-smoke.git" refs/heads/main *> (Join-Path $logs 'git-ls-remote.log')
    Assert-NativeCommand 'Git fixture HTTP validation'
    if (-not ((Get-Content -Raw (Join-Path $logs 'git-ls-remote.log')) -match 'refs/heads/main')) { throw 'Git fixture HTTP validation did not return main' }

    & docker compose -f docker-compose.dev.yml --profile temporal up -d *> (Join-Path $logs 'docker-compose.log')
    if ($LASTEXITCODE -ne 0) { throw "Temporal Docker profile failed; inspect $(Join-Path $logs 'docker-compose.log')" }
    Wait-TcpPort 7233 'Temporal'

    $workspaceRoot = Join-Path $tempRoot 'worker-workspace'
    $pythonWorker = Start-LoggedProcess 'python-worker' 'uv' @('run', 'uvicorn', 'agent_worker.app:app', '--app-dir', 'src', '--host', '127.0.0.1', '--port', '8000') `
        (Join-Path $repoRoot 'apps/python-agent-worker') @{ WORKER_WORKSPACE_ROOT = $workspaceRoot; AGENT_COMMAND = "pwsh -NoProfile -ExecutionPolicy Bypass -File $(Quote-Argument $fakeAgent)" } $logs
    $children.Add($pythonWorker)

    $gatewayDirectory = Join-Path $repoRoot 'apps/worker-gateway'
    & npm run build --prefix $gatewayDirectory *> (Join-Path $logs 'gateway-build.log')
    if ($LASTEXITCODE -ne 0) { throw "Worker Gateway build failed; inspect $(Join-Path $logs 'gateway-build.log')" }
    $gateway = Start-LoggedProcess 'gateway' 'node' @('dist/main.js') $gatewayDirectory @{ PYTHON_WORKER_URL = 'http://127.0.0.1:8000'; PORT = '3001' } $logs
    $children.Add($gateway)

    $workerCapabilities = Wait-HttpJson 'http://127.0.0.1:8000/v1/capabilities' { param($value) $value.workerId -eq 'python-agent-worker' -and $value.adapterIds -contains 'fake-agent' -and $value.modes -contains 'READ' -and $value.modes -contains 'WRITE' } 'Python Worker' $pythonWorker
    $gatewayCapabilities = Wait-HttpJson 'http://127.0.0.1:3001/v1/capabilities' { param($value) @($value | Where-Object { $_.workerId -eq 'python-agent-worker' -and $_.adapterIds -contains 'fake-agent' -and $_.modes -contains 'READ' -and $_.modes -contains 'WRITE' }).Count -eq 1 } 'Worker Gateway' $gateway

    $temporalDirectory = Join-Path $repoRoot 'apps/temporal-worker'
    $temporalWorker = Start-LoggedProcess 'temporal-worker' 'node' @((Join-Path $repoRoot 'node_modules/tsx/dist/cli.mjs'), 'src/main.ts') $temporalDirectory @{ GATEWAY_URL = 'http://127.0.0.1:3001'; TEMPORAL_ADDRESS = '127.0.0.1:7233'; PROJECT_REPOSITORY_URI = "http://127.0.0.1:$gitPort/engine-smoke.git"; PROJECT_BASE_BRANCH = 'main' } $logs
    $children.Add($temporalWorker)
    Wait-LogText (Join-Path $logs 'temporal-worker.out.log') 'temporal-worker connected:' 'Temporal Worker' $temporalWorker

    [pscustomobject]@{
        tempRoot = $tempRoot
        endpoints = @{ temporal = '127.0.0.1:7233'; temporalUi = 'http://127.0.0.1:8233'; gitFixture = "http://127.0.0.1:$gitPort/engine-smoke.git"; pythonWorker = 'http://127.0.0.1:8000'; gateway = 'http://127.0.0.1:3001' }
        capabilities = @{ pythonWorker = $workerCapabilities; gateway = @($gatewayCapabilities) }
        logs = @{ directory = $logs; docker = (Join-Path $logs 'docker-compose.log'); gitHttp = (Join-Path $logs 'git-http.out.log'); pythonWorker = (Join-Path $logs 'python-worker.out.log'); gateway = (Join-Path $logs 'gateway.out.log'); temporalWorker = (Join-Path $logs 'temporal-worker.out.log') }
        processIds = @($children | ForEach-Object Id)
    } | ConvertTo-Json -Depth 6
    $succeeded = $true
}
catch {
    Write-Error $_
    throw
}
finally {
    if (-not $succeeded) {
        foreach ($child in $children) {
            & taskkill /PID $child.Id /T /F *> $null
        }
        Write-Error "Engine stack setup failed. Logs: $logs"
    }
}
