# Deploy no Railway

> **Recomendado (mais barato):** banco no **Neon grátis** + app no Railway — **[DEPLOY-NEON-RAILWAY.md](DEPLOY-NEON-RAILWAY.md)**

O guia abaixo usa PostgreSQL **dentro** do Railway (custa mais). Use só se não quiser Neon.

---

# Deploy no Railway com PostgreSQL no Railway (alternativa)

## Por que PostgreSQL (obrigatório para o cliente)

**Sem PostgreSQL**, o sistema usa banco em memória (H2): ao reiniciar o site, **todos os agendamentos somem**.

**Com PostgreSQL ligado ao app no Railway**, os agendamentos ficam no disco do banco — reinício, deploy ou queda do app **não apagam** nada.

- Vários profissionais podem usar **ao mesmo tempo** sem perder dados
- Backup automático no Railway

---

## Passo a passo (amanhã)

### 1. Subir código no GitHub

```bash
cd clinica-agenda-main
git init
git add .
git commit -m "Producao com PostgreSQL e seguranca"
git remote add origin https://github.com/Widineii/clinica-agenda.git
git push -u origin main
```

### 2. No Railway (obrigatório — sem isso o deploy falha)

1. Acesse [railway.app](https://railway.app)
2. Abra o projeto **clinica-agenda**
3. **+ New** → **Database** → **PostgreSQL** (se ainda nao existir)
4. Clique no servico do **app web** (nao no banco) → **Variables** → **+ New Variable** → **Add Reference**
5. Escolha o servico PostgreSQL e a variavel **`DATABASE_URL`** → salvar  
   (o Railway copia `PGHOST`, `PGPORT`, etc. automaticamente)
6. Confira tambem na aba **Variables** do app:
5. Adicione ou confira:

| Variável | Valor |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `ADMIN_LOGIN` | `admin` |
| `ADMIN_PASSWORD` | senha forte (anote em lugar seguro) |
| `ADMIN_NAME` | `Administracao` |
| `SEED_TEST_USER_ENABLED` | `true` (perfil **teste** / senha **297b** para voce testar no ar) |
| `SEED_TEST_USER_LOGIN` | `teste` (opcional) |
| `SEED_TEST_USER_PASSWORD` | `297b` (opcional; minimo 4 caracteres no sistema) |

7. **Redeploy** o app (Deployments → tres pontinhos → Redeploy) e aguarde **Active**
8. Abra o domínio público (ex.: `clinica-agenda-production.up.railway.app`)

### 3. Primeiro acesso

- Login: valor de `ADMIN_LOGIN` (ex.: `admin`)
- Senha: valor de `ADMIN_PASSWORD`
- Profissionais (Polyana, Julia, etc.): login de cada um, senha inicial `297b` — **pedir para trocar** em Trocar senha

### 4. Teste rápido antes de entregar

- [ ] Login admin
- [ ] Login Polyana (`polyana` / `297b`)
- [ ] Criar 1 agendamento fixo
- [ ] Cancelar 1 dia
- [ ] Recarregar a página — agendamento continua correto
- [ ] Trocar senha de teste

---

## Testar PostgreSQL no seu PC (opcional)

```bash
docker compose up -d
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=postgres-local
```

Acesse http://localhost:8081

---

## Se o deploy falhar (status 1)

Erro comum: **PostgreSQL nao ligado ao app** — o log mostra `Connection to localhost:5432 refused` ou `PostgreSQL nao configurado`.

1. **Logs** do servico web (ultimas linhas)
2. No app, variavel **`DATABASE_URL`** deve existir (referencia ao Postgres)
3. `SPRING_PROFILES_ACTIVE=prod`
4. `ADMIN_PASSWORD` definida
5. Health: `https://clinica-agenda-production.up.railway.app/actuator/health` → `UP`

---

## Manutenção

- **Backup:** Railway → PostgreSQL → Backups (ativar se disponível no plano)
- **Não apagar** o serviço PostgreSQL sem exportar dados antes
- Atualizações: `git push` → Railway redeploy automático
