
# AI Support Ticket Management Platform

A backend-focused support ticket management platform built with Java 25 and Spring Boot 4.1.1.

The project focuses on secure authentication, ticket management, database persistence, validation, and automated testing.

## Technology Stack

<!-- AUTO:START -->

### Project Structure

- `src/main/java/com/supportai/AiSupportPlatformApplication.java`
- `src/main/java/com/supportai/ai/HybridTicketCategoryClassifier.java`
- `src/main/java/com/supportai/ai/OllamaTicketCategoryClassifier.java`
- `src/main/java/com/supportai/ai/RuleBasedTicketCategoryClassifier.java`
- `src/main/java/com/supportai/ai/TicketCategoryClassifier.java`
- `src/main/java/com/supportai/audit/controller/TicketAuditLogController.java`
- `src/main/java/com/supportai/audit/dto/TicketAuditLogResponse.java`
- `src/main/java/com/supportai/audit/entity/TicketAuditLog.java`
- `src/main/java/com/supportai/audit/repository/TicketAuditLogRepository.java`
- `src/main/java/com/supportai/audit/service/TicketAuditLogService.java`
- `src/main/java/com/supportai/auth/controller/AuthController.java`
- `src/main/java/com/supportai/auth/dto/AuthResponse.java`
- `src/main/java/com/supportai/auth/dto/ChangePasswordRequest.java`
- `src/main/java/com/supportai/auth/dto/LoginRequest.java`
- `src/main/java/com/supportai/auth/dto/LoginResponse.java`
- `src/main/java/com/supportai/auth/dto/RegisterRequest.java`
- `src/main/java/com/supportai/auth/service/AuthService.java`
- `src/main/java/com/supportai/config/PasswordConfig.java`
- `src/main/java/com/supportai/exception/ForbiddenException.java`
- `src/main/java/com/supportai/exception/GlobalExceptionHandler.java`
- `src/main/java/com/supportai/exception/ResourceNotFoundException.java`
- `src/main/java/com/supportai/health/HealthController.java`
- `src/main/java/com/supportai/notification/controller/NotificationController.java`
- `src/main/java/com/supportai/notification/dto/NotificationResponse.java`
- `src/main/java/com/supportai/notification/entity/Notification.java`
- `src/main/java/com/supportai/notification/repository/NotificationRepository.java`
- `src/main/java/com/supportai/notification/service/NotificationService.java`
- `src/main/java/com/supportai/security/SecurityConfig.java`
- `src/main/java/com/supportai/security/jwt/JwtConfig.java`
- `src/main/java/com/supportai/security/jwt/JwtService.java`
- `src/main/java/com/supportai/ticket/comment/controller/TicketCommentController.java`
- `src/main/java/com/supportai/ticket/comment/dto/CommentResponse.java`
- `src/main/java/com/supportai/ticket/comment/dto/CreateCommentRequest.java`
- `src/main/java/com/supportai/ticket/comment/entity/TicketComment.java`
- `src/main/java/com/supportai/ticket/comment/repository/TicketCommentRepository.java`
- `src/main/java/com/supportai/ticket/comment/service/TicketCommentService.java`
- `src/main/java/com/supportai/ticket/controller/TicketController.java`
- `src/main/java/com/supportai/ticket/dto/AssignTicketRequest.java`
- `src/main/java/com/supportai/ticket/dto/CreateTicketRequest.java`
- `src/main/java/com/supportai/ticket/dto/PageResponse.java`
- `src/main/java/com/supportai/ticket/dto/TicketResponse.java`
- `src/main/java/com/supportai/ticket/dto/UpdateTicketRequest.java`
- `src/main/java/com/supportai/ticket/entity/Ticket.java`
- `src/main/java/com/supportai/ticket/entity/TicketCategory.java`
- `src/main/java/com/supportai/ticket/entity/TicketPriority.java`
- `src/main/java/com/supportai/ticket/repository/TicketRepository.java`
- `src/main/java/com/supportai/ticket/repository/TicketSpecifications.java`
- `src/main/java/com/supportai/ticket/service/TicketService.java`
- `src/main/java/com/supportai/user/entity/Role.java`
- `src/main/java/com/supportai/user/entity/User.java`
- `src/main/java/com/supportai/user/repository/UserRepository.java`

### Gradle Dependencies

- `implementation("org.springframework.boot:spring-boot-starter-actuator")`
- `implementation("org.springframework.boot:spring-boot-starter-data-jpa")`
- `implementation("org.springframework.boot:spring-boot-starter-flyway")`
- `implementation("org.springframework.boot:spring-boot-starter-security")`
- `implementation("org.springframework.boot:spring-boot-starter-validation")`
- `implementation("org.springframework.boot:spring-boot-starter-webmvc")`
- `implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")`
- `implementation("org.flywaydb:flyway-database-postgresql")`
- `runtimeOnly("org.postgresql:postgresql")`
- `testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")`
- `testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")`
- `testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")`
- `testImplementation("org.springframework.boot:spring-boot-starter-security-test")`
- `testImplementation("org.springframework.boot:spring-boot-starter-validation-test")`
- `testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")`
- `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`

### Discovered API Endpoints

