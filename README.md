# Canteen Backend API

Express.js REST API for Smart Campus Canteen Ordering & Pre-Booking System with PostgreSQL & MySQL support.

## Features
- User authentication (registration, login with hashed passwords via `scrypt`)
- Menu management & live stock updates
- Order placement with real-time wait estimation
- Transactional order processing with inventory lock (`FOR UPDATE`)
- Admin dashboard with order lifecycle management & sales analytics
- Built-in PostgreSQL support with automated table creation & sequence management

## Tech Stack
- **Runtime**: Node.js
- **Framework**: Express.js
- **Database**: PostgreSQL (via `pg`) / MySQL (via `mysql2`)

## Deployment on Render
1. Create a **Web Service** connected to `https://github.com/shiva415/canteen-backend`.
2. Configure Settings:
   - **Environment**: Node
   - **Root Directory**: *(Leave empty / blank)*
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
3. Environment Variables:
   - `DATABASE_URL`: `postgresql://project_ubvw_user:mJVmvnu6HhybpOP5qKuWhEAoVW9N3k0i@dpg-db85c4gu01pc73ejgr30-a/project_ubvw`
   - `ADMIN_PASSWORD`: `admin123`
   - `AUTH_SECRET`: `your-random-secret-key`
   - `NODE_ENV`: `production`

## Local Development
```bash
npm install
npm start
```
API runs on `http://localhost:5000`.
Health check: `http://localhost:5000/health`.
