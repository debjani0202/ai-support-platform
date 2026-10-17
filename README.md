
# AI Support Ticket Management Platform

A backend-focused support ticket management platform built with Java 25 and Spring Boot 4.1.1.

The project focuses on secure authentication, ticket management, database persistence, validation, and automated testing.

## Technology Stack

<!-- AUTO:START -->

### Project Structure

- `src/main/java/com/supportai/AiSupportPlatformApplication.java`
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

- `DELETE` `/api/tickets/{id}` — `TicketController`
- `GET` `/api/health` — `HealthController`
- `GET` `/api/notifications/unread-count` — `NotificationController`
- `GET` `/api/notifications` — `NotificationController`
- `GET` `/api/tickets/{id}` — `TicketController`
- `GET` `/api/tickets` — `TicketController`
- `GET` `` — `TicketAuditLogController`
- `GET` `` — `TicketCommentController`
- `PATCH` `/api/notifications/{id}/read` — `NotificationController`
- `PATCH` `/api/tickets/{id}/assign` — `TicketController`
- `POST` `/api/auth/change-password` — `AuthController`
- `POST` `/api/auth/login` — `AuthController`
- `POST` `/api/auth/register` — `AuthController`
- `POST` `/api/tickets` — `TicketController`
- `POST` `` — `TicketCommentController`
- `PUT` `/api/tickets/{id}` — `TicketController`

### Test Results

**Latest available report counts:** 28 tests, 0 failures, 0 errors, 0 skipped. These counts do not establish whether the latest test run passed.
<!-- AUTO:END -->

## Project Overview

This platform is designed to manage support tickets through a structured backend architecture.

### Core Features

- REST APIs for support ticket management
- User registration and login
- JWT-based authentication and authorization
- Role-based access control
- PostgreSQL database integration
- Database versioning with Flyway migrations
- Input validation
- Centralized exception handling
- Automated testing with JUnit and Spring Boot testing support
- Application health and monitoring through Spring Boot Actuator

## Architecture

The application follows a layered backend structure:

- **Controller layer:** Handles incoming HTTP requests.
- **Service layer:** Contains application and business logic.
- **Repository layer:** Handles database operations.
- **Entity layer:** Represents persistent database models.
- **DTO layer:** Defines request and response data structures.
- **Security layer:** Configures authentication and authorization.
- **Database migration layer:** Manages schema changes using Flyway.

## Prerequisites

- Java 25
- Docker
- PostgreSQL
- Git

## Run Locally

Start your PostgreSQL container and configure your database credentials in the application's configuration.

Then run the application from the project root:

```powershell
.\gradlew.bat bootRun
```

Run automated tests:

```powershell
.\gradlew.bat test
```

## Update Generated Documentation

Regenerate the project structure and dependency list:

```powershell
.\gradlew.bat updateReadme
```

The generated documentation is maintained between the `AUTO:START` and `AUTO:END` markers.

## Project Status

Under active development.
