$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$lp = Invoke-WebRequest -Uri 'http://localhost:8081/login' -WebSession $s -UseBasicParsing
$csrf = [regex]::Match($lp.Content, 'name="_csrf"\s+value="([^"]+)"').Groups[1].Value
Invoke-WebRequest -Uri 'http://localhost:8081/login' -Method POST -WebSession $s -UseBasicParsing -Body @{
    login = 'polyana'
    senha = '297b'
    _csrf = $csrf
} | Out-Null
$d = Invoke-WebRequest -Uri 'http://localhost:8081/agendamentos/dashboard' -WebSession $s -UseBasicParsing
$c = $d.Content
Write-Host "Meus ganhos nav: $($c.Contains('Meus ganhos'))"
Write-Host "painelValorConsultaPolyana: $($c.Contains('painelValorConsultaPolyana'))"
Write-Host "meus-ganhos id: $($c.Contains('id=""meus-ganhos""'))"
Write-Host "podeAcompanhar true in JS: $($c.Contains('podeAcompanharGanhosConsultaPropria = true'))"
