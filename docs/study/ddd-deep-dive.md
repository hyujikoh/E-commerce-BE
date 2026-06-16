# DDD 딥다이브 — 학습 커리큘럼 + 이 코드베이스 적용 가이드

> **무엇인가**: Kotlin + Spring Boot 기반 e-commerce 백엔드(`commerce-api`)에서 DDD를 전술·전략 풀스펙으로 학습하고 실제 코드에 적용하기 위한 가이드.
> **어떻게 만들었나**: deep-research 워크플로우(5개 검색 앵글 × 병렬 검색 → 25개 소스 fetch → 113개 주장 추출 → 주장별 3표 적대적 검증)로 조사. **23개 주장이 3-0 검증 통과**, 2개는 0-3으로 반증되어 폐기. 1차 출처(Vernon 원전, Microsoft Learn DDD eBook, Context Mapper 공식 문서, AWS Prescriptive Guidance, 검증된 예제 repo) 중심.
> **상태**: 개념 정전(canon)은 high confidence. 구현 세부 4개 영역은 미검증 → [§9 심화 조사 필요](#9-심화-조사-필요-open-questions).

---

## 0. 이 문서 읽는 법

- **DDD를 처음 배운다** → §1(로드맵) → §3(전술) → §4(이 코드 적용).
- **바로 week3 작업에 들어간다** → §6(시작 도메인) → §4(적용) → §7(체크리스트).
- **전략 설계가 궁금하다** → §2.
- 각 주장 끝의 `[S#]`는 §10 출처 번호. 검증 결과(`3-0` 등)도 함께 표기.

---

## 1. 학습 로드맵

### 1.1 단계별 순서 (confidence: medium — 합성 권장)

> 단일 권위 출처가 "이 순서대로 배우라"고 못박지는 않는다. 검증된 출처들의 깊이와 DDD 원칙에서 합성한 권장 순서다 `[S1,S4,S7,S8]`.

1. **전략 기초** — Ubiquitous Language, Bounded Context의 "왜". 도메인 언어가 코드·DB·API 명칭의 기준이 된다는 감각부터.
2. **도메인 탐색** — Event Storming으로 흐름을 펼치고 Subdomain을 Core / Supporting / Generic으로 분류.
3. **전술 패턴** — Entity / Value Object / Aggregate / Repository / Domain Service / Domain Event / Factory.
4. **아키텍처** — Layered vs Hexagonal vs Clean. 의존성 방향 규칙.
5. **Kotlin/Spring 구현** — JPA `@Embeddable` VO 매핑, 도메인 이벤트 발행, (필요시) Transactional Outbox.
6. **실습** — 한 바운디드 컨텍스트를 끝까지 만들어 본다.

### 1.2 핵심 도서 (업계 정전)

| 난이도 | 책 | 쓰임 |
|--------|----|----|
| 입문 | Vaughn Vernon, *Domain-Driven Design Distilled* | 전략 설계·서브도메인·이벤트스토밍을 얇고 빠르게 |
| 입문 | Vlad Khononov, *Learning Domain-Driven Design* | 현대적·접근성 높은 전략+전술 통합 입문 |
| 중급 | Vaughn Vernon, *Implementing DDD* (Red Book) | **애그리거트 설계 규칙·헥사고날·도메인 이벤트의 1차 출처** |
| 레퍼런스 | Eric Evans, *Domain-Driven Design* (원전) | 용어의 출발점. 통독보다 사전처럼 |

### 1.3 실습 예제 프로젝트 (검증됨)

- **시작용 — `ttulka/ddd-example-ecommerce-kotlin`** `[S7]` `3-0`
  e-commerce를 **Sales(Core) / Warehouse / Billing / Shipping(Supporting)** 4개 바운디드 컨텍스트로 분리. Kotlin+Spring **실코드**로 Core/Supporting 분류와 도메인 이벤트 통신을 보여준다. README가 명시적으로 *"rejects anti-patterns like Anemic Domain Model"*. → 우리 프로젝트 구조(`com.loopers.domain.*`)와 가장 가까운 출발점.
- **심화 — `ddd-by-examples/library`** `[S8]` `3-0`
  전략(Big Picture + Design Level **Event Storming**, Example Mapping, Ubiquitous Language, catalogue·lending 두 컨텍스트) + 전술(Aggregate, VO, Domain Event, Repository, Policy, **CQRS**)을 종합. 복잡한 `lending`은 헥사고날(도메인에 Spring 어노테이션 없음), 단순한 `catalogue`는 CRUD. **ArchUnit으로 "model이 infrastructure/spring에 의존하지 않음"을 강제** — 의존성 규칙을 테스트로 박는 모범.

---

## 2. 전략 설계 (Strategic Design)

전략 설계는 "큰 그림에서 경계를 어디에 긋는가"다. 코드를 짜기 전에 한다.

### 2.1 Bounded Context & Subdomain
- **Subdomain 분류**: Core(경쟁력의 원천, 가장 투자), Supporting(필요하지만 차별화 아님), Generic(살 수 있는 것). e-commerce에서 **Sales/Order가 Core**, 재고·결제·배송은 Supporting으로 보는 게 검증된 예제의 분류 `[S7]`.
- **경계 식별 기준**: "관계가 있는 곳"이 아니라 **"규칙(불변식)이 함께 지켜져야 하는 곳"**에 경계를 긋는다(→ §3.1).

### 2.2 Context Mapping 관계 패턴 `[S5]` `3-0`
관계는 **대칭**과 **비대칭**으로 갈린다:

| 분류 | 패턴 | 비고 |
|------|------|------|
| 대칭 (방향 없음) | Partnership, Shared Kernel | 두 팀이 운명 공동체 |
| 비대칭 — upstream 역할 | Open Host Service(OHS), Published Language(PL) | 공급자가 안정적 API/언어 공개 |
| 비대칭 — downstream 역할 | Anticorruption Layer(ACL), Conformist(CF) | 소비자가 보호막을 치거나(ACL) 그대로 따름(CF) |

- **공식 제약**: OHS·PL·ACL·CF는 **대칭 관계(Partnership/Shared Kernel)에는 적용되지 않는다** (Context Mapper Language Semantics Rule #4) `[S5]`.
- ⚠️ **흔한 오해(반증됨)**: "다운스트림은 Conformist 또는 ACL 중 **정확히 하나만** 골라야 한다"는 이분법은 **틀렸다**(0-3 반증). 둘은 대표 선택지일 뿐 강제 양자택일이 아니다.

### 2.3 Event Storming
도메인 이벤트(주황 포스트잇)를 시간순으로 펼쳐 흐름·경계·애그리거트 후보를 발견하는 워크숍. Big Picture(전체) → Design Level(상세) 순으로. `ddd-by-examples/library`가 실제 적용 사례 `[S8]`.

---

## 3. 전술 패턴 (Tactical Patterns)

### 3.1 Aggregate — DDD의 심장

**애그리거트 = 트랜잭션 일관성 경계** `[S1,S4]` `3-0`. 경계 안의 모든 것은 어떤 연산 후에도 비즈니스 불변식을 만족해야 한다. 경계 밖의 일관성은 그 애그리거트의 책임이 아니다.

> Vernon 원전: *"aggregate is synonymous with transactional consistency boundary"*. 첫 규칙은 *"Model True Invariants In Consistency Boundaries"* — 개발자가 지어낸 가짜 불변식이 아니라 **진짜 불변식**을 경계 안에 모델링하라 `[S1]`.

**Vernon의 애그리거트 설계 4원칙** `[S1,S2,S6]` `3-0` (모두 1차 출처 verbatim 확인):

1. **작게 설계하라** — 루트 엔티티 + 함께 일관성을 지켜야 하는 최소한의 속성/VO만. *"This large-cluster Aggregate will never perform or scale well."*
2. **외부 애그리거트는 ID로만 참조하라** — 직접 객체 참조 금지. *"Prefer references to external Aggregates only by their globally unique identity."*
3. **경계 밖은 결과적 일관성** — 다른 애그리거트는 같은 트랜잭션이 아니라 이벤트로 나중에 맞춘다.
4. **트랜잭션당 애그리거트 하나만 수정** — *"modifies only one aggregate instance per transaction in all cases."* 단, Vernon 본인이 *"rule of thumb / should be the goal in most cases"*로 완화. 예외 절("Reasons to Break the Rules")이 존재 → **절대 규칙 아님, 기본값**.

**애그리거트 루트 = 유일한 수정 진입점** `[S4]` `3-0`. 내부 자식 엔티티/VO 변경은 **반드시 루트를 거쳐** 불변식을 보존한다. 애그리거트 간 직접 내비게이션을 금지하고 다른 루트는 FK(ID)로만 참조한다.
> Microsoft Learn: *"it should be the only entry point for updates... The Order entity only has a foreign key field for the buyer, but not a navigation property."*

**식별 방법**: "가장 흔한 트랜잭션에서 함께 일관성을 지켜야 하는 엔티티들"을 분석 → 그게 한 애그리거트 `[S1]`.

### 3.2 Entity vs Value Object `[S4]` `3-0`
- **Entity**: 속성이 아니라 **식별자(시간에 걸친 연속성·영속성)**로 정의된다. *"primarily defined by their identity, continuity, and persistence over time."*
- **Value Object**: 개념적 식별자 없이 도메인의 한 특성을 **기술**할 뿐. 불변.
- **같은 개념도 컨텍스트마다 다르다**: 주소는 e-commerce에선 VO, 전력회사 앱에선 Entity.

### 3.3 나머지 전술 요소
- **Repository**: 애그리거트 루트 단위로 영속성을 추상화하는 포트. (우리 `domain/*/XxxRepository`가 이 역할 → §4.)
- **Domain Service**: 한 엔티티/VO에 자연스럽게 속하지 않는 도메인 로직.
- **Domain Event**: 도메인에서 "일어난 일"(과거형: `OrderPlaced`). 컨텍스트 간 통신·결과적 일관성의 매개.
- **Factory**: 복잡한 애그리거트 생성 캡슐화.

### 3.4 ⚠️ Anemic Domain Model (빈약한 도메인 모델) 안티패턴 `[S11]`
데이터(필드 + getter/setter)만 있고 행위가 없는 도메인 객체 → 로직이 서비스로 새어나가는 **안티패턴**. 검증된 두 예제 모두 명시적으로 거부 `[S7]`. **판별법**: 도메인 객체에 의미 있는 비즈니스 메서드가 있는가? setter로 아무 때나 상태를 바꿀 수 있는가(있으면 위험)?

---

## 4. 이 코드베이스에 매핑 (가장 중요한 절)

우리 `commerce-api`는 이미 DDD 레이어드 구조 + **잘 만들어진 User 애그리거트**를 갖고 있다. 위 개념이 실제 어디에 대응하는지:

### 4.1 레이어 = 의존성 역전
```
interfaces/api  →  application(facade)  →  domain  ←  infrastructure
```
- `domain/*/XxxRepository.kt`(인터페이스, Spring 의존 없음) = **Repository 포트**. `infrastructure/*/XxxRepositoryImpl.kt`가 구현 = 어댑터. → **헥사고날의 핵심(도메인이 인프라를 모름)을 레이어드 형태로 이미 실현** `[S9]`.
- 참고 슬라이스: `apps/commerce-api/src/main/kotlin/com/loopers/{domain,application,infrastructure,interfaces}/example/`

### 4.2 Value Object = `@Embeddable data class` (이미 모범적으로 적용 중)
실제 코드 `domain/user/vo/Name.kt`:
```kotlin
@Embeddable
data class Name(
    @Column(name = "name", nullable = false) val value: String,
) {
    init { if (value.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.") }
    fun masked(): String = if (value.length <= 1) "*" else value.dropLast(1) + "*"
}
```
- 불변(`val`) + `@Embeddable` + `init` 검증 + 도메인 행위(`masked()`). 리서치 권장(VO = data class + 불변 + `@Embeddable`)과 **정확히 일치** `[S4]`. **이미 올바른 길**.
- 존재 VO: `Name`, `BirthDate`, `PhoneNumber`, `Password`, `RawPassword`. ⚠️ Kotlin+JPA 매핑 함정은 §9.

### 4.3 Aggregate Root = `BaseEntity` 상속 + 루트 경유 행위 (이미 좋은 예시)
실제 코드 `domain/user/UserModel.kt`가 **교과서적 애그리거트 루트**다:
- `@Entity : BaseEntity()` — 식별자(Long PK)로 정의되는 엔티티(§3.2).
- VO들을 `@Embedded var ... protected set` — **외부에서 직접 못 바꿈**(캡슐화).
- `init { }` — 불변식(loginId 패턴, email 필수) 보장.
- `changePassword(...)` — **루트 경유 도메인 행위**. "새 비밀번호 == 현재 비밀번호면 거부"라는 비즈니스 규칙을 메서드 안에서 강제 → §3.4 Anemic의 정반대(풍부한 모델).
- → 이 패턴을 새 도메인(Order 등)에 그대로 복제하면 된다.

### 4.4 우리 구조에서 한 단계 더 갈 곳
- **현재**: `User` 애그리거트 완성(루트+VO 5종+불변식+행위), `example` 슬라이스.
- **다음(week3)**: **새 Core 도메인(Order 권장)을 애그리거트로** — `Order`가 `OrderItem`들과 금액 합계 불변식을 보유, 상품/구매자는 **ID로만 참조**(원칙 2), 상태 전이를 루트 메서드로.
- **그 다음**: **Domain Event** 발행으로 결제·재고와 결과적 일관성.

---

## 5. e-commerce 도메인 모델링

### 5.1 컨텍스트 분리 (검증된 사례) `[S7]` `3-0`
`ttulka/ddd-example-ecommerce-kotlin`: **Sales(Core)** = 상품 등록·카탈로그·가격·주문 검증/생성(Catalog·Cart·Order 포함), **Warehouse/Billing/Shipping(Supporting)**. 컨텍스트 간 통신은 도메인 이벤트(`OrderPlaced`, `PaymentCollected`, `GoodsFetched`, `DeliveryDispatched`)로만. *"Events contain no Domain Objects"*.
- ⚠️ **반증됨**: "이벤트 Listener가 곧 ACL"이라는 일반화는 틀림(0-3). 형식적 ACL은 thin listener 이상이고, 정석은 **내부 도메인 이벤트 vs 공개 통합 이벤트** 구분이다.

### 5.2 애그리거트 간 일관성 — Saga `[S10,S7]` `3-0`
분산/`database-per-service`에서는 단일 조정자가 없어 **2PC가 사실상 불가능** → **Saga 코레오그래피**로 결과적 일관성. 각 서비스가 로컬 트랜잭션으로 자기 DB를 갱신·이벤트 발행 → 후속 서비스가 구독(order→inventory→payment) → 실패 시 **보상 트랜잭션**으로 복원.
> AWS: *"the two-phase commit is not an option... use the saga choreography pattern... If the payment fails, the payment service runs a compensatory transaction."*
- ⚠️ **함정**: "DB 갱신 + 이벤트 발행을 원자적으로"는 **dual-writes 문제**. 실무엔 **Transactional Outbox** 필요(AWS 본문도 명시). 교육용 예제는 in-process 이벤트로 단순화.
- **우리 함의**: 단일 DB 모놀리스(`commerce-api`)에선 처음엔 **단일 트랜잭션 + Spring 이벤트**로 충분. 멀티 앱(`commerce-streamer`+kafka) 확장 때 Saga/Outbox 도입.

### 5.3 재고 동시성 (오버셀 방지)
→ **검증된 1차 출처 미확보**. §9 open question. (낙관적 `@Version` vs 비관적 `FOR UPDATE` vs atomic decrement UPDATE vs 예약/점유 모델)

---

## 6. week3 시작 도메인 추천: **주문(Order)** `[S7,S4,S1]` (confidence: medium)

> User 애그리거트는 이미 완성돼 있으므로(§4.3), week3는 **새 Core 도메인 추가**가 자연스럽다.

검증된 예제 + DDD 원칙에서 합성한 권장. **Order가 애그리거트 설계 학습에 가장 좋다**:

1. **명확한 트랜잭션 일관성 경계** — 주문·주문항목·금액합계가 함께 일관되어야 함 → §3.1 "작은 애그리거트 + 진짜 불변식"을 직접 체득.
2. **ID 참조 패턴** — 상품·구매자를 객체가 아니라 FK(ID)로 참조(원칙 2·루트 규칙)를 자연스럽게 연습.
3. **결과적 일관성의 출발점** — 결제·재고·배송으로 이어지는 Saga/도메인 이벤트의 진입.
4. **Core 도메인** — 전략적으로 학습·투자 가치 최고(Sales=Core).

**대안 비교**:
- **Catalog**: CRUD에 가까워 전술 패턴 학습 효과 낮음(`ddd-by-examples`도 catalogue를 CRUD로 처리).
- **Inventory**: 동시성 제어 학습엔 훌륭하나 애그리거트 설계 **입문**엔 Order가 더 적합. 동시성을 깊게 파려면 Inventory가 2순위.

> 최종 도메인 선정은 사용자 확인 후. 이 추천은 "근거 있는 기본값"이다.

---

## 7. week3 실행 체크리스트 (도메인 확정 후)

기존 설계 파이프라인(`docs/design/` accommodation 템플릿)을 그대로 따른다:

1. **설계 문서** — `01-requirements`(유비쿼터스 언어·불변식·상태머신) → `02-sequence` → `03-class-diagram`(애그리거트 경계 명시) → `04-erd`.
2. **구현** (`apps/commerce-api`, **`UserModel` 패턴 복제**):
   - `domain/{d}/{D}Model.kt` — `@Entity : BaseEntity()`, `init` 불변식, VO는 `@Embedded var ... protected set`, **루트 경유 도메인 메서드**, 외부 애그리거트는 ID 참조.
   - `domain/{d}/vo/*.kt` — `@Embeddable data class` + `CoreException` 검증(`Name.kt` 패턴).
   - `domain/{d}/{D}Repository.kt`(포트) → `infrastructure/{d}/{D}RepositoryImpl.kt` + `{D}JpaRepository.kt`.
   - `application/{d}/{D}Facade.kt` + `{D}Info.from()`.
   - `interfaces/api/{d}/{D}V1Controller.kt` + `Dto` + `ApiSpec`.
3. **테스트** — 단위(`{D}ModelTest`, VO `*Test`) + 통합(`{D}ServiceIntegrationTest`, `@SpringBootTest`+Testcontainers) + E2E(`{D}V1ApiE2ETest`).
4. **지식 동기화** — `ontology/abox/{d}.yaml`(`repo`+`package`+`wiki_doc`) + `wiki/{d}/`.
5. **검증** — `./gradlew :apps:commerce-api:test`, `/domain-audit`, `/pull-request`(머지는 사용자 직접).

---

## 8. 학습 중 주의할 오해 (반증된 주장)

| 흔한 오해 | 실제 | 검증 |
|-----------|------|------|
| "다운스트림은 Conformist or ACL 중 하나만 골라야 한다" | 둘은 대표 선택지일 뿐, 강제 양자택일 아님 | 0-3 반증 |
| "이벤트 Listener = ACL" | 형식적 ACL은 thin listener 이상. 핵심은 내부 도메인 이벤트 vs 공개 통합 이벤트 구분 | 0-3 반증 |
| "트랜잭션당 애그리거트 하나는 절대 규칙" | rule of thumb / 기본값. Vernon도 예외 절을 둠 | 출처 caveat |
| "도메인 객체에 setter 두는 게 편하다" | Anemic 안티패턴의 시작. `UserModel`처럼 `protected set` + 행위 메서드 | §3.4, §4.3 |

---

## 9. 심화 조사 필요 (Open Questions)

리서치에서 **검증된 1차 출처 주장으로 확보되지 못한** 영역. Stage C 구현 전/중 추가 조사 권장(①②는 우리 코드에 직결):

1. **Kotlin/JPA `@Embeddable` VO 매핑 함정** — `data class`와 JPA no-arg 생성자 충돌(`kotlin-jpa` plugin), VO 컬렉션 매핑, 식별자 VO의 `equals/hashCode`. ← 이미 `@Embeddable data class` VO를 쓰므로 가장 시급.
2. **도메인 이벤트 발행 3방식 비교** — `ApplicationEventPublisher` vs Spring Data `@DomainEvents`/`AbstractAggregateRoot` vs Transactional Outbox. 트랜잭션 경계·실패 처리·dual-writes 해결 조합.
3. **재고 동시성 제어** — 낙관적(`@Version`) vs 비관적(`FOR UPDATE`) vs atomic decrement vs 예약/점유 모델의 오버셀 방지 트레이드오프.
4. **아키텍처 비교 + 성숙도 체크리스트** — Layered vs Hexagonal vs Clean 정량 비교, DDD 성숙도 자가진단(유비쿼터스 언어 코드 반영도·애그리거트 경계 적정성·Anemic 징후).

---

## 10. 출처

검증 통과 주장의 1차 출처 중심. `quality`: primary(1차/공식), secondary, blog.

| # | 출처 | 품질 | 영역 |
|---|------|------|------|
| S1 | [Vernon, *Effective Aggregate Design* (PDF)](https://www.dddcommunity.org/wp-content/uploads/files/pdf_articles/Vernon_2011_1.pdf) | primary | 전술/애그리거트 |
| S2 | [InformIT — IDDD 발췌 seqNum=4](https://www.informit.com/articles/article.aspx?p=2020371&seqNum=4) | primary | 전술 |
| S3 | [InformIT — IDDD 발췌 seqNum=3](https://www.informit.com/articles/article.aspx?p=2020371&seqNum=3) | primary | 전술 |
| S4 | [Microsoft Learn — DDD/CQRS Domain Model](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/microservice-domain-model) | primary | 전술/애그리거트/Entity·VO |
| S5 | [Context Mapper — Language Model](https://contextmapper.org/docs/language-model/) | primary | 전략/Context Mapping |
| S6 | [archi-lab.io — Vernon Aggregate Rules](https://www.archi-lab.io/infopages/ddd/aggregate-design-rules-vernon.html) | secondary | 전술 |
| S7 | [ttulka/ddd-example-ecommerce-kotlin](https://github.com/ttulka/ddd-example-ecommerce-kotlin) · [(Java판)](https://github.com/ttulka/ddd-example-ecommerce) | primary | e-commerce 모델링 |
| S8 | [ddd-by-examples/library](https://github.com/ddd-by-examples/library) | primary | 심화 예제 |
| S9 | [dustinsand/hex-arch-kotlin-spring-boot](https://github.com/dustinsand/hex-arch-kotlin-spring-boot) | primary | Kotlin 헥사고날 |
| S10 | [AWS — Saga Choreography](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/saga-choreography.html) | primary | 일관성/Saga |
| S11 | [Fowler — Anemic Domain Model](https://martinfowler.com/bliki/AnemicDomainModel.html) | blog | 안티패턴 |
| S12 | [heynickc/awesome-ddd](https://github.com/heynickc/awesome-ddd) | secondary | 자료 모음 |

참고(미검증 보강 블로그): Baeldung [spring-data-ddd](https://www.baeldung.com/spring-data-ddd), Thorben Janssen [domain-event](https://thorben-janssen.com/spring-data-jpa-domain-event/), [JPA VO](https://dev.to/peholmst/using-value-objects-with-jpa-27mi), [locking strategies](https://codewiz.info/blog/locking-strategies-spring-boot/).

---

## 부록 — 리서치 통계

- 검색 앵글 5 · fetch 25 소스 · 추출 113 주장 · 검증 25 → **확정 23 / 폐기 2** · 서브에이전트 107 · 약 3.05M 토큰.
- 검증 방식: 주장별 독립 3표 적대적 검증, 2/3 이상 반증 시 폐기.
