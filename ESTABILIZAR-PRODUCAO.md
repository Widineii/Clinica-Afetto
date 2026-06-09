# Estabilizar Neon + Railway — checklist (15 min)

Use esta ordem **antes** de pensar em trocar de banco (Turso, Supabase, etc.).

---

## Sintomas

- Deploy no Railway fica **Performing healthchecks** e falha
- Cliente vê **"Algo deu errado"** ao logar
- `/actuator/health` retorna **DOWN**
- Site ainda na **v2.799** (deploy novo não entra)

---

## Passo 1 — Acordar o Neon (2 min)

1. Abra [console.neon.tech](https://console.neon.tech) → projeto **Clínica Afetto**
2. **SQL Editor** → execute:

```sql
SELECT 1;
```

3. Deve retornar `1`. Se der erro, o banco está inacessível (senha, projeto errado ou Neon fora).

4. **Branches** → branch principal → aba **Computes**: status deve ser **Active** (verde). Se estiver **Idle/Suspended**, clique no compute ou rode `SELECT 1` para acordar.

5. Em **Connection details** → aba **Parameters**, anote:
   - Host (deve ter **`-pooler`** no nome)
   - User, Database, Port

---

## Passo 2 — Variáveis no Railway (5 min)

Railway → serviço **clinica-agenda** → **Variables**

### Obrigatórias (sem DATABASE_URL com `****`)

| Variável | Valor correto |
|----------|----------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PGHOST` | Host do Neon **com `-pooler`** (ex.: `ep-xxxx-pooler....neon.tech`) |
| `PGPORT` | `5432` |
| `PGDATABASE` | `neondb` |
| `PGUSER` | `neondb_owner` |
| `PGPASSWORD` | **Mesma senha** definida no Neon (SQL Editor ou reset) |
| `PGSSLMODE` | `require` |

### Memória Java (CRÍTICO)

| Variável | Valor |
|----------|--------|
| `JAVA_OPTS` | `-Xms384m -Xmx768m` |

**Não use** `-Xmx1536m` em plano Hobby (512 MB–1 GB) — o container morre no deploy.

### Apague se existir

- `DATABASE_URL` com senha mascarada (`****`) ou URL antiga de outro projeto

### Admin (login do sistema)

| Variável | Exemplo |
|----------|---------|
| `ADMIN_LOGIN` | `admin` |
| `ADMIN_PASSWORD` | senha forte |
| `ADMIN_NAME` | `Administracao` |

Salve as variáveis.

---

## Passo 3 — RAM no Railway (1 min)

Railway → serviço → **Settings** → Resources

- Recomendado: **1 GB** de RAM (se disponível no plano)
- Com 512 MB, mantenha `JAVA_OPTS=-Xms384m -Xmx768m`

---

## Passo 4 — Redeploy (5–10 min)

1. **Deployments** → **Redeploy** (ou faça `git push` se houver código novo)
2. Aguarde até **10 min** (healthcheck configurado para isso)
3. Status deve ficar **Active** (verde)

Se falhar: **View logs** → copie as últimas 30 linhas.

---

## Passo 5 — Testar (2 min)

| URL | Esperado |
|-----|----------|
| `/login` | Abre, versão **v2.815** ou superior no rodapé |
| `/actuator/health` | `{"status":"UP"}` |
| Login `teste` / `297b` | Entra na agenda |

---

## Erros comuns nos logs

| Log | Solução |
|-----|---------|
| `password authentication failed` | `PGPASSWORD` errada → reset no Neon, atualizar Railway |
| `OutOfMemoryError` | Baixar `JAVA_OPTS` para `-Xmx768m` |
| `Connection refused` / timeout | `PGHOST` errado ou sem `-pooler` |
| `DATABASE_URL contem asteriscos` | Apagar `DATABASE_URL`, usar PGHOST/PGPASSWORD |
| Healthcheck failure 10 min | Banco não conecta ou memória estourou |

---

## Scale to zero do Neon (plano Free)

O Neon **não está fora do ar**. No plano gratuito:

- Após **~5 min sem consultas**, o compute **suspende** (economia).
- Na próxima consulta, **acorda sozinho** em ~0,5–3 s (às vezes mais no cold start).
- **Não dá para desligar** o scale-to-zero no Free — só no plano pago.

**O que isso explica:** login lento na **primeira** vez depois de ficar parado.

**O que isso NÃO explica:** `/actuator/health` ficar **DOWN por 10+ segundos** o tempo todo — isso é senha errada, `PGHOST` errado, `JAVA_OPTS` alto ou deploy antigo (v2.799) ainda no ar.

**Erro hexadecimal / DNS** (8.8.8.8, 1.1.1.1): problema do **seu PC** ao conectar localmente. O Railway usa rede própria; não corrige produção.

**Manter acordado (opcional):** Neon pago → desativar scale-to-zero. No Free, o app já envia keepalive a cada 2 min quando está **UP** e conectado ao banco.

---

## Se ainda não funcionar

1. Neon → **Reset password** do role `neondb_owner`
2. Atualize `PGPASSWORD` no Railway
3. Redeploy de novo

Se após **2 tentativas** continuar DOWN:

- **Opção A:** Neon plano pago (mesmo PostgreSQL, zero mudança no código)
- **Opção B:** Migrar para Supabase PostgreSQL (ainda Postgres)
- **Turso** só se aceitar reescrever/migrar (SQLite, dias de trabalho)

---

## Atualizar código (v2.815+)

Depois de ajustar Railway, suba o código:

```bash
cd /home/dinei/Downloads/Clinica-Afetto-main
git pull
git push origin main
```

Ou redeploy manual no Railway se o GitHub já estiver atualizado.

---

## Checklist rápido

- [ ] `SELECT 1` no Neon OK
- [ ] `PGHOST` com `-pooler`
- [ ] `PGPASSWORD` = senha atual do Neon
- [ ] Sem `DATABASE_URL` quebrada
- [ ] `JAVA_OPTS=-Xms384m -Xmx768m`
- [ ] Redeploy concluído Active
- [ ] `/actuator/health` = UP
- [ ] Login teste funciona
