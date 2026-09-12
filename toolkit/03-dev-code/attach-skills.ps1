# attach-skills.ps1
# Chay tu GOC PROJECT MOI (noi da copy nguyen thu muc "toolkit" vao).
# Copy cac skill tinh (khong can cap nhat song) tu toolkit/03-dev-code/SKILL/
# vao .claude/skills/ cua project nay, de Claude Code/Codex tu nhan dien va nap duoc.
#
# CACH DUNG:
#   .\toolkit\03-dev-code\attach-skills.ps1 -Stack react-native-expo
#   .\toolkit\03-dev-code\attach-skills.ps1 -Stack android-native
#
# Script luon gan SKILL/_shared/* (dung cho moi nen tang) + SKILL/<stack>/*.
#
# QUY TAC BAO TRI: them skill tinh moi vao dung thu muc con trong SKILL/ thi TU DONG
# duoc gom theo (khong can sua file nay).

param(
    [Parameter(Mandatory = $true)]
    [string]$Stack
)

$skillSource = Join-Path $PSScriptRoot "SKILL"
$skillTarget = ".claude\skills"

if (-not (Test-Path $skillSource)) {
    Write-Host "Khong tim thay $skillSource - dang chay dung tu goc project chua toolkit/03-dev-code/ ?" -ForegroundColor Red
    exit 1
}

$stackPath = Join-Path $skillSource $Stack
if (-not (Test-Path $stackPath)) {
    Write-Host "Khong co tech-stack '$Stack'. Cac lua chon hop le:" -ForegroundColor Red
    Get-ChildItem $skillSource -Directory | Where-Object { $_.Name -ne "_shared" } | ForEach-Object {
        Write-Host "  - $($_.Name)" -ForegroundColor Yellow
    }
    exit 1
}

New-Item -ItemType Directory -Force -Path $skillTarget | Out-Null

$groups = @((Join-Path $skillSource "_shared"), $stackPath) | Where-Object { Test-Path $_ }

foreach ($g in $groups) {
    Get-ChildItem $g -Directory | ForEach-Object {
        $dest = Join-Path $skillTarget $_.Name
        Copy-Item $_.FullName $dest -Recurse -Force
        Write-Host "Da gan skill: $($_.Name)" -ForegroundColor Green
    }
}

Write-Host "`nXong. Skill tinh cho '$Stack' da san sang (tu chua trong project, khong phu thuoc may)." -ForegroundColor Cyan

if ($Stack -eq "react-native-expo") {
    Write-Host "Rieng plugin Expo (expo@claude-plugins-official) van can cai global - xem SETUP.md." -ForegroundColor Yellow
}
if ($Stack -eq "android-native") {
    Write-Host "Android Native khong co plugin chinh thuc - toan bo skill deu da vendor san o day." -ForegroundColor Yellow
}
