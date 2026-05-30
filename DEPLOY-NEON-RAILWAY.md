# Neon (banco grátis) + Railway Hobby (site) — produção

| Onde | O que |
|------|--------|
| **Neon** | PostgreSQL — agendamentos **não somem** ao reiniciar o site |
| **Railway** | App Java (Docker) — plano **Hobby** (~US$ 5/mês) |

**Não crie PostgreSQL no Railway** — você pagaria banco duas vezes. Só Neon + app.

---

## Antes de começar

- [ ] Conta [neon.tech](https://neon.tech) (GitHub vale)
- [ ] Conta [railway.app](https://railway.app) com plano **Hobby** ativo
- [ ] Código no GitHub: [github.com/Widineii/clinica-agenda](https://github.com/Widineii/clinica-agenda)
- [ ] Senhas anotadas **só no seu caderno/app de senhas** — nunca no WhatsApp da clínica

---

## Parte 1 — Banco no Neon (10 min)

### 1.1 Criar projeto

1. [console.neon.tech](https://console.neon.tech) → **New Project**
2. Nome: `clinica-agenda`
3. Region: **US East (Ohio)** ou **AWS US East** (perto dos servidores Railway EUA)
4. Postgres: **16**

### 1.2 Definir senha do banco (recomendado)

Neon → **SQL Editor** → execute (troque a senha por uma forte sua):

```sql
ALTER ROLE neondb_owner WITH PASSWORD 'SUA_SENHA_FORTE_AQUI';
```

### 1.3 Anotar os dados de conexão

Neon → **Dashboard** → **Connection details** → aba **Parameters** (não use URL com `****`):

| Campo Neon | Variável no Railway |
|------------|---------------------|
| Host | `PGHOST` |
| Database | `PGDATABASE` (geralmente `neondb`) |
| User | `PGUSER` (geralmente `neondb_owner`) |
| Password | `PGPASSWORD` (a que você definiu no SQL) |
| Port | `PGPORT` = `5432` |
| SSL | `PGSSLMODE` = `require` |

Exemplo de host: `ep-xxxx.us-east-1.aws.neon.tech`

---

## Parte 2 — Código no GitHub (se ainda não estiver)

Na pasta do projeto (PowerShell):

```powershell
cd "c:\Users\widin\Downloads\aaaaaa-main\aaaaaa-main"
git init
git add .
git commit -m "Deploy producao Neon + Railway"
git branch -M main
git remote add origin https://github.com/Widineii/clinica-agenda.git
git push -u origin main
```

Se o repositório já existir e só faltar atualizar:

```powershell
git add .
git commit -m "Atualiza deploy Neon + Railway"
git push
```

---

## Parte 3 — Railway (15 min)

### 3.1 Novo projeto

1. [railway.app/new](https://railway.app/new)
2. **Deploy from GitHub repo** → autorize GitHub → escolha **clinica-agenda** / branch **main**
3. Workspace: confirme plano **Hobby** ($5/mês)

### 3.2 Só um serviço (app)

- Deve aparecer **um** serviço (o app).
- Se você criou **PostgreSQL** no Railway por engano: apague esse serviço de banco (dados vão no Neon).

### 3.3 Build

O projeto já tem `railway.toml` + `Dockerfile` — Railway usa **Docker** automaticamente.

### 3.4 Memória (importante no dia da estreia)

1. Clique no serviço do **app** → **Settings**
2. **Resources** → RAM: **1 GB** (se a fatura apertar depois, teste 512 MB)
3. Salve

### 3.5 Variáveis de ambiente

Serviço do app → **Variables** → adicione:

| NAME | VALUE |
|------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PGHOST` | host do Neon (sem `https://`) |
| `PGPORT` | `5432` |
| `PGDATABASE` | `neondb` |
| `PGUSER` | `neondb_owner` |
| `PGPASSWORD` | senha que você definiu no Neon |
| `PGSSLMODE` | `require` |
| `ADMIN_LOGIN` | `admin` |
| `ADMIN_PASSWORD` | senha forte do admin (você escolhe) |
| `ADMIN_NAME` | `Administracao` |

**Não use** `DATABASE_URL` se o Neon mostrar senha mascarada (`****`).

Opcional (pagamentos InfinitePay em produção):

| NAME | VALUE |
|------|--------|
| `PAGAMENTO_WEBHOOK_SECRET` | segredo do webhook, se usar |

### 3.6 Domínio público

1. Serviço do app → **Settings** → **Networking**
2. **Generate Domain** (ex.: `clinica-agenda-production.up.railway.app`)
3. Anote a URL — é a que a clínica vai usar

### 3.7 Deploy

- **Deployments** → aguarde status **Success** / **Active**
- Logs: procure `Started` sem `PostgreSQL nao configurado`

---

## Parte 4 — Testar (obrigatório)

Substitua `SUA-URL` pelo domínio do Railway.

| Teste | URL / ação | Esperado |
|-------|------------|----------|
| Health | `https://SUA-URL/actuator/health` | `{"status":"UP"}` |
| Login admin | `/login` | `admin` + `ADMIN_PASSWORD` |
| Polyana | `/login` | `polyana` / `297b` |
| Agendamento | criar 1 fixo na agenda | aparece na grade |
| Persistência | **Redeploy** no Railway → abrir de novo | agendamento **continua** |
| Senha | Trocar senha (teste) | funciona |

---

## Parte 5 — Entregar à clínica

**URL:** `https://SUA-URL.up.railway.app`

| Quem | Login | Senha inicial |
|------|-------|----------------|
| Administração | `admin` | a que você definiu em `ADMIN_PASSWORD` |
| Profissionais | login de cada um (ex. `polyana`) | `297b` → **trocar no 1º dia** |

### Dia 1 — muita gente entrando junto

1. **15 min antes:** você ou Polyana abre o site e faz login (acorda Neon + app).
2. Peça para entrarem **em ondas** (não 25 no mesmo segundo).
3. Se ficar lento: esperar 1 min e **F5**.

---

## Custos (referência)

| Item | ~R$/mês |
|------|---------|
| Neon free | R$ 0 |
| Railway Hobby | R$ 28–55 |
| Sobra dos R$ 150 da clínica | R$ 95–120 |

---

## Problemas comuns

| Erro nos logs | Solução |
|---------------|---------|
| `PostgreSQL nao configurado` | Falta `PGHOST` + `PGPASSWORD` (ou `DATABASE_URL` válida) |
| `DATABASE_URL contem asteriscos` | Apague `DATABASE_URL`; use `PGHOST`, `PGUSER`, `PGPASSWORD` |
| `Connection refused` / SSL | `PGSSLMODE=require` e host Neon correto |
| App reinicia (OOM) | Suba RAM para **1 GB** no Railway |
| Site lento só na 1ª abertura do dia | Neon free “dorme”; normal — abrir antes do expediente |
| Deploy falha no build | Ver logs; confirme `Dockerfile` na raiz e push no `main` |

---

## Manutenção

- Atualizar sistema: `git push` → Railway redeploy automático
- Backup Neon: painel Neon → backups (plano free tem limites)
- **Não apagar** projeto Neon sem exportar dados
- Fatura Railway: [railway.app/account/billing](https://railway.app/account/billing) — confira no 1º mês

---

## Checklist final

- [ ] Neon com senha definida
- [ ] Railway Hobby, **sem** Postgres no Railway
- [ ] Variáveis `PGHOST` … `PGSSLMODE` + `ADMIN_PASSWORD`
- [ ] RAM **1 GB**
- [ ] Domínio gerado
- [ ] `/actuator/health` = UP
- [ ] Teste Polyana + agendamento após redeploy
