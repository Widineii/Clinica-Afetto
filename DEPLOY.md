# Publicar para cliente

## O que ja esta pronto

- Ambiente local: perfil `local` com H2 e usuarios de demonstracao
- Ambiente publico: perfil `prod` com PostgreSQL por variaveis de ambiente
- Dockerfile para deploy em plataformas como Render e Railway
- Porta configurada por `PORT`

## Variaveis obrigatorias em producao

- `SPRING_PROFILES_ACTIVE=prod`
- `PGHOST`
- `PGPORT`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD`
- `ADMIN_LOGIN`
- `ADMIN_PASSWORD`
- `ADMIN_NAME` opcional

## Exemplo de acesso inicial

- `ADMIN_LOGIN=admin`
- `ADMIN_PASSWORD=troque-essa-senha`

## Render + Neon (recomendado — producao do cliente)

Guia completo: **[DEPLOY-NEON-RENDER.md](DEPLOY-NEON-RENDER.md)**

- **Neon:** PostgreSQL (free tier com mais duracao que Render DB free)
- **Render:** Web Service Docker + variaveis `DATABASE_URL` e `ADMIN_PASSWORD`

## Railway (alternativo)

Guia: **[DEPLOY-RAILWAY.md](DEPLOY-RAILWAY.md)**

## Observacoes

- Em producao nao entram usuarios demo automaticamente.
- As 4 salas sao criadas automaticamente na primeira execucao.
- O admin inicial so e criado se `ADMIN_LOGIN` e `ADMIN_PASSWORD` forem informados.
