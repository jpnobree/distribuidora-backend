# Backend — Distribuidora (Java / Spring Boot)

API REST em **Java 17 + Spring Boot 3** com login (JWT) e dois papéis de
usuário:

- **ADMIN** — cadastra produtos e altera preços/dados do catálogo.
- **USER** — visualiza o catálogo e envia mensagens de contato para o
  vendedor (equivalente ao botão "Solicitar orçamento" do front-end).

Este backend é independente do projeto `distribuidora-vitrine` (front-end
React). A ideia é, mais adiante, o front-end passar a buscar os produtos
aqui via `fetch`/`axios` em vez do arquivo estático `src/data/products.js`.

## Pré-requisitos

- **JDK 17 ou superior** instalado (o projeto já foi testado com as
  versões 22 e 23 que aparecem em `C:\Users\<voce>\.jdks`, caso tenham
  sido instaladas pelo IntelliJ).
- Não é necessário instalar o Maven: o projeto inclui o **Maven Wrapper**
  (`mvnw` / `mvnw.cmd`), que baixa o Maven automaticamente na primeira
  execução (precisa de internet nessa primeira vez).
- **PostgreSQL** rodando localmente (ou acessível pela rede), com um banco
  chamado `distribuidora` já criado. As tabelas ficam em
  [`sql/schema.sql`](sql/schema.sql) — rode esse script uma vez no seu
  Postgres antes de subir a aplicação (o Hibernate também tenta criar/
  atualizar as tabelas sozinho graças a `ddl-auto=update`, mas rodar o
  script manualmente deixa isso explícito).

## Como rodar

No Windows (PowerShell ou cmd), dentro da pasta do projeto:

```bash
mvnw.cmd spring-boot:run
```

Linux/Mac (ou Git Bash no Windows):

```bash
./mvnw spring-boot:run
```

Ou, se preferir, abra a pasta no **IntelliJ IDEA** (o projeto já tem um
`pom.xml` na raiz — o IntelliJ detecta e importa como projeto Maven
automaticamente) e rode a classe `BackendApplication`.

A API sobe em `http://localhost:8080`.

Antes de rodar pela primeira vez, ajuste a conexão com o Postgres em
`src/main/resources/application.properties` (host, porta, nome do banco,
usuário, senha):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/distribuidora
spring.datasource.username=postgres
```

Na primeira execução, o Hibernate cria/atualiza as tabelas automaticamente
(mesmo esquema de [`sql/schema.sql`](sql/schema.sql)) e o `DataSeeder` popula:

- Categorias e alguns produtos de exemplo (iguais aos que já existiam no
  front-end).
- Dois usuários padrão:

  | usuário   | senha      | papel |
  |-----------|------------|-------|
  | `admin`   | `admin123` | ADMIN |
  | `cliente` | `cliente123` | USER  |

  **Troque essas senhas** (em `application.properties`, chaves
  `app.seed.*`) antes de usar isso fora da sua máquina.

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
| POST   | `/api/auth/login`          | Público                   | Login, retorna JWT |
| GET    | `/api/categories`          | Público                   | Lista categorias |
| GET    | `/api/products`            | Público                   | Lista o catálogo |
| GET    | `/api/products/{slug}`     | Público                   | Detalhe de um produto |
| POST   | `/api/products`            | **ADMIN**                 | Cria produto |
| PUT    | `/api/products/{slug}`     | **ADMIN**                 | Atualiza produto (todos os campos) |
| PATCH  | `/api/products/{slug}/price` | **ADMIN**                | Atalho: só troca o preço |
| DELETE | `/api/products/{slug}`     | **ADMIN**                 | Remove produto |
| POST   | `/api/contacts`             | Logado (ADMIN ou USER)    | Envia mensagem para o vendedor |
| GET    | `/api/contacts/mine`        | Logado (ADMIN ou USER)    | Lista as próprias mensagens |
| GET    | `/api/contacts`             | **ADMIN**                 | Lista todas as mensagens recebidas |

O catálogo (`GET /api/products` e `/api/categories`) é público de propósito,
para manter o mesmo espírito de vitrine aberta que o front-end já tem hoje.
Se preferir exigir login também para visualizar, basta remover essas duas
linhas de `.permitAll()` em
[`SecurityConfig.java`](src/main/java/com/distribuidora/backend/config/SecurityConfig.java).

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
  config/       SecurityConfig (regras de acesso), DataSeeder (dados iniciais)
  controller/   Endpoints REST (Auth, Product, Category, Contact)
  dto/          Objetos de entrada/saída da API
  exception/    Tratamento de erros (404, 409, validação)
  model/        Entidades JPA (User, Product, Category, ContactMessage)
  repository/   Acesso a dados (Spring Data JPA)
  security/     JWT (geração/validação) e integração com Spring Security
  service/      Regras de negócio
```

## Configuração

Tudo fica em `src/main/resources/application.properties`:

- `app.jwt.secret` / `app.jwt.expiration-minutes` — segredo e validade do
  token. **Troque o segredo antes de ir para produção.**
- `spring.datasource.*` — aponta para PostgreSQL (driver `org.postgresql`,
  já incluído no `pom.xml`). Ajuste host/porta/banco/usuário/senha para o
  seu ambiente.
- CORS: hoje libera `http://localhost:5173` (endereço padrão do front-end
  Vite). Ajuste em `SecurityConfig.corsConfigurationSource()` quando for
  publicar o front-end em um domínio real.

## Próximos passos sugeridos

- Ligar o front-end (`distribuidora-vitrine`) a esta API no lugar do
  `src/data/products.js` estático.
- Se precisar de mais granularidade de permissões no futuro (ex: um papel
  "vendedor" separado de "admin"), o enum `Role` em
  `model/Role.java` é o ponto de partida.
