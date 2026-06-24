# Deploy na KingHost — VPS 4 GB Linux

Guia para subir a Clínica Afetto no VPS (app + PostgreSQL, sem Neon/Railway).

---

## Passo 1 — Configurar o VPS no painel KingHost

1. Painel → **Configurar** (VPS 4 GB LINUX)
2. Escolha:
   - **Ubuntu 22.04** ou **24.04 LTS**
   - Senha root forte (anote)
   - SSH habilitado
3. Aguarde **5–15 min** até o VPS ficar **Ativo**
4. Anote o **IP público** do VPS (ex.: `177.x.x.x`)

---

## Passo 2 — Entrar no servidor (SSH)

No seu PC:

```bash
ssh root@IP_DO_VPS
```

(Troque `IP_DO_VPS` pelo IP que a KingHost mostrar.)

---

## Passo 3 — Instalar Docker

No VPS:

```bash
apt update && apt upgrade -y
apt install -y git curl
curl -fsSL https://get.docker.com | sh
systemctl enable docker
systemctl start docker
```

Teste:

```bash
docker --version
docker compose version
```

---

## Passo 4 — Baixar o projeto

```bash
cd /opt
git clone https://github.com/Widineii/Clinica-Afetto.git clinica-afetto
cd clinica-afetto
git pull origin main
```

---

## Passo 5 — Variáveis de ambiente

```bash
cp .env.kinghost.example .env
nano .env
```

Preencha senhas fortes em `PGPASSWORD`, `ADMIN_PASSWORD` e `PAGAMENTO_WEBHOOK_SECRET`.

Em `APP_INFINITEPAY_BASE_URL`, use primeiro `http://IP_DO_VPS:8080` e depois troque pelo domínio com HTTPS.

---

## Passo 6 — Subir app + banco

```bash
docker compose -f docker-compose.kinghost.yml up -d --build
```

Aguarde o build (5–10 min na primeira vez). Acompanhe:

```bash
docker compose -f docker-compose.kinghost.yml logs -f app
```

Quando aparecer `Started` sem erro, teste:

```bash
curl -s http://localhost:8080/actuator/health
```

Esperado: `"status":"UP"`.

No navegador: `http://IP_DO_VPS:8080/login`

---

## Passo 7 — Migrar dados do Neon (se já tinha produção)

**No seu PC** (com `pg_dump` instalado ou via Neon SQL dump):

```bash
pg_dump "postgresql://neondb_owner:SENHA@ep-orange-shape-apb7aixp-pooler.c-7.us-east-1.aws.neon.tech/neondb?sslmode=require" > backup-neon.sql
```

Envie para o VPS:

```bash
scp backup-neon.sql root@IP_DO_VPS:/opt/clinica-afetto/
```

**No VPS**, pare o app, restaure, suba de novo:

```bash
cd /opt/clinica-afetto
docker compose -f docker-compose.kinghost.yml stop app
docker exec -i clinica-postgres psql -U clinica -d clinica_agenda < backup-neon.sql
docker compose -f docker-compose.kinghost.yml start app
```

Se o dump Neon usar schema `public` com outro owner, pode precisar ajuste — avise se der erro.

---

## Passo 8 — Domínio e HTTPS (depois que login funcionar)

1. DNS do domínio → registro **A** apontando para `IP_DO_VPS`
2. Instalar Nginx + Certbot no VPS
3. Proxy `443` → `localhost:8080`
4. Atualizar `APP_INFINITEPAY_BASE_URL` no `.env` para `https://seudominio.com.br`
5. `docker compose -f docker-compose.kinghost.yml up -d`

---

## Comandos úteis

```bash
# Ver logs
docker compose -f docker-compose.kinghost.yml logs -f app

# Reiniciar após mudar .env
docker compose -f docker-compose.kinghost.yml up -d --build

# Backup diário do banco (cron)
docker exec clinica-postgres pg_dump -U clinica clinica_agenda > /root/backup-$(date +%F).sql
```

---

## Firewall (recomendado)

```bash
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 8080/tcp
ufw enable
```

Depois do Nginx, pode fechar a porta 8080 externamente e deixar só 80/443.

---

## Checklist

- [ ] VPS configurado (Ubuntu)
- [ ] Docker instalado
- [ ] `.env` com senhas fortes
- [ ] `docker compose up` OK
- [ ] `/actuator/health` = UP
- [ ] Login funciona
- [ ] Dados migrados do Neon (se aplicável)
- [ ] Domínio + HTTPS
- [ ] Desligar Railway/Neon só depois de tudo OK

---

## Deploy automatico (GitHub Actions)

Apos cada `git push` na branch `main`, o workflow **CI** roda os testes. Se passar, o workflow **Deploy KingHost** entra no VPS, atualiza o codigo e faz rebuild.

### Secrets no GitHub (obrigatorio)

Repositorio → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret | Exemplo | Descricao |
|--------|---------|-----------|
| `KINGHOST_HOST` | `177.x.x.x` | IP publico do VPS |
| `KINGHOST_USER` | `root` | Usuario SSH |
| `KINGHOST_SSH_KEY` | conteudo da chave privada | Chave SSH (sem senha) autorizada no VPS |

**Gerar chave no PC (se ainda nao tiver):**

```bash
ssh-keygen -t ed25519 -f kinghost-deploy -N ""
```

Copie `kinghost-deploy.pub` para `/root/.ssh/authorized_keys` no VPS.  
Cole o conteudo de `kinghost-deploy` (privada) no secret `KINGHOST_SSH_KEY`.

### Disparo manual

GitHub → **Actions** → **Deploy KingHost** → **Run workflow**.

### Fluxo

1. `git push origin main`
2. **CI** — `./mvnw test`
3. **Deploy KingHost** — SSH no VPS → `scripts/deploy-kinghost.sh`
4. Confira a versao no rodape do site (ex.: `2.822`)

Se os secrets nao estiverem configurados, o deploy e ignorado (workflow verde, sem alterar o servidor).

---

## Deploy automatico direto no VPS (recomendado)

Esse modo deixa o VPS sempre na mesma versao da branch `main` do GitHub. A cada 5 minutos o servidor confere se existe commit novo; se existir, ele atualiza o codigo e roda rebuild do Docker.

No VPS:

```bash
cd /opt/clinica-afetto
git fetch origin main
git reset --hard origin/main
chmod +x scripts/install-auto-deploy-kinghost.sh
./scripts/install-auto-deploy-kinghost.sh
```

Conferir se ficou ativo:

```bash
systemctl status clinica-afetto-auto-deploy.timer
journalctl -u clinica-afetto-auto-deploy.service -f
```

Rodar uma atualizacao agora, sem esperar o timer:

```bash
systemctl start clinica-afetto-auto-deploy.service
```

Desativar o automatico:

```bash
cd /opt/clinica-afetto
chmod +x scripts/uninstall-auto-deploy-kinghost.sh
./scripts/uninstall-auto-deploy-kinghost.sh
```
