param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Email = "smoke+$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.com",
    [string]$Password = "password",
    [string]$SpecPath = "$PSScriptRoot\demo-openapi.yaml"
)

$ErrorActionPreference = "Stop"

function Escape-JsonString([string]$Value) {
    return $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n')
}

$authBody = @{ email = $Email; password = $Password } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body $authBody
$headers = @{ Authorization = "Bearer $($auth.token)" }

$projectBody = @{ name = "Smoke $([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"; description = "Automated smoke check" } | ConvertTo-Json
$project = Invoke-RestMethod -Uri "$BaseUrl/api/projects" -Method Post -ContentType "application/json" -Headers $headers -Body $projectBody

$specSource = Get-Content -Raw $SpecPath
$specBody = '{"name":"demo-openapi.yaml","source":"' + (Escape-JsonString $specSource) + '"}'
$version = Invoke-RestMethod -Uri "$BaseUrl/api/projects/$($project.id)/spec-versions" -Method Post -ContentType "application/json" -Headers $headers -Body $specBody

$publishBody = @{ mode = "STATEFUL"; requireApiKey = $false } | ConvertTo-Json
$instance = Invoke-RestMethod -Uri "$BaseUrl/api/spec-versions/$($version.id)/publish" -Method Post -ContentType "application/json" -Headers $headers -Body $publishBody

$mock = Invoke-WebRequest -UseBasicParsing -Uri "$($instance.publicUrl)/orders?__status=200" -TimeoutSec 15
$logs = Invoke-RestMethod -Uri "$BaseUrl/api/instances/$($instance.id)/logs" -Headers $headers

[PSCustomObject]@{
    email = $Email
    projectId = $project.id
    specStatus = $version.status
    publicUrlShown = $null -ne $instance.publicUrl
    tokenPreview = $instance.tokenPreview
    mockStatus = $mock.StatusCode
    logCount = @($logs).Count
} | ConvertTo-Json
