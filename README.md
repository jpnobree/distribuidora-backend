# Backend — Distribuidora (Java / Spring Boot)

![CI](https://github.com/jpnobree/distribuidora-backend/actions/workflows/ci.yml/badge.svg)

API REST em **Java 17 + Spring Boot 3** com login (JWT) e dois papéis de
usuário:

- **ADMIN** — cadastra produtos e altera preços/dados do catálogo.
- **USER** — visualiza o catálogo e envia mensagens de contato para o
  vendedor (equivalente ao botão "Solicitar orçamento" do front-end).

Este backend é independente do projeto `distribuidora-frontend` (front-end
React), que já consome esta API via `fetch` para catálogo, login e gestão
de produtos.

## Arquitetura

```mermaid
flowchart LR
    subgraph Cliente["Navegador"]
        FE["React + Vite\n(distribuidora-frontend)"]
    end

    subgraph API["distribuidora-backend — Spring Boot"]
        SEC["SecurityConfig\n+ JwtAuthFilter"]
        CTRL["Controllers\nAuth · Product · Category · Contact · Upload"]
        SVC["Services\nregra de negócio"]
        REPO["Repositories\nSpring Data JPA"]
    end

    DB[("PostgreSQL")]
    FILES[("./uploads\nimagens de produto")]

    FE -- "REST + JWT" --> SEC
    SEC --> CTRL --> SVC --> REPO --> DB
    CTRL -- "multipart" --> FILES
    FILES -- "estático /uploads/**" --> FE
```

Camadas clássicas do Spring Boot (`controller → service → repository`), com
DTOs isolando a API das entidades JPA — não é Clean Architecture formal, mas
uma arquitetura em camadas correta para o tamanho atual do projeto.

## Pré-requisitos

- **Docker** (recomendado — sobe backend + Postgres com um comando, sem
  precisar instalar Java nem Postgres na máquina), **ou**
- **JDK 17 ou superior** + **PostgreSQL** rodando localmente, se preferir
  rodar sem Docker.

## Como rodar

### Opção 1 — Docker (recomendado)

```bash
docker compose up --build
```

Sobe o backend **e** um Postgres já configurado, aplica as migrations do
Flyway automaticamente e popula os dados de exemplo. A API fica em
`http://localhost:8080`. Para parar e limpar os dados: `docker compose down -v`.

### Opção 2 — Java local

Não é necessário instalar o Maven: o projeto inclui o **Maven Wrapper**
(`mvnw` / `mvnw.cmd`), que baixa o Maven automaticamente na primeira
execução (precisa de internet nessa primeira vez).

```bash
mvnw.cmd spring-boot:run    # Windows
./mvnw spring-boot:run      # Linux/Mac/Git Bash
```

Ou abra a pasta no **IntelliJ IDEA** (detecta o `pom.xml` e importa como
projeto Maven automaticamente) e rode a classe `BackendApplication`.

Você precisa ter um PostgreSQL rodando e ajustar a conexão via variáveis de
ambiente (ou editando os valores padrão em `application.properties`):

```bash
DB_URL=jdbc:postgresql://localhost:5432/distribuidora
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

Na primeira execução, o **Flyway** cria as tabelas e popula os dados
automaticamente (ver
[`src/main/resources/db/migration`](src/main/resources/db/migration)):

- `V1__init_schema.sql` cria o schema.
- `V2__seed_produtos_atacado.sql` cadastra os 86 produtos reais recebidos do
  fornecedor (carnes, hortifruti, frango, embutidos e peixes), com o código
  do fornecedor como SKU.
- O `DataSeeder` cria, além disso, as categorias e dois usuários padrão:

  | usuário   | senha      | papel |
  |-----------|------------|-------|
  | `admin`   | `admin123` | ADMIN |
  | `cliente` | `cliente123` | USER  |

  **Troque essas senhas** (variáveis `ADMIN_SEED_PASSWORD`/`DEMO_SEED_PASSWORD`,
  ou as chaves `app.seed.*` em `application.properties`) antes de usar isso
  fora da sua máquina.

## Documentação da API (Swagger)

Com o backend rodando: **http://localhost:8080/swagger-ui.html** — lista
todos os endpoints, com "Try it out" para testar direto do navegador
(cole o token JWT em Authorize).

## Testes

Testes unitários (JUnit 5 + Mockito) na camada de service, e testes de
integração da camada web com `MockMvc` — estes últimos sobem o contexto de
segurança de verdade (`SecurityConfig`), então provam que as regras de
acesso funcionam, não só o service isolado.

```bash
mvnw.cmd test    # Windows
./mvnw test      # Linux/Mac/Git Bash
```

| Classe | O que é coberto |
|---|---|
| `ProductServiceTest` | busca por slug — sucesso e "não encontrado" |
| `AuthServiceTest` | cadastro (sucesso e username duplicado), login (sucesso e credenciais inválidas) |
| `ContactServiceTest` | criar mensagem, usuário inexistente, listar só as próprias mensagens |
| `ProductControllerTest` | catálogo público, `403` sem papel ADMIN, `201` criando como ADMIN, `400` em validação |
| `LoginRateLimitFilterTest` | confirma que o rate limit do login conta certo (não em dobro) e bloqueia com `429` após o limite |

Além disso, o `docker-compose.yml` foi testado de ponta a ponta contra um
Postgres real (migration do Flyway, login, paginação, filtro por categoria
e por busca, Swagger) — não é um teste automatizado, mas foi validado
manualmente antes de cada entrega.

Ainda faltam: testes de integração com Testcontainers (Postgres real dentro
da suíte de testes) e cobertura dos demais controllers/services.

## Autenticação

1. **Login** — `POST /api/auth/login`
   ```json
   { "username": "admin", "password": "admin123" }
   ```
   Retorna `{ "token": "...", "username": "admin", "role": "ADMIN" }`.

2. Use o token nas próximas chamadas, no header:
   ```
   Authorization: Bearer <token>
   ```

3. **Cadastro** (sempre cria papel USER — conta ADMIN não é auto-cadastrável
   por segurança) — `POST /api/auth/register`
   ```json
   { "username": "novo_cliente", "password": "senha123", "email": "opcional@exemplo.com" }
   ```

## Endpoints

| Método | Rota                       | Quem pode acessar        | Descrição |
|--------|----------------------------|---------------------------|-----------|
| POST   | `/api/auth/register`       | Público                   | Cria conta USER |
| POST   | `/api/auth/login`          | Público                   | Login, retorna JWT. Limitado a 5 tentativas/minuto por IP (`429` acima disso) |
| GET    | `/api/categories`          | Público                   | Lista categorias |
| GET    | `/api/products`            | Público                   | Catálogo paginado — aceita `?page=`, `?size=` (padrão 100), `?category=` e `?search=` |
| GET    | `/api/products/{slug}`     | Público                   | Detalhe de um produto |
| POST   | `/api/products`            | **ADMIN**                 | Cria produto |
| PUT    | `/api/products/{slug}`     | **ADMIN**                 | Atualiza produto (todos os campos) |
| PATCH  | `/api/products/{slug}/price` | **ADMIN**                | Atalho: só troca o preço |
| DELETE | `/api/products/{slug}`     | **ADMIN**                 | Remove produto |
| POST   | `/api/uploads`              | **ADMIN**                 | Envia uma imagem, retorna a URL para usar no campo `image` |
| GET    | `/uploads/{arquivo}`        | Público                   | Serve a imagem enviada |
| POST   | `/api/contacts`             | Logado (ADMIN ou USER)    | Envia mensagem para o vendedor |
| GET    | `/api/contacts/mine`        | Logado (ADMIN ou USER)    | Lista as próprias mensagens |
| GET    | `/api/contacts`             | **ADMIN**                 | Lista todas as mensagens recebidas |

O catálogo (`GET /api/products` e `/api/categories`) é público de propósito,
para manter o mesmo espírito de vitrine aberta que o front-end já tem hoje.
Se preferir exigir login também para visualizar, basta remover essas duas
linhas de `.permitAll()` em
[`SecurityConfig.java`](src/main/java/com/distribuidora/backend/config/SecurityConfig.java).

### Exemplo: listar o catálogo (paginado, com filtro)

```
GET /api/products?category=carnes-aves&search=picanha&page=0&size=20
```

```json
{
  "content": [ { "id": "picanha-premium-98562", "name": "Picanha Premium", "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Essa resposta é cacheada (Caffeine, 10 min) e invalidada automaticamente
sempre que um produto é criado/editado/removido — o cliente nunca vê dado
desatualizado, mas não bate no banco a cada visita ao catálogo.

### Exemplo: criar um produto (ADMIN)

```json
POST /api/products
Authorization: Bearer <token do admin>

{
  "slug": "azeitona-verde-99001",
  "sku": "99001",
  "name": "Azeitona Verde com Caroço",
  "category": "mercearia",
  "unit": "balde 2kg",
  "price": 32.5,
  "tags": ["Importado"],
  "description": "Azeitona verde em salmoura.",
  "origin": "Argentina",
  "available": true
}
```

### Exemplo: enviar a imagem de um produto (ADMIN)

```
POST /api/uploads
Authorization: Bearer <token do admin>
Content-Type: multipart/form-data

file: <arquivo da imagem>
```

Resposta: `{ "url": "http://localhost:8080/uploads/<nome-gerado>.jpg" }` — use essa
URL no campo `image` do produto (`POST`/`PUT /api/products`). Os arquivos ficam
salvos em `./uploads` (fora do controle de versão) e são servidos publicamente
em `/uploads/{arquivo}`.

### Exemplo: cliente entra em contato com o vendedor

```json
POST /api/contacts
Authorization: Bearer <token de qualquer usuario logado>

{
  "productSlug": "picanha-premium-98562",
  "message": "Qual o preço para 20kg?",
  "phone": "(85) 99999-9999"
}
```

## Estrutura do projeto

```
src/main/java/com/distribuidora/backend/
  config/       SecurityConfig, WebConfig, DataSeeder (dados iniciais)
  controller/   Endpoints REST (Auth, Product, Category, Contact, Upload)
  dto/          Objetos de entrada/saída da API (inclui PageResponse)
  exception/    Tratamento de erros (404, 409, validação)
  model/        Entidades JPA (User, Product, Category, ContactMessage)
  repository/   Acesso a dados (Spring Data JPA + Specifications)
  security/     JWT, LoginRateLimitFilter, integração com Spring Security
  service/      Regras de negócio (cache no catálogo)
src/main/resources/
  db/migration/       Migrations do Flyway (fonte de verdade do schema)
  application.properties       Config padrão (com fallback para dev local)
  application-prod.properties  Overrides sem fallback (exige env vars)
Dockerfile, docker-compose.yml, .github/workflows/ci.yml
```

## Configuração

A maior parte fica em `src/main/resources/application.properties`, com
segredos lidos de variáveis de ambiente (`${VAR:valor-padrao-para-dev}`):

| Variável | Para quê | Obrigatória em prod |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Conexão com o Postgres | Sim |
| `JWT_SECRET` | Assinatura do token JWT | Sim |
| `ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD` | Credenciais do admin criado no primeiro start | Sim |
| `DEMO_SEED_USERNAME` / `DEMO_SEED_PASSWORD` | Credenciais do usuário de demonstração | Sim |

Ative o perfil de produção com `--spring.profiles.active=prod` (ou
`SPRING_PROFILES_ACTIVE=prod`): sem os valores padrão de desenvolvimento,
a aplicação falha ao subir se alguma dessas variáveis não for definida —
de propósito, para nunca ir ao ar com o segredo/senha de exemplo.

Outros pontos de configuração:
- `spring.cache.caffeine.spec` — tamanho/expiração do cache do catálogo.
- `app.rate-limit.login.*` — tentativas de login permitidas por minuto/IP.
- CORS: hoje libera `http://localhost:5173` (endereço padrão do front-end
  Vite). Ajuste em `SecurityConfig.corsConfigurationSource()` quando for
  publicar o front-end em um domínio real.

## Próximos passos sugeridos

- Deploy real (Render/Railway + banco gerenciado) usando a imagem Docker
  já pronta.
- Testes de integração com Testcontainers.
- Se precisar de mais granularidade de permissões no futuro (ex: um papel
  "vendedor" separado de "admin"), o enum `Role` em
  `model/Role.java` é o ponto de partida.
