# 🛒 Buy API

A production-ready Spring Boot e-commerce REST API with JWT authentication,
role-based access control, transactional order processing, and Supabase integration.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.5 (Web, Security, Data JPA, Validation, Mail) |
| Database | PostgreSQL (Supabase) |
| ORM | Hibernate / JPA |
| Auth | JWT via jjwt 0.12.6 |
| Storage | Supabase Storage (product images) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Mockito, H2 |
| Deployment | Docker (multi-stage build) |

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Maven (or use the included `./mvnw` wrapper)
- A PostgreSQL database (the example config uses [Supabase](https://supabase.com))
- A Supabase project for image storage

### Clone and configure

```bash
git clone https://github.com/your-username/buy-api.git
cd buy-api
cp .env.example .env
```

Open `.env` and fill in your values. Required variables are:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` — generate with: `openssl rand -base64 64`
- `SUPABASE_URL`, `SUPABASE_KEY`

### Run with Maven

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.  
Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.

### Run with Docker

```bash
docker build -t buy-api .
docker run -p 8080:8080 --env-file .env buy-api
```

### Run tests

```bash
./mvnw test
```

Tests use an H2 in-memory database — no external database required.

---

## 📦 Features

### 🔐 Authentication & Roles

JWT-based stateless authentication with three roles:

| Role | Permissions |
|---|---|
| `CUSTOMER` | Browse products and categories, manage own cart, place and cancel own orders |
| `SELLER` | All customer permissions + create/update products and categories, update order statuses |
| `ADMIN` | Full access including user management, product deletion, and actuator endpoints |

### 🛒 E-commerce

- Product CRUD with image upload (Supabase Storage) and soft delete
- Category hierarchy with parent → child relationships
- Cart management with real-time stock validation
- Order placement with transactional stock deduction
- Order cancellation with stock restoration
- Order status management (PENDING → CONFIRMED → SHIPPED → DELIVERED / CANCELLED)

### ⚙️ Platform

- Swagger UI with bearer token support
- Pagination and keyword/category filtering on product listings
- Async email notifications (order confirmation)
- Centralized error responses using ProblemDetail (RFC 7807)
- Spring Boot Actuator (`/actuator/health`, `/actuator/info`, `/actuator/metrics`)

---

## 🧱 Architecture

Layered architecture: **Controller → Service → Repository**

- DTOs for all request/response boundaries
- Stateless JWT authentication via a custom `OncePerRequestFilter`
- Price snapshots stored on `OrderItem` for historical accuracy
- Soft delete on `Product` to preserve order history
- Separate DB env vars (rather than a single JDBC URL string) to avoid `&` encoding issues

---

## ☁️ Deployment

The application is fully environment-driven and deployable on any container platform.

Example stack: **Render** (application) + **Supabase** (PostgreSQL + Storage)

See `.env.example` for all supported environment variables.

---

## 🧪 Testing

| Test type | Scope |
|---|---|
| `@WebMvcTest` | Controller behaviour, request validation, method-level security |
| `SecurityIntegrationTest` | URL-level rules across all roles via the real `SecurityFilterChain` |
| `@DataJpaTest` | Custom repository queries against H2 |
| Unit tests | Service logic, JWT utilities, exception handlers |