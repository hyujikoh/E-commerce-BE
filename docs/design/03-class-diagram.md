# 03. 클래스 다이어그램 (도메인 객체 설계)

> 기준 문서: `01-requirements.md`, `02-sequence-diagrams.md`
> 표현: Mermaid `classDiagram`
> 다이어그램 작성 기준: `.claude/skills/requirements-analysis/SKILL.md` § 5️⃣

---

## 0. 설계 원칙

- **Aggregate 경계**: 트랜잭션 일관성이 필요한 단위를 aggregate로 묶고, aggregate 간에는 **ID 참조**만 한다.
- **불변식 보호 위치**: 상태 머신·도메인 규칙은 모델(`Reservation`, `DailyRoomInventory`)에 응집. 서비스는 트랜잭션 경계와 외부 협력만 담당.
- **Value Object**: 의미 있는 단순값(`DateRange`, `Money`)은 VO로 분리해 검증·연산 응집.
- **Service 역할 분리**: 검색 / 예약 / 재고 / 만료 정리 / 결제(외부)를 별도 서비스로 둔다 — 책임 경계가 시퀀스와 1:1로 매칭.

## 1. 클래스 다이어그램

### 이 다이어그램이 필요한 이유

- "어느 클래스가 무슨 규칙을 보호하는가"를 한눈에 확인한다. 시퀀스에서 흩어진 호출이 어디로 모이는지를 보고 응집도를 검증한다.
- aggregate 경계가 합의(B1, B5)와 일치하는지 — 특히 `Reservation`이 `DailyRoomInventory`를 직접 만지지 않는다는 점.

### 다이어그램

```mermaid
classDiagram
  direction LR

  %% ─── 도메인 객체 ───
  class Property {
    +Long id
    +Long hostId
    +String name
    +String city
    +String address
    +String description
  }

  class RoomType {
    +Long id
    +Long propertyId
    +String name
    +int maxOccupancy
    +String bedType
  }

  class DailyRoomInventory {
    +Long roomTypeId
    +LocalDate date
    +int remaining
    +decrement() void
    +increment() void
  }

  class DailyRoomRate {
    +Long roomTypeId
    +LocalDate date
    +Money amount
  }

  class Reservation {
    +Long id
    +Long guestId
    +Long roomTypeId
    +DateRange stayPeriod
    +ReservationStatus status
    +Money totalAmount
    +List~NightlyAmount~ nightlyBreakdown
    +Instant expiresAt
    +CancelReason cancelReason
    +Instant canceledAt
    +confirm() void
    +cancel(CancelReason) void
    +expire() void
    +checkIn() void
    +checkOut() void
    +markNoShow() void
  }

  class Wishlist {
    +Long id
    +Long guestId
    +Long propertyId
    +Instant addedAt
  }

  %% ─── Value Objects ───
  class DateRange {
    <<value object>>
    +LocalDate checkIn
    +LocalDate checkOut
    +int nights()
    +Stream~LocalDate~ datesExclusive()
  }

  class Money {
    <<value object>>
    +long amount
    +String currency
    +plus(Money) Money
  }

  class NightlyAmount {
    <<value object>>
    +LocalDate date
    +Money amount
  }

  %% ─── Enum ───
  class ReservationStatus {
    <<enumeration>>
    PENDING
    CONFIRMED
    CHECKED_IN
    CHECKED_OUT
    CANCELLED
    NO_SHOW
  }

  class CancelReason {
    <<enumeration>>
    USER_REQUEST
    EXPIRED
    PAYMENT_FAILED
  }

  %% ─── Service ───
  class ReservationService {
    <<service>>
    +create(guestId, roomTypeId, stayPeriod, guestCount) Reservation
    +confirm(reservationId) void
    +cancel(reservationId, guestId) void
    +expire(reservationId) void
  }

  class InventoryService {
    <<service>>
    +reserve(roomTypeId, dates) void
    +release(roomTypeId, dates) void
  }

  class PropertySearchService {
    <<service>>
    +search(SearchCriteria) List~SearchResult~
  }

  class ExpirationScheduler {
    <<service>>
    +sweepExpired() void
  }

  class PaymentService {
    <<external>>
    +chargeAsync(reservationId, totalAmount) PaymentSession
    +on webhook → confirm/fail
  }

  class DomainEventBus {
    <<infrastructure>>
    +publish(event) void
  }

  class ReservationCanceledEvent {
    <<event>>
    +Long reservationId
    +CancelReason cancelReason
    +Money totalAmount
    +ReservationStatus originalStatus
    +Instant canceledAt
  }

  %% ─── 도메인 관계 ───
  Property "1" --> "*" RoomType : has
  RoomType "1" --> "*" DailyRoomInventory : per date
  RoomType "1" --> "*" DailyRoomRate : per date
  Reservation ..> RoomType : ref by id
  Wishlist ..> Property : ref by id
  Reservation *-- DateRange
  Reservation *-- Money
  Reservation *-- ReservationStatus
  Reservation *-- CancelReason
  DailyRoomRate *-- Money

  %% ─── 서비스 협력 ───
  ReservationService --> Reservation : aggregate root
  ReservationService --> InventoryService : delegates inventory
  ReservationService ..> PaymentService : async
  ReservationService --> DomainEventBus : publish
  DomainEventBus ..> ReservationCanceledEvent

  InventoryService --> DailyRoomInventory : atomic UPDATE

  PropertySearchService --> Property
  PropertySearchService --> RoomType
  PropertySearchService --> DailyRoomInventory : read
  PropertySearchService --> DailyRoomRate : read

  ExpirationScheduler --> ReservationService : expire(id)
```

