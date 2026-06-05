# Kotlin/Spring/JPA DDD 구현 가이드 — VO 매핑 + 도메인 이벤트

> **무엇인가**: [ddd-deep-dive.md](./ddd-deep-dive.md)가 DDD 개념이라면, 이 문서는 **Kotlin+Spring Boot+JPA로 실제 매핑·발행할 때의 함정과 모범사례**다. 숙박 예약 도메인 구현(`docs/design/03,04`)에 바로 쓴다.
> **검증**: 텍스트 기반 리서치 + 적대적 검수(검수 에이전트가 `curl`로 1차 소스 재확인). 검수에서 교정된 5건을 본문에 이미 반영했다. 검증 수준: `[검증됨]`=1차 소스 정독, `[사전지식]`=표준 문서 기반·라이브 재확인 권장.

---

## 1. 엔티티 vs 임베더블 — Kotlin에서의 핵심 구분

| | 엔티티 (`@Entity`) | Value Object (`@Embeddable`) |
|---|---|---|
| Kotlin 선언 | **일반 `class`** | **`data class`** |
| 이유 | 식별자 기반 동등성. `data class`의 `copy()`/전 프로퍼티 `equals`/`toString`(LazyInit 위험)이 부적합 | 식별자 없는 값 동등성이 **올바름** → `data class`가 적합 |
| 가변성 | 본문 필드 + `protected set`(캡슐화) | `val` 불변 |
| 프로젝트 예 | `UserModel`(일반 class) | `Name`/`PhoneNumber`(@Embeddable data class) |

→ 이 프로젝트는 **이미 이 구분을 정확히** 지키고 있다. 숙박 도메인도 동일하게: `Reservation`=일반 class, `Money`/`DateRange`=`@Embeddable data class`. `[검증됨/사전지식]`

### 컴파일러 플러그인 `[검증됨 — 검수 교정]`
- `kotlin("plugin.jpa")`: `@Entity`/`@Embeddable`/`@MappedSuperclass`에 **no-arg 생성자 합성** + **allopen(open화)**. ← JPA 클래스의 프록시·리플렉션 생성을 가능케 함.
- `kotlin("plugin.spring")`: `@Component`/`@Service`/`@Configuration`류만 open. **`@Entity`는 건드리지 않음.**
- 이 프로젝트: 루트가 모든 모듈에 `plugin.spring`, `modules/jpa`+`commerce-api`가 `plugin.jpa` 적용 → 숙박 엔티티/VO는 그대로 안전.

### 엔티티 equals/hashCode `[검증됨]`
- `BaseEntity`는 현재 **미오버라이드**(참조 동등). 단일 트랜잭션 범위에선 무해.
- `Set<Entity>`·detached 비교가 필요해지면 **전략 (b)**를 `BaseEntity`에 한 번만: `equals`는 `id`가 양쪽 non-null이고 같을 때만 true, `hashCode`는 클래스 상수(`javaClass.hashCode()`). `@GeneratedValue` id가 persist 후 바뀌어도 hashCode 불변.
- 출처: [Vlad — JPA entity identifier equals/hashCode](https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/)

---

## 2. @Embeddable VO 매핑

