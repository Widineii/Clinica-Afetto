# Deploy no Render (site)

**Banco recomendado:** [Neon](https://neon.tech) — veja **[DEPLOY-NEON-RENDER.md](DEPLOY-NEON-RENDER.md)** (guia completo Neon + Render).

Resumo das variaveis no Web Service:

```
SPRING_PROFILES_ACTIVE=prod
ADMIN_LOGIN=admin
ADMIN_PASSWORD=sua-senha-forte
ADMIN_NAME=Administracao
DATABASE_URL=postgresql://...  (connection string do Neon)
```

O Postgres do proprio Render free expira em 30 dias; por isso usamos Neon para o banco.
