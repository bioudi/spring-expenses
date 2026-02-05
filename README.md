# Expense Tracker API

A Spring Boot REST API for tracking expenses with webhook support and Docker deployment.

## Features

- **Webhook endpoint** for receiving expense data
- **Query endpoint** with date range and category filtering
- **Summary endpoint** with aggregated statistics
- **API key authentication** for webhook security
- **Docker Compose** setup for easy deployment

## Tech Stack

- Java 21
- Spring Boot 3.2
- Spring Data JPA
- PostgreSQL 15
- Maven
- Docker & Docker Compose

## Project Structure

```
src/main/java/com/expensetracker/
├── ExpenseTrackerApplication.java
├── config/
│   ├── ApiKeyFilter.java
│   └── ExpenseCategory.java
├── controller/
│   ├── ExpenseController.java
│   └── WebhookController.java
├── dto/
│   ├── ErrorResponse.java
│   ├── ExpenseRequest.java
│   ├── ExpenseResponse.java
│   └── ExpenseSummary.java
├── entity/
│   └── Expense.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── InvalidCategoryException.java
├── repository/
│   └── ExpenseRepository.java
└── service/
    └── ExpenseService.java
```

## Running the Application

### Option 1: Local Development (PostgreSQL in Docker, App locally)

1. Start PostgreSQL only:
```bash
docker compose up postgres -d
```

2. Run the Spring Boot application:
```bash
./mvnw spring-boot:run
```

The app will connect to `localhost:5432` by default.

### Option 2: Full Docker Deployment

Run both PostgreSQL and the application in Docker:

```bash
docker compose up --build
```

This will:
- Build the Spring Boot application using the multi-stage Dockerfile
- Start PostgreSQL with health checks
- Start the application once PostgreSQL is healthy

### Stopping Services

```bash
# Stop all services
docker compose down

# Stop and remove volumes (clears database data)
docker compose down -v
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/expense_tracker` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `expenseuser` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `expensepass` | Database password |
| `API_KEY` | `dev-api-key-change-in-production` | API key for webhook authentication |

### Changing the API Key

For Docker deployment, edit `docker-compose.yml`:
```yaml
environment:
  API_KEY: your-secure-api-key-here
```

For local development, set the environment variable or edit `application.properties`.

## API Endpoints

### Create Expense (Webhook)

```bash
POST /api/webhook/expense
Header: X-API-Key: your-api-key
Content-Type: application/json

{
  "amount": 25.99,
  "category": "Food & Drinks",
  "merchant": "Starbucks",
  "cardName": "Chase Freedom",
  "timestamp": "2024-01-15T10:30:00",
  "notes": "Morning coffee"
}
```

**Valid Categories:**
- Food & Drinks
- Shopping
- Travel
- Services
- Entertainment
- Health
- Transportation

### Get Expenses

```bash
# Get all expenses
GET /api/expenses

# Filter by date range
GET /api/expenses?startDate=2024-01-01&endDate=2024-01-31

# Filter by category
GET /api/expenses?category=Food%20%26%20Drinks

# Combine filters
GET /api/expenses?startDate=2024-01-01&endDate=2024-01-31&category=Shopping
```

### Get Summary

```bash
GET /api/expenses/summary
```

Response:
```json
{
  "totalSpent": 1250.50,
  "transactionCount": 15,
  "categoryBreakdown": {
    "Food & Drinks": {
      "total": 450.25,
      "count": 8,
      "percentage": 36.01
    },
    "Shopping": {
      "total": 800.25,
      "count": 7,
      "percentage": 63.99
    }
  }
}
```

## Exposing Webhook with ngrok

To receive webhooks from external services:

1. Install ngrok: https://ngrok.com/download

2. Start the application (local or Docker)

3. Expose port 8080:
```bash
ngrok http 8080
```

4. Use the ngrok URL for your webhook:
```
https://abc123.ngrok.io/api/webhook/expense
```

5. Configure your external service to send POST requests to this URL with:
   - Header: `X-API-Key: your-api-key`
   - Content-Type: `application/json`

## Example Requests

### Create an expense:
```bash
curl -X POST http://localhost:8080/api/webhook/expense \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-api-key-change-in-production" \
  -d '{
    "amount": 42.50,
    "category": "Food & Drinks",
    "merchant": "Whole Foods",
    "cardName": "Amex Gold",
    "notes": "Groceries"
  }'
```

### Get all expenses:
```bash
curl http://localhost:8080/api/expenses
```

### Get summary:
```bash
curl http://localhost:8080/api/expenses/summary
```

## Error Responses

### Validation Error (400)
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid request data",
  "path": "/api/webhook/expense",
  "fieldErrors": [
    {
      "field": "amount",
      "message": "Amount must be positive"
    }
  ]
}
```

### Invalid Category (400)
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 400,
  "error": "Invalid Category",
  "message": "Invalid category: InvalidCat. Valid categories are: Food & Drinks, Shopping, Travel, Services, Entertainment, Health, Transportation",
  "path": "/api/webhook/expense"
}
```

### Missing API Key (401)
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing API key"
}
```

### Invalid API Key (403)
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Invalid API key"
}
```