### Money — amount + currency **두 컬럼** `[검증됨]`
단일 컬럼은 통화를 잃는다. `@Embeddable data class` + 필드에 `@Column` 직접. 같은 VO를 한 엔티티에 둘 이상 임베드할 때만 `@AttributeOverride`로 컬럼명 분리. 출처: [Vlad — MonetaryAmount JPA](https://vladmihalcea.com/monetaryamount-jpa-hibernate/)

### nullable @Embedded 함정 `[검증됨 — 검수 교정]`
**임베디드의 모든 컬럼이 null이면 Hibernate가 VO 인스턴스 자체를 null로 로드**한다(빈 VO와 null 구분 불가). → 선택적 VO(예: 취소정보)는 핵심 필드를 `nullable = false`로 두어 "전부 null" 상황을 차단. 출처: Hibernate User Guide.

### 식별자/참조 VO `[검증됨]`
- **PK용 식별자 VO** → `@Embeddable` + `@EmbeddedId` (AttributeConverter는 PK에 **스펙상 금지**).
- **non-PK 단일컬럼 참조 VO**(`PropertyId` 등) → `AttributeConverter(@Converter)` 가능.
- **여러 컬럼 VO**(Money, DateRange) → `@Embeddable`.
- `@Convert` 금지 대상: Id/version/relationship/명시적 `@Enumerated`·`@Temporal`, 다중컬럼. 출처: [Thorben — AttributeConverter](https://thorben-janssen.com/jpa-attribute-converter/), [Jakarta Persistence 3.1 spec](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)
- **이 프로젝트 권장**: 기존 `UserModel`이 식별자를 단순 `Long`으로 다루므로, 일관성을 위해 다른 애그리거트 참조는 **`Long` ID**(`roomTypeId`, `guestId`)로. PK는 `BaseEntity`의 surrogate `Long`.

### @ElementCollection 한계 `[검증됨]`
- value/`@Embeddable` 컬렉션을 별도 테이블에 저장하지만 **요소에 식별성이 없어, 하나만 바꿔도 전체 DELETE 후 재INSERT**. 컬렉션이 크면 페널티.
- **권장**: 아주 작은 불변 VO 컬렉션에만. 길어질 수 있으면 `@OneToMany` 엔티티화. 출처: [Thorben — ElementCollection](https://thorben-janssen.com/hibernate-tips-elementcollection/)
- 숙박 적용: `ReservationNightly`(예약당 N박 가격 스냅샷, 생성 시 1회 고정, 평균 수박)은 작은 불변 컬렉션 → `@ElementCollection`이 적합.

---

## 3. 도메인 이벤트 발행 — 3방식 `[검증됨 — 검수 교정 다수]`

| 항목 | ApplicationEventPublisher + @(Transactional)EventListener | Spring Data @DomainEvents / AbstractAggregateRoot | Transactional Outbox |
|---|---|---|---|
| 트랜잭션 경계 | `@TransactionalEventListener` phase: **AFTER_COMMIT(기본)**/BEFORE_COMMIT/AFTER_ROLLBACK. **활성 TX 없으면 미실행**(`fallbackExecution=true`로만 우회) | 리포지토리 메서드 호출 시 발행(같은 TX) | outbox INSERT를 비즈니스 변경과 **같은 커밋**에 포함 → 원자성 |
| 발행 시점 | `publishEvent()` 호출 시 | `save/saveAll/delete/deleteAll/deleteInBatch/deleteAllInBatch` 호출 시. **`deleteById`는 제외**. dirty checking만으로 UPDATE 시 **누락** | 커밋 |
| 비동기 | `@Async`(예외 전파 안 됨 → `AsyncUncaughtExceptionHandler`) | 수신 측 선택 | relay(폴링/CDC) 비동기 |
| dual-writes | **대응 못 함** | **대응 못 함** | **표준 해법** |

출처: [Spring — Transaction event](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html), [Spring Data — domain events](https://docs.spring.io/spring-data/jpa/reference/repositories/core-domain-events.html), [microservices.io — Outbox](https://microservices.io/patterns/data/transactional-outbox.html), [Debezium — Outbox](https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/)

### ⚠️ 구조적 제약 `[검증됨 — 검수]`
`AbstractAggregateRoot`(이벤트 누적 + save 시 자동 발행)는 **클래스 상속**이 필요한데, 우리 엔티티는 이미 `BaseEntity`(`@MappedSuperclass`)를 상속한다. **Kotlin 단일 상속**이라 둘을 동시에 못 쓴다. → 이벤트 누적이 필요하면 (1) 로직을 `BaseEntity`에 합치거나 (2) 해당 애그리거트만 별도 베이스로 분리. **설계 시 먼저 결정.**

### 권장 조합
- **단일 DB(현 commerce-api)**: 우선 `@TransactionalEventListener(AFTER_COMMIT)` + (유실 무방지 필요 시) **Spring Modulith** Event Publication Registry. 외부 부수효과는 멱등.
- **Kafka 전파(commerce-streamer)**: **Transactional Outbox**(outbox 테이블 같은 커밋 INSERT) + Debezium `EventRouter` SMT. 소비자 **멱등** 필수(at-least-once).
- 설계 04-erd가 이미 **outbox 테이블**을 명시 → 숙박은 outbox 경로와 정합.

---

## 4. 숙박 도메인 적용 (핵심 슬라이스)

### Money / DateRange — 기존 VO 패턴 그대로
```kotlin
@Embeddable
data class Money(
    @Column(name = "amount", nullable = false) val amount: Long,        // 원화 정수(최소단위). BigDecimal도 가능
    @Column(name = "currency", nullable = false, length = 3) val currency: String,
) {
    init { if (amount < 0) throw CoreException(ErrorType.BAD_REQUEST, "금액은 음수일 수 없습니다.") }
    operator fun plus(o: Money): Money { require(currency == o.currency); return copy(amount = amount + o.amount) }
    companion object { fun krw(amount: Long) = Money(amount, "KRW"); val ZERO_KRW = krw(0) }
}
```
```kotlin
@Embeddable
data class DateRange(
    @Column(name = "check_in_date", nullable = false) val checkIn: LocalDate,
    @Column(name = "check_out_date", nullable = false) val checkOut: LocalDate,
) {
    init { if (!checkOut.isAfter(checkIn)) throw CoreException(ErrorType.BAD_REQUEST, "체크아웃은 체크인 이후여야 합니다.") }
    val nights: Int get() = ChronoUnit.DAYS.between(checkIn, checkOut).toInt()
    fun datesExclusive(): List<LocalDate> = (0 until nights).map { checkIn.plusDays(it.toLong()) }  // 체크아웃일 제외
}
```

### Reservation — 일반 class + BaseEntity + 루트 경유 상태 전이
- 다른 애그리거트는 **`roomTypeId: Long`/`guestId: Long`**(ID 참조, DDD 원칙).
- 상태 전이(`confirm/cancel/expire/...`)는 **루트 메서드**로만, 전이 조건 자체 검증. setter 미노출(`protected set`).
- `ReservationNightly`(가격 스냅샷)는 `@ElementCollection`(작은 불변 컬렉션).

### InventoryService — 재고 단독 책임, **atomic UPDATE = 더블부킹 방지**
```kotlin
// DailyRoomInventoryJpaRepository
@Modifying
@Query("update DailyRoomInventory i set i.remaining = i.remaining - 1 " +
       "where i.roomTypeId = :roomTypeId and i.date = :date and i.remaining > 0")
fun decrementIfAvailable(roomTypeId: Long, date: LocalDate): Int  // 반환=영향 행 수
```
- `reserve(roomTypeId, dates)`: 각 날짜에 `decrementIfAvailable` 호출 → **affected != 1이면 즉시 예외(롤백)**. 동시 요청 중 한쪽만 1행 변경 성공 → 나머지 "재고 부족". `WHERE remaining > 0` + DB CHECK(`remaining >= 0`)로 이중 방어.
- `ReservationService.create`가 `@Transactional`로 재고 차감 + 예약 INSERT를 한 커밋에 묶는다(B5: 단일 RDB TX).

---

## 5. 미확인·주의 (과장 방지)
- in-process 이벤트 세부(일부)는 사용 중인 spring-framework 6.x / spring-data-jpa 3.x 문서로 최종 확정 권장.
- `precision/scale`, outbox 폴링 중복 방지(`FOR UPDATE SKIP LOCKED`) 등 일부 디테일은 코드 수준 미확인.
- Money를 Long(최소단위) vs BigDecimal: 이 프로젝트는 정수 원화 가정이면 Long이 단순. 다통화·소수면 BigDecimal+scale.
