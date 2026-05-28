# Neon (banco) + Render (site) — producao da clinica

| Onde | O que |
|------|--------|
| **Neon** | PostgreSQL gratis por mais tempo (com limites de uso) |
| **Render** | Site no ar (`clinica-agenda.onrender.com`) |

Agendamentos ficam no Neon — reiniciar o site no Render **nao apaga** nada.

---

## Parte 1 — Criar banco no Neon (5 min)

1. Acesse [neon.tech](https://neon.tech) e crie conta (GitHub vale)
2. **New Project**
   - Nome: `clinica-agenda`
   - Region: **US East** (perto do Render Virginia)
   - Postgres: versao 16
3. No projeto, abra **Dashboard** → **Connection details**
4. Copie a connection string **pooled** ou **direct** (recomendado para Spring/JPA: **direct**)
   - Formato: `postgresql://usuario:senha@ep-xxxx.us-east-1.aws.neon.tech/neondb?sslmode=require`
5. Guarde em lugar seguro — e a senha do banco

> Plano free Neon: uso generoso para clinica pequena; confira limites em [neon.tech/pricing](https://neon.tech/pricing).

---

## Parte 2 — Web Service no Render (5 min)

1. [dashboard.render.com](https://dashboard.render.com) → **New → Web Service**
2. Repo: **Widineii / clinica-agenda**, branch **main**, **Docker**
3. Name: `clinica-agenda`
4. Region: **Virginia (US East)** (mesma regiao do Neon US East)
5. **Environment Variables** (metodo A — recomendado se "Mostrar senha" do Neon falhar):

**Apague** `DATABASE_URL` se existir. Use estas:

| NAME | VALUE |
|------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `ADMIN_LOGIN` | `admin` |
| `ADMIN_PASSWORD` | senha forte do admin (voce escolhe) |
| `ADMIN_NAME` | `Administracao` |
| `PGHOST` | host do Neon (ex. `ep-plain-band-ap9a4yvm.c-7.us-east-1.aws.neon.tech`) |
| `PGPORT` | `5432` |
| `PGDATABASE` | `neondb` |
| `PGUSER` | `neondb_owner` |
| `PGPASSWORD` | senha que voce definir no passo "Senha manual" abaixo |
| `PGSSLMODE` | `require` |

### Senha manual no Neon (quando o painel da erro)

1. Neon → menu **SQL Editor**
2. Cole e execute (troque a senha se quiser):

```sql
ALTER ROLE neondb_owner WITH PASSWORD 'AffettoClinica2026';
```

3. No Render, `PGPASSWORD` = `AffettoClinica2026` (a mesma senha, sem espacos)

Metodo B: uma variavel `DATABASE_URL` com a string completa do Neon (so se **Copiar** trouxer senha real, nao `****`).

6. **Deploy Web Service**

---

## Parte 3 — Testar

- `https://clinica-agenda.onrender.com/actuator/health` → `UP`
- Login `admin` + `ADMIN_PASSWORD`
- Polyana: `polyana` / `297b`
- Criar agendamento → **Manual Deploy** no Render → agendamento continua (dados no Neon)

---

## Blueprint (opcional)

Se usar **New → Blueprint**, o `render.yaml` so cria o Web Service. Voce ainda preenche no painel:

- `ADMIN_PASSWORD`
- `DATABASE_URL` (Neon)

---

## Problemas comuns

| Erro | Solucao |
|------|---------|
| `PostgreSQL nao configurado` | Falta `DATABASE_URL` no Render |
| SSL / connection | Use a URL do Neon com `sslmode=require` |
| Site lento ao abrir | Plano free do Render dorme ~15 min sem uso |
| Trocar senha do banco Neon | Atualize `DATABASE_URL` no Render e redeploy |

---

## Entrega ao cliente

- URL: `https://clinica-agenda.onrender.com` (ou seu dominio)
- Admin: `admin` + senha que voce definiu
- Profissionais: logins do sistema, senha inicial `297b` → trocar no primeiro dia