## 2. 이 구조에서 특히 봐야 할 포인트

1. **`Reservation`은 `DailyRoomInventory`를 직접 모르고 있다** — 점선 의존조차 없음. 재고 차감은 `ReservationService → InventoryService → DailyRoomInventory`로만 일어난다. B1 합의의 코드 단위 표현.
2. **`DailyRoomInventory`의 `decrement/increment`는 SQL 한 줄로 구현되는 도메인 행위** — JPA에서는 `@Modifying @Query`로 직접 작성하거나, `Inventory` aggregate가 atomic SQL을 호출하는 방식. ORM에 끌려가지 않고 도메인의 의도를 코드에 남긴다.
3. **상태 머신은 `Reservation` 안에 응집** — `confirm/cancel/expire/checkIn/checkOut/markNoShow` 각 메서드가 전이 조건을 자체 검증. 컨트롤러·서비스가 상태를 외부에서 set 하지 못하게 한다 (setter 미노출 가정).
4. **도메인 이벤트는 `DomainEventBus`를 통해 발행** — `ReservationCanceledEvent`는 외부 환불·알림·통계 모듈의 진입점. ERD 단계에서 outbox 테이블과 매핑.
5. **`PropertySearchService`는 4개 영속 모델을 모두 read만 한다** — 검색 결과 DTO(`SearchResult`)로 가공해 반환. 검색용 모델을 별도로 두지 않고 영속 모델을 직접 조회하는 단순한 구조 (성능 한계 시 read model 분리 후보).

## 3. 잠재 리스크

- **Reservation aggregate가 비대해질 가능성** — 상태 머신 + 결제 스냅샷 + 만료 timer + 취소 사유까지 한 클래스. 라운드 3 이후 환불·정산·로열티가 붙으면 분리 후보 (`CancellationPolicy`, `PaymentSnapshot`).
- **`Property`/`RoomType` 변경 시점이 모호** — 운영자가 객실 정보를 수정하면 진행 중인 `PENDING/CONFIRMED` 예약은? B2(스냅샷)로 가격은 보호되지만, `bedType`·`maxOccupancy` 같은 메타는 진행 중 예약과 정합성이 깨질 수 있음. 이번 라운드는 "운영자 수정은 신규 예약부터 반영"으로 가정.
- **`InventoryService`의 atomic UPDATE가 RDB에 강하게 종속** — 다중 인스턴스 RDB 또는 분산 환경 진입 시 단일 SQL이 충분하지 않을 수 있음. 그때는 Redis 분산 락 또는 sharding 도입을 검토 (이번 라운드 범위 외).
