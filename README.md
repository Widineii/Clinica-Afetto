# Agenda Afetto

![Status](https://img.shields.io/badge/status-online%20demo-16a34a)
![CI](https://github.com/Widineii/clinica-agenda/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17+-f97316)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-22c55e)
![Database](https://img.shields.io/badge/database-H2%20%7C%20PostgreSQL%20%7C%20MySQL-2563eb)
![Deploy](https://img.shields.io/badge/deploy-Railway%20%2B%20Neon-5b21b6)

Sistema web de agenda para clinica com controle por profissional, sala, cliente, data e horario.

Este projeto foi desenvolvido como item de portfolio para substituir o uso de planilhas abertas por um fluxo mais seguro, organizado e visual para a rotina de uma clinica.

## Producao (Neon + Railway) — recomendado

- Deploy: **[DEPLOY-NEON-RAILWAY.md](DEPLOY-NEON-RAILWAY.md)** (banco Neon grátis + site Railway Hobby)
- Alternativa: **[DEPLOY-NEON-RENDER.md](DEPLOY-NEON-RENDER.md)** (banco Neon + site Render)
- Repositorio: [github.com/Widineii/Clinica-Afetto](https://github.com/Widineii/Clinica-Afetto)
- Apos o deploy: URL gerada no Railway (ex.: `https://clinica-agenda-production.up.railway.app`)

## Screenshots

### Tela de login

![Tela de login](docs/images/login.png)

### Dashboard e grade semanal

![Dashboard principal](docs/images/dashboard-overview.png)

### Detalhe da agenda da clinica

![Detalhe da agenda](docs/images/dashboard-details.png)

## Funcionalidades

- Login por usuario
- Separacao entre administracao e profissionais
- Agendamento por sala, data, horario e cliente
- Grade semanal inspirada na rotina real da clinica
- Suporte a atendimentos avulsos, fixos e quinzenais
- Bloqueio de cancelamento com menos de 24 horas para profissionais
- Cadastro de novos profissionais pela administracao
- Profiles separados para ambiente local e producao

## Tecnologias

- Java 17
- Spring Boot
- Spring MVC
- Spring Data JPA
- Thymeleaf
- H2 Database
- PostgreSQL
- MySQL
- Bootstrap 5
- Maven
- Docker
- Railway + Neon
- Render (alternativa)

## Como rodar localmente

Requisitos:

- Java 17+
- Maven Wrapper do projeto

No Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Em Linux/macOS:

```bash
./mvnw spring-boot:run
```

Acesso local:

- Login: [http://localhost:8081/login](http://localhost:8081/login)
- Dashboard: [http://localhost:8081/agendamentos/dashboard](http://localhost:8081/agendamentos/dashboard)

## Acesso de demonstracao

As credenciais da demonstracao nao ficam expostas neste repositorio publico.

Para apresentar a versao online em entrevista, curriculo ou portfolio, o acesso pode ser disponibilizado separadamente.

## Estrutura do projeto

```text
src/main/java/com/clinica/sistema
|-- config
|-- controller
|-- dto
|-- model
|-- repository
`-- service

src/main/resources
|-- templates
|-- application-local.properties
|-- application-prod.properties
`-- application.properties
```

## Diferenciais para portfolio

- Projeto baseado em caso real de uso
- Migracao de planilha para sistema web
- Interface ajustada para exibicao de grade semanal
- Regras de negocio da clinica implementadas no backend
- Versao online preparada para apresentacao
- Testes automatizados em services
- Deploy preparado com Docker e Render (PostgreSQL)

## Como apresentar em entrevista

1. Mostre o problema: agenda em planilha e risco de desorganizacao.
2. Abra o login e explique os perfis.
3. Mostre a grade semanal por sala e profissional.
4. Explique as regras de atendimento fixo, avulso e quinzenal.
5. Finalize mostrando que o projeto tem deploy online, testes e estrutura de producao.

## Autor

Desenvolvido por **Widinei Martins**.

- GitHub: [github.com/Widineii](https://github.com/Widineii)
- LinkedIn: [linkedin.com/in/widineimartinsdev](https://www.linkedin.com/in/widineimartinsdev)
- WhatsApp: [w.app/widineii](https://w.app/widineii)
