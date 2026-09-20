# EventHub — High-Concurrency Ticket Booking & Reservation Platform

EventHub is a production-oriented, full-stack Java ticket reservation platform engineered to address real-world concurrency challenges, race condition prevention, distributed temporary seat holds, and event-driven asynchronous processing.

---

## 🏛️ System Architecture

```text
[ Browser / Frontend Client (HTML5 / CSS3 / Vanilla JS) ]
              │   ▲
  HTTP / REST │   │ Polling (Every 3s for live seat status)
              ▼   │
[ Spring Boot 3.3.x Application (Java 21 Virtual Threads Ready) ]
   ├── Security Filter & Stateless JWT Authentication
   ├── Event & Seat Inventory Service
   ├── Distributed Seat Lock Engine (Redis with 10-minute TTL)
   ├── Pessimistic DB Write Locking (MySQL with ACID transactions)
   ├── Simulated Idempotent Payment Processor
   └── Asynchronous Event Publisher (Apache Kafka)
              │
              ├──► [ Redis (Port 6379) ]  (In-Memory Seat Locks with TTL)
              ├──► [ MySQL 8.0 (Port 3306) ]  (Persistent Storage: Users, Bookings, Seats)
              └──► [ Apache Kafka (Port 9092) ] (Topic: 'booking-events')
                        │
                        ▼
                   [ BookingKafkaConsumer ] (Decoupled ticket issuance & email dispatch)
```

---

## 🛠️ Technology Stack

| Layer | Component | Version / Library |
| :--- | :--- | :--- |
| **Language** | Java | 21 |
| **Framework** | Spring Boot | 3.3.3 |
| **Persistence** | Spring Data JPA / Hibernate | MySQL 8.0 |
| **Concurrency & Lock** | Redis (Spring Data Redis) | Redis 7 |
| **Event Streaming** | Apache Kafka | 3.7.0 (KRaft Mode) |
| **Security** | Spring Security | JJWT (io.jsonwebtoken 0.12.5) |
| **Build Tool** | Apache Maven | 3.9+ |
| **Frontend** | Vanilla Web | HTML5, CSS3, JavaScript (Fetch API, Polling) |
| **Containerization**| Docker & Docker Compose | Multi-container setup |

---

## 🚀 Quickstart Guide

### Prerequisites
- **Java 21** JDK installed
- **Apache Maven 3.8+**
- **Docker & Docker Compose**

### Step 1: Start Infrastructure Containers
Start MySQL, Redis, and Apache Kafka in the background:
```bash
docker compose up -d
```
Verify the containers are healthy:
```bash
docker compose ps
```

### Step 2: Build & Run the Spring Boot Application
```bash
mvn clean spring-boot:run
```
Once started, the backend automatically seeds:
- 2 Users:
  - `user@eventhub.com` / `user123` (Customer)
  - `admin@eventhub.com` / `admin123` (Admin)
- 2 Venues (Hyderabad & Bengaluru)
- 2 Events with 48 seats each (Rows A to F, Seats 1 to 8)

### Step 3: Access the Application
Open your web browser and navigate to:
```text
http://localhost:8080
```

---

## 🔑 Key Engineering Patterns & Concurrency Design

### 1. Two-Tier Seat Locking (Redis + MySQL)
1. **Redis In-Memory Temporary Lock (Pre-booking / 10-Minute Hold)**:
   - When a user selects seats and clicks **Reserve Seats**, an atomic `SETNX` operation runs in Redis with a 10-minute TTL:
     `key: lock:event:{eventId}:seat:{seatId}` -> `value: {userEmail}`.
   - If another user attempts to lock the same seat, Redis rejects the request, preventing checkout collisions.
2. **Pessimistic Database Write Lock (Checkout Transaction)**:
   - During `/api/bookings/checkout`, Spring Data JPA acquires a `PESSIMISTIC_WRITE` database lock (`SELECT ... FOR UPDATE`) on the target seats in MySQL to ensure atomicity.
3. **Permanent Transition & Lock Release**:
   - Once payment succeeds, the seat status is persisted as `BOOKED` in MySQL, and the temporary Redis key is safely deleted.

### 2. Idempotent Payment Processing
- Each checkout transaction generates a unique `idempotencyKey` on the client.
- The `PaymentService` verifies if a transaction with that key has already succeeded. Repeated submissions (such as network retries or double-clicks) return the cached result without charging or booking twice.

### 3. Asynchronous Decoupling via Apache Kafka
- When payment is confirmed, the critical user-facing path immediately finishes.
- A `BookingKafkaMessage` is published to the `booking-events` topic.
- A background worker (`BookingKafkaConsumer`) receives the message to process ticket generation, invoice creation, and email dispatch without blocking the customer.

### 4. Real-Time Seat Availability via HTTP Polling
- Clients polling `/api/events/{id}/seats` every 3 seconds receive an aggregated status computed dynamically from both MySQL (`status`) and active Redis locks (`LOCKED` + ownership verification).

---

## 📡 REST API Specifications

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Create customer account |
| `POST` | `/api/auth/login` | Public | Authenticate and obtain JWT token |
| `GET` | `/api/events` | Public | List all upcoming events |
| `GET` | `/api/events/{id}` | Public | Get event details |
| `GET` | `/api/events/{id}/seats`| Public / Auth | List seats with live lock status |
| `POST` | `/api/bookings/lock-seats`| Authenticated | Acquire 10-min Redis seat hold |
| `POST` | `/api/bookings/checkout` | Authenticated | Create pending booking with DB locks |
| `POST` | `/api/payments/process` | Authenticated | Process idempotent payment simulation |
| `GET` | `/api/bookings/my-bookings`| Authenticated | View current user's booking history |
