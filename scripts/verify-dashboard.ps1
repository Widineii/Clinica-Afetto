$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = if ($args.Count -ge 1) { $args[0] } else { 'carol' }
$senha = if ($args.Count -ge 2) { $args[1] } else { '297b' }
$lp = Invoke-WebRequest -Uri 'http://localhost:8081/login' -WebSession $s -UseBasicParsing
$csrf = [regex]::Match($lp.Content, 'name="_csrf"\s+value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri 'http://localhost:8081/login' -Method POST -WebSession $s -UseBasicParsing -Body @{
    login = $login
    senha = $senha
    _csrf = $csrf
} | Out-Null
$d = Invoke-WebRequest -Uri 'http://localhost:8081/agendamentos/dashboard' -WebSession $s -UseBasicParsing
$c = $d.Content
Write-Host "mensal-linha: $($c.Contains('meus-agendamentos-mensal-linha'))"
Write-Host "Marcar: $($c.Contains('Marcar outra consulta'))"
Write-Host "Mensal accordion: $($c -match '<span>Mensal</span>')"
$d.Content | Out-File -FilePath (Join-Path $PSScriptRoot '..\dash-check.html') -Encoding utf8