- `DELETE` `/api/tickets/{id}` | `TicketController`
- `GET` `/api/health` | `HealthController`
- `GET` `/api/notifications/unread-count` | `NotificationController`
- `GET` `/api/notifications` | `NotificationController`
- `GET` `/api/tickets/{id}` | `TicketController`
- `GET` `/api/tickets/{ticketId}/audit-logs` | `TicketAuditLogController`
- `GET` `/api/tickets/{ticketId}/comments` | `TicketCommentController`
- `GET` `/api/tickets` | `TicketController`
- `PATCH` `/api/notifications/{id}/read` | `NotificationController`
- `PATCH` `/api/tickets/{id}/assign` | `TicketController`
- `POST` `/api/auth/change-password` | `AuthController`
- `POST` `/api/auth/login` | `AuthController`
- `POST` `/api/auth/register` | `AuthController`
- `POST` `/api/tickets/{ticketId}/comments` | `TicketCommentController`
- `POST` `/api/tickets` | `TicketController`
- `PUT` `/api/tickets/{id}` | `TicketController`

### Test Results

**Available test report totals:** 109 tests, 0 failures, 0 errors, 0 skipped. These counts do not establish whether the latest test run passed.
<!-- AUTO:END -->

## Project Overview

AI Support Ticket Management Platform is a backend application built with Java and Spring Boot. It provides REST APIs for managing support tickets, authenticating users, enforcing access permissions, and tracking ticket activity.

### Core Features

- User registration and login with JWT-based authentication.
- Role-based authorization for customers, agents, and administrators.
- Ticket creation, retrieval, updates, deletion, and assignment.
- Ticket classification using a locally configured Ollama model.
- Ticket comments and notifications.
- Audit logs for supported ticket events.
- PostgreSQL persistence with Flyway database migrations.
- Request validation and centralized exception handling.
- Automated tests using JUnit and Spring Boot testing support.
- Application health monitoring with Spring Boot Actuator.
- JaCoCo test coverage reports.

## Architecture

The application uses a layered backend architecture:

- **Controller layer:** Exposes REST endpoints and handles HTTP requests.
- **Service layer:** Implements business rules, ticket workflows, and authorization checks.
- **Repository layer:** Uses Spring Data JPA to access persisted data.
- **Entity layer:** Represents database-backed domain objects.
- **DTO layer:** Defines request and response structures.
- **Security layer:** Handles authentication, JWT processing, and access control.
- **Database migration layer:** Uses Flyway to version and apply schema changes.

## Technology Stack

- **Language:** Java 25
- **Framework:** Spring Boot 4.1.1
- **Build tool:** Gradle Kotlin DSL, using the included Gradle wrapper
- **Database:** PostgreSQL 17
- **Persistence:** Spring Data JPA and Hibernate
- **Migrations:** Flyway
- **Authentication:** JWT and Spring Security
- **Local AI:** Ollama with the configured `qwen3:4b` model
- **Testing and coverage:** JUnit, Spring Boot testing support, and JaCoCo
- **Containerization:** Docker

## Prerequisites

Install or configure the following before running the application:

- Java 25.
- Git.
- Docker Desktop with Docker Engine running.
- PowerShell on Windows.
- Ollama and the `qwen3:4b` model if you want to use local AI classification.

Verify the Java installation and Docker availability:

```powershell
java -version
.\gradlew.bat --version
docker --version
docker info
```

Run Gradle commands from the project root. The included Gradle wrapper selects the project's configured Gradle version.

## Run Locally

### 1. Configure the database

The current application configuration expects PostgreSQL at:

- Host: `localhost`
- Port: `5434`
- Database: `ai_support_db`
- Username: `postgres`

If you already have the project's PostgreSQL container running, keep using it. Check its status:

```powershell
docker ps --filter "name=ai-support-postgres"
```

For a new local database, choose a password and start PostgreSQL 17:

```powershell
$env:DB_PASSWORD = Read-Host "Enter a PostgreSQL password"

docker run -d `
    --name ai-support-postgres `
    -e POSTGRES_DB=ai_support_db `
    -e POSTGRES_USER=postgres `
    -e "POSTGRES_PASSWORD=$env:DB_PASSWORD" `
    -p 5434:5432 `
    postgres:17
```

If Docker reports that the container name is already in use, inspect the existing container instead of creating another one.

### 2. Configure application secrets

The application reads the database password from `DB_PASSWORD` and the JWT signing secret from `JWT_SECRET`. Never commit real credentials or signing secrets to Git.

On the original development machine, the local secret loader is located at:

```powershell
"$env:LOCALAPPDATA\AiSupportPlatform\Load-Secrets.ps1"
```

Load your existing local secrets into the current PowerShell session:

```powershell
. "$env:LOCALAPPDATA\AiSupportPlatform\Load-Secrets.ps1"
```

For a fresh clone or another computer, configure your own `DB_PASSWORD` and a strong, private `JWT_SECRET` in the environment before starting the application. The database password must match the password configured for PostgreSQL.

### 3. Start the application

From the project root, with the database running and secrets loaded:

```powershell
.\gradlew.bat bootRun
```

Keep this terminal open while the application is running.

### 4. Check application health

In a second PowerShell terminal, check the health endpoints:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/actuator/health
```

The application should report a healthy status when it has started successfully and its required dependencies are available.

### 5. Run automated tests

From the project root:

```powershell
.\gradlew.bat test
```

Generate a build and coverage reports with:

```powershell
.\gradlew.bat build
```

The JaCoCo HTML report is normally available at:

```text
build/reports/jacoco/test/html/index.html
```

Test results are available under:

```text
build/reports/tests/test/index.html
```

## Update Generated Documentation

Regenerate the automatically maintained README section with:

```powershell
.\gradlew.bat updateReadme
```

The Gradle task generates project structure, dependency information, discovered API endpoints, and test-result summaries between the `AUTO:START` and `AUTO:END` markers.

Edit the manual sections outside those markers. The generated test totals describe the available test reports and should not, by themselves, be treated as proof that the latest test run passed.

## Project Status

Under active development.