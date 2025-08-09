[![Java CI with Maven](https://github.com/rajkundalia/hotel-booking-saga-orchestration/actions/workflows/maven.yml/badge.svg)](https://github.com/rajkundalia/hotel-booking-saga-orchestration/actions/workflows/maven.yml)

# Spring Boot Saga Pattern - Orchestration (Multi-Module Educational Project)

A comprehensive multi-module Spring Boot project demonstrating the **Saga Orchestration Pattern** for a hotel booking system using centralized coordination.

## Note: Debug the test cases to understand the flow better rather than starting the application.

## Learning Objectives

This project demonstrates:
- **Saga Orchestration Pattern** - Centralized coordination of distributed transactions
- **Local Compensating Transactions** - Rollback mechanisms for distributed failures
- **Idempotency and Retry Safety** - Safe command re-execution
- **Timeout Detection and Handling** - Long-running transaction management
- **Concurrency and Consistency** - Handling dirty reads, lost updates, non-repeatable reads
- **Testing** - Integration tests for all scenarios

## Architecture Overview

The system implements the **Saga Orchestration Pattern** where the Booking Service acts as the central orchestrator, coordinating the saga steps:

```
[Booking Service] ──┐
    (Orchestrator)  │
                    ├── [Hotel Service] (Room Reservation)
                    └── [Payment Service] (Payment Authorization)
```

### Saga Flow
1. **Reserve Room** → Hotel Service reserves a room
2. **Authorize Payment** → Payment Service authorizes payment
3. **Complete Booking** → Success path
4. **Compensation** → Rollback on failures

## Project Structure

```
saga-orchestration-hotel-booking/
├── booking-service/         # Orchestrator logic, saga flow management
├── hotel-service/           # Room availability, reservation, compensation
├── payment-service/         # Payment authorization, cancellation
├── common/                  # Shared models (DTOs, enums, commands, utils)
├── integration-tests/       # Comprehensive saga tests
└── README.md
```

## Technology Stack

| Component | Technology |
|-----------|------------|
| **Language** | Java 17+ |
| **Framework** | Spring Boot 3.x |
| **Communication** | Spring Cloud OpenFeign |
| **Persistence** | H2 Database (in-memory) |
| **Concurrency Control** | Database constraints + Optimistic locking |
| **Build Tool** | Maven (multi-module) |
| **Testing** | JUnit 5, Mockito, Spring Test, WireMock |

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+

### Build and Run

1. **Clone and build the project:**
```bash
git clone <repository-url>
cd hotel-booking-saga-orchestration
mvn clean install
```

2. **Start the services in separate terminals:**

```bash
# Terminal 1 - Hotel Service
cd hotel-service
mvn spring-boot:run

# Terminal 2 - Payment Service  
cd payment-service
mvn spring-boot:run

# Terminal 3 - Booking Service (Orchestrator)
cd booking-service
mvn spring-boot:run
```

3. **Services will be available at:**
- Booking Service: http://localhost:8080
- Hotel Service: http://localhost:8081
- Payment Service: http://localhost:8082

### Test the System

**Create a booking:**
```bash
curl -X POST http://localhost:8080/api/booking \
  -H "Content-Type: application/json" \
  -d '{
    "hotelId": 1,
    "roomType": "STANDARD",
    "checkIn": "2025-08-01",
    "checkOut": "2025-08-03",
    "guestName": "John Doe",
    "roomPrice": 199.99,
    "cardNumber": "4111111111111111",
    "cardHolderName": "John Doe",
    "expiryMonth": "12",
    "expiryYear": "2025",
    "cvv": "123"
  }'
```

**Check booking status:**
```bash
curl http://localhost:8080/api/booking/{sagaId}
```

## Saga Implementation Details

### State Machine
The saga uses a state machine with the following states:

```
STARTED → ROOM_RESERVED → PAYMENT_AUTHORIZED → BOOKING_COMPLETED
    ↓           ↓               ↓
CANCELLED ← COMPENSATING ← PAYMENT_FAILED
```

### Idempotency Handling
Each command includes an `idempotencyKey` to ensure safe re-execution:

```java
@Transactional
public CommandResult<ReservationDto> reserveRoom(ReserveRoomCommand command) {
    // Check if already processed
    if (idempotencyStore.hasProcessed(command.getIdempotencyKey())) {
        return cachedResult;
    }
    // Process command and store result
}
```

### Compensation Logic
When failures occur, the orchestrator executes compensating transactions:

- **Payment fails** → Cancel payment + Release room
- **Room reservation fails** → End saga with failure
- **Timeout occurs** → Retry or compensate based on retry count

### Timeout Management
Sagas have configurable timeouts with automatic retry/compensation:

```java
@Scheduled(fixedDelay = 30000)
public void handleTimeouts() {
    List<SagaInstance> expiredSagas = findExpiredSagas();
    expiredSagas.forEach(saga -> retrySaga(saga.getSagaId()));
}
```

## Testing Strategy

### Test Categories

1. **Happy Path Tests** - Successful booking flow
2. **Compensation Tests** - Failure scenarios and rollback
3. **Idempotency Tests** - Command re-execution safety
4. **Timeout Tests** - Long-running transaction handling
5. **Concurrency Tests** - Double booking prevention, optimistic locking

### Running Tests

```bash
# Run all tests
mvn test

# Run integration tests only
cd integration-tests
mvn test
```

### Key Test Scenarios

**Happy Path:**
```java
@Test
void shouldCompleteBookingSagaSuccessfully() {
    BookingResponse response = bookingService.createBooking(request);
    
    await().untilAsserted(() -> {
        SagaInstance saga = sagaRepository.findById(response.getSagaId()).get();
        assertEquals(SagaState.BOOKING_COMPLETED, saga.getState());
    });
}
```

**Compensation:**
```java
@Test
@TestPropertySource(properties = {"payment.simulation.failure-rate=1.0"})
void shouldCompensateWhenPaymentFails() {
    // Payment service configured to always fail
    BookingResponse response = bookingService.createBooking(request);
    
    await().untilAsserted(() -> {
        SagaInstance saga = sagaRepository.findById(response.getSagaId()).get();
        assertEquals(SagaState.BOOKING_CANCELLED, saga.getState());
    });
}
```

## Data Consistency & Concurrency Control

### Double Booking Prevention
The system prevents double bookings using **database constraints** with a room availability table:

```java
@Entity
@Table(name = "room_availability", 
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"hotel_id", "room_type", "date"},
           name = "uk_room_date"
       ))
public class RoomAvailability {
    private Long hotelId;
    private String roomType;
    private LocalDate date;
    private String reservationId;
}
```

**How it works:**
1. Create reservation record
2. Create availability records for each date (one per day)
3. Database constraint prevents duplicate bookings automatically
4. Handle `DataIntegrityViolationException` → return "ROOM_NOT_AVAILABLE"

```java
public class CommandResult<ReservationDto> reserveRoom(ReserveRoomCommand command) {
    try {
        return attemptReservation(command);
    } catch (DataIntegrityViolationException e) {
        return CommandResult.failure("Room not available", "ROOM_NOT_AVAILABLE");
    }
}
```

### Lost Updates Prevention
Implements optimistic locking with `@Version`:

```java
@Entity
public class Reservation {
    @Version
    private Long version;
    // Automatic version increment on updates
    // Throws OptimisticLockingFailureException on conflicts
}
```

### Idempotency Protection
Each command includes an `idempotencyKey` to ensure safe re-execution:

```java
@Transactional
public CommandResult<ReservationDto> reserveRoom(ReserveRoomCommand command) {
    // Check if already processed
    Optional<IdempotencyRecord> existing = 
        idempotencyRepository.findById(command.getIdempotencyKey());
    
    if (existing.isPresent()) {
        return cachedResult; // Return previous result
    }
    // Process command and store result
}
```

### Concurrency Test Scenarios

**Double Booking Prevention:**
```java
@Test
void twoConcurrentRequestsForSameRoom_OnlyOneSucceeds() {
    // Both threads try to book same room/dates
    CompletableFuture<CommandResult> future1 = 
        CompletableFuture.supplyAsync(() -> hotelService.reserveRoom(command1));
    CompletableFuture<CommandResult> future2 = 
        CompletableFuture.supplyAsync(() -> hotelService.reserveRoom(command2));
    
    // Only one should succeed
    assertTrue((result1.isSuccess() && !result2.isSuccess()) ||
               (!result1.isSuccess() && result2.isSuccess()));
}
```

**Optimistic Locking:**
```java
@Test
void conflictingSaves_ThrowsOptimisticLockingFailureException() {
    Reservation res1 = repository.findById(id).get();
    Reservation res2 = repository.findById(id).get(); // Same version
    
    res1.setStatus(CONFIRMED);
    repository.save(res1); // Version incremented
    
    res2.setStatus(CANCELLED); // Stale version
    assertThrows(OptimisticLockingFailureException.class, 
                () -> repository.save(res2));
}
```

### Why This Approach Works
- **Race Condition Safe** - Database constraints are atomic
- **Production Ready** - Used by major booking platforms
- **Simple** - No complex locking logic required
- **Scalable** - Works with any ACID database
- **Reliable** - Cannot have double bookings by design

## Configuration

### Simulation Parameters
Control failure rates and delays for testing:

```yaml
hotel:
  simulation:
    delay: 1000        # milliseconds
    failure-rate: 0.1  # 10% failure rate

payment:
  simulation:
    delay: 500         # milliseconds  
    failure-rate: 0.05 # 5% failure rate
```

### Saga Timeouts
Configure saga timeout and retry behavior:

```java
public class SagaInstance {
    private int maxRetries = 3;
    private LocalDateTime expiresAt; // 30 minutes default
}
```

## Monitoring and Observability

### Correlation IDs
All requests are traced with correlation IDs:

```java
CorrelationIdUtils.generateAndSetCorrelationId();
// All logs will include correlationId in MDC
```

### Health Checks
Services expose health endpoints:
- http://localhost:8080/actuator/health
- http://localhost:8081/actuator/health
- http://localhost:8082/actuator/health

### Database Console
H2 console available for debugging:
- http://localhost:8080/h2-console
- http://localhost:8081/h2-console
- http://localhost:8082/h2-console

### Database Tables
Key tables for understanding the system:
- `reservations` - Hotel room reservations
- `room_availability` - Per-date availability with unique constraints
- `idempotency_records` - Command deduplication
- `saga_instances` - Orchestration state tracking

## Learning Paths

### Beginner
1. Run the happy path scenario
2. Understand the saga state machine
3. Explore the compensation logic

### Intermediate
1. Simulate failures and observe compensation
2. Test idempotency scenarios
3. Experiment with timeout handling

### Advanced
1. Analyze concurrency test scenarios
2. Implement additional saga patterns
3. Add monitoring and observability

## References

- [Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Cloud OpenFeign](https://spring.io/projects/spring-cloud-openfeign)
- [Distributed Systems Patterns](https://martinfowler.com/articles/patterns-of-distributed-systems/)

---
## Note: If there is a mistake that you notice, please drop me a message or raise a PR.
