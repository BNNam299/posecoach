# setup-machine.ps1
# Chay khi bat dau MOI project moi (khong chi may moi) - vi day la skill "dong",
# can luon dua ve ban moi nhat, khong chi kiem tra ton tai.
# Cac skill tinh khac (react-native-patterns, react-native-best-practices,
# vercel-react-native-skills, supabase-postgres-best-practices) da vendor trong
# toolkit/03-dev-code/SKILL/, gan vao tung project qua attach-skills.ps1 - KHONG
# lien quan file nay.

$installed = claude plugin list 2>&1 | Select-String "expo@claude-plugins-official"

if ($installed) {
    Write-Host "== Da co plugin Expo - cap nhat len ban moi nhat ==" -ForegroundColor Cyan
    claude plugin update expo@claude-plugins-official
    Write-Host "`nDA UPDATE. Can KHOI DONG LAI Claude Code de ban moi co hieu luc." -ForegroundColor Yellow
} else {
    Write-Host "== Chua co plugin Expo - cai moi (scope: user/global) ==" -ForegroundColor Cyan
    claude plugin install expo@claude-plugins-official
}

Write-Host "`n== Kiem tra lai ==" -ForegroundColor Cyan
claude plugin list

Write-Host "`nXong. Plugin Expo dung duoc o BAT KY project nao tren may nay." -ForegroundColor Green
