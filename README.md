# Canteen Backend (Spring Boot)

High-performance **Java 21 / Spring Boot 3** REST API for the Smart Campus Canteen Ordering & Pre-Booking System.

## Tech Stack
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Hibernate, Validation)
- **Database**: PostgreSQL (Primary) / MySQL (Compatible)
- **Security**: JWT & HMAC SHA-256 Auth, BCrypt password hashing, Admin access control
- **Build Tool**: Maven

## Features
- **Student Auth**: Registration, Login, Token generation & validation
- **Live Menu**: Item availability, dynamic stock management
- **Order Placement**: Transactional inventory locking (`PESSIMISTIC_WRITE`), dynamic wait estimation
- **Live Tracking**: Order tracking by token, student order history
- **Admin Dashboard**: Orders overview, status lifecycle update (`Placed` -> `Preparing` -> `Ready` -> `Collected`), menu item management & pricing
- **Sales Analytics**: Today's orders & revenue, total orders & revenue, top-selling items

## Render Deployment
1. Connect Render Web Service to `https://github.com/shiva415/canteen-backend`.
2. Settings:
   - **Environment**: Docker (or Native)
   - **Root Directory**: *(Leave empty / blank)*
3. Environment Variables:
   - `DATABASE_URL`: `postgresql://project_ubvw_user:mJVmvnu6HhybpOP5qKuWhEAoVW9N3k0i@dpg-db85c4gu01pc73ejgr30-a/project_ubvw`
   - `ADMIN_PASSWORD`: `admin123`
   - `AUTH_SECRET`: `your-random-secret-key`

## Local Development
Requires Java 21+:
```bash
./mvnw clean spring-boot:run
```
Or with Maven:
```bash
mvn clean spring-boot:run
```
The API starts on port `5000`:
- Root check: `http://localhost:5000/`
- Health check: `http://localhost:5000/health`
- Menu: `http://localhost:5000/api/menu`
