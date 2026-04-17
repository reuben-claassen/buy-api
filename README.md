# 🛒 Buy API

Production-ready Spring Boot e-commerce API demonstrating:

* JWT-based authentication with Spring Security
* Transactional order processing with stock consistency
* PostgreSQL integration with environment-driven configuration
* Dockerized deployment across cloud platforms
* CI/CD automation with GitHub Actions

---

## 🚀 Key Capabilities

* Implemented stateless authentication using JWT and Spring Security
* Designed transactional order workflows with stock validation and rollback safety
* Built layered architecture (Controller → Service → Repository)
* Integrated PostgreSQL using environment-based configuration
* Automated testing and deployment via CI/CD pipelines
* Developed REST APIs with pagination, filtering, and role-based access control

---

## 🛠️ Tech Stack

* **Java 21**
* **Spring Boot 4.0.5** — Web, Security, Data JPA, Validation, Mail
* **PostgreSQL**
* **Hibernate / JPA**
* **JWT (jjwt 0.12.6)**
* **SpringDoc OpenAPI (Swagger)**
* **Docker** (multi-stage build)

---

## 🧱 Architecture

* Layered architecture: Controller → Service → Repository
* DTO-based request/response isolation
* Stateless JWT authentication via filter chain
* Centralized exception handling using ProblemDetail (RFC 7807)
* Transactional service layer for consistency (orders, stock updates)

---

## 📦 Core Features

### 🔐 Authentication

* JWT login & registration
* BCrypt password hashing
* Role-based access (CUSTOMER / ADMIN)

### 🛒 E-commerce Core

* Product CRUD with soft delete
* Category hierarchy (parent → children)
* Cart management with stock validation
* Order placement with transactional consistency
* Order cancellation with stock restoration

### ⚙️ Platform Features

* Swagger UI (`/swagger-ui/index.html`)
* Async email notifications
* File uploads (multipart)
* Pagination & filtering

---

## ☁️ Deployment

* Containerized using Docker (multi-stage build, non-root user)
* Compatible with cloud platforms (Render, AWS, Railway, Fly.io, etc.)
* Supports managed PostgreSQL providers (Supabase, Neon, RDS, etc.)
* Environment-driven configuration for portability
* CI/CD with GitHub Actions:

  * Automated tests on every push
  * Deployment via webhook or platform integration
* Health checks and keep-alive strategies supported

> Example deployment: Render (application) + Supabase (PostgreSQL)

---

## ⚙️ Environment Configuration

The application is fully environment-driven.

Example variables:

* `DB_HOST`, `DB_PORT`, `DB_NAME`
* `DB_USERNAME`, `DB_PASSWORD`
* `JWT_SECRET`
* `MAIL_HOST`, `MAIL_USERNAME`, etc.

See `.env.example` for full configuration.

---

## 🧪 Testing

* Unit tests (Mockito)
* Controller tests (`@WebMvcTest`)
* Repository tests (`@DataJpaTest`)
* H2 in-memory database for CI

Run all tests:

```bash
./mvnw test
```

---

## 📸 API Preview

Swagger UI available at:

```
/swagger-ui/index.html
```

---

## 💡 Design Decisions

**Separate DB env vars instead of a single JDBC URL**
Avoids shell escaping issues with `&` in JDBC query parameters by assembling the URL in `application.properties`.

**Soft delete on products**
Preserves order history while allowing products to be hidden from active listings.

**Price snapshots in OrderItem**
Ensures historical accuracy even if product prices change later.

**Async email handling**
Prevents SMTP delays from impacting API response times.

**H2 for tests, Postgres for production**
Enables fast, isolated CI runs without external dependencies.

**ProblemDetail error responses**
Provides consistent, machine-readable API errors (RFC 7807).
