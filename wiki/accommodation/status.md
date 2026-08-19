# accommodation 구현 현황 (status)

> 설계: `docs/design/01~04`. ontology: `ontology/abox/accommodation.yaml`. 코드: `apps/commerce-api/.../domain/accommodation`.
> 범례: ✅ 구현+테스트 / 🟡 부분 / ⬜ 미구현(planned)

## Round 3 — 예약 생성 핵심 슬라이스

### 도메인 모델
| 항목 | 상태 | 비고 |
|------|------|------|
| `Reservation` (애그리거트 루트) | ✅ | 상태머신 6상태 + 루트 경유 전이(confirm/cancel/expire/checkIn/checkOut/markNoShow), 가격 스냅샷 보유 |
| `ReservationNightly` (가격 스냅샷) | ✅ | 애그리거트 내부 엔티티(@OneToMany cascade), UNIQUE(reservation_id, date) |
| `DailyRoomInventory` (일자별 재고) | ✅ | atomic UPDATE 차감, UNIQUE(room_type_id, date) + CHECK(remaining>=0) |
| `DailyRoomRate` (일자별 요금) | ✅ | 가격 스냅샷 원천 |
| VO: `Money`, `DateRange` | ✅ | @Embeddable data class + 검증 |
| `Property`, `RoomType` | ⬜ | 슬라이스에서는 `roomTypeId: Long`(ID 참조)로 대체 |
| `Wishlist` | ✅ | Round 4 구현 (아래 참조) |

### 서비스 / 흐름 (AC)
| AC | 시나리오 | 상태 | 검증 |
|----|----------|------|------|
| 예약 생성 (Main A 5~6) | 객실 타입·기간 → PENDING 생성 + 가격 스냅샷 + 일자별 재고 차감 | ✅ | `ReservationServiceIntegrationTest`, E2E |
| 더블부킹 방지 (Exception C-1/C-2) | 마지막 1개 동시 예약 → 1건만 성공 | ✅ | `InventoryServiceIntegrationTest$Concurrency` (10스레드) |
| 다중 일자 원자성 (B5) | 일부 일자 재고 부족 시 전체 롤백 | ✅ | `InventoryServiceIntegrationTest` rollback 테스트 |
| 가격 잠금 (B2) | PENDING 시점 요금을 ReservationNightly 로 스냅샷 | ✅ | `ReservationServiceIntegrationTest` |
| 소프트 홀드 10분 (P4) | `expiresAt = now + 10분` | 🟡 | 값 설정·생성은 됨. 만료 스케줄러는 ⬜ |
| 결제 확정 (Main A 7~8) | 결제 webhook → CONFIRMED | ⬜ | 상태 전이 메서드(`confirm`)는 있음, 연동 ⬜ |
| 취소/만료 + 재고 복구 (Alt B/D) | cancel/expire + `InventoryService.release` | ⬜ | 상태 전이 메서드 있음, release·스케줄러 ⬜ |
| 검색 (Main A 1~4) | 도시·기간·인원 검색 + 가격 합산 | ⬜ | `PropertySearchService` 후속 |
| 인증 연동 | `@LoopersAuth` 로 guest 식별 | ⬜ | 현재는 요청 body 의 guestId (의도된 슬라이스 한계) |

## Round 4 — 쿠폰 + 예약 트랜잭션/락/동시성

> PR: https://github.com/hyujikoh/E-commerce-BE/pull/11 (리뷰 대기).
> 검증: `:apps:commerce-api:test` 전체 통과(해피 + 배드 + 동시성, 0 실패/0 에러/0 스킵).

### 도메인 모델
| 항목 | 상태 | 비고 |
|------|------|------|
| `Coupon` (쿠폰 템플릿) | ✅ | FIXED(정액)/RATE(정률) · value · minOrderAmount · expiredAt. admin CRUD + 발급내역 |
| `IssuedCoupon` (발급 쿠폰) | ✅ | AVAILABLE/USED/EXPIRED, UNIQUE(user_id, coupon_id) — 1인 1발급·재사용 불가 |
| `Wishlist` (찜) | ✅ | 찜/찜취소, UNIQUE(guest_id, property_id). `domain/wishlist` 패키지 |
| `PropertyWishlistCount` (숙소 찜 수) | ✅ | 원자적 카운터 UPDATE로 동시 증감 정합성 (read-your-writes) |
| VO: `Money` 할인 연산 | ✅ | 정액/정률 할인 계산 + 원금 캡 + 원 단위 절사 |

### 서비스 / 흐름 (AC)
| AC | 시나리오 | 상태 | 검증 |
|----|----------|------|------|
| 쿠폰 소유·재사용 방지 | 소유자만 사용, 사용된 쿠폰 불가 | ✅ | `CouponServiceIntegrationTest$Use`, `ReservationCouponIntegrationTest$InvalidCoupon` |
| 정액/정률 할인 계산 | FIXED/RATE 할인 정확 | ✅ | `CouponTest` (정액·정률·원금 캡·절사) |
| 발급 쿠폰 1회 사용(동시) | 동시 사용해도 1회만 | ✅ | `CouponServiceIntegrationTest$ConcurrentUse` (성공 1 / ALREADY_USED 9) |
| 예약 원자성 | 다일자 부분 성공 금지 | ✅ | `ReservationCouponIntegrationTest$PartialInventoryRollback` |
| 사용 불가 쿠폰 → 예약 실패 | 존재X/사용/만료/타유저 | ✅ | `InvalidCoupon` (notFound/alreadyUsed/notOwned/expired) |
| 재고 부족 → 예약 실패 | 한 일자라도 부족 | ✅ | `PartialInventoryRollback`, `ReservationV1ApiE2ETest` |
| 전체 롤백 | 쿠폰/재고/금액 중 하나 실패 | ✅ | `PartialInventoryRollback` (쿠폰 복구 + 재고 원복 + 예약 0건) |
| 예약 성공 반영 | 할인 적용 + USED + 재고 차감 + 스냅샷 | ✅ | `WithCoupon`, E2E(원금/할인/최종금액 스냅샷) |
| 동시 찜/찜취소 정합성 | 찜 수 정상 반영 | ✅ | `WishlistServiceIntegrationTest`, `WishlistV1ApiE2ETest` |
| 동일 쿠폰 동시 예약 | 1회만 사용 | ✅ | `ReservationCouponIntegrationTest$ConcurrentSameCoupon` |
| 동일 객실·일자 동시 예약 | 더블부킹 없음 | ✅ | `ConcurrentOverlappingDates` (성공 1 / OUT_OF_INVENTORY 9) |
| 겹치는 다일자 동시 예약 | 모든 일자 정합성 | ✅ | `ConcurrentOverlappingDates` + 날짜 오름차순 락(데드락 회피) |

## 다음 슬라이스 후보
1. 예약 확정/취소/만료 + `InventoryService.release` + 만료 스케줄러
2. `Property`/`RoomType` 엔티티 + 검색(`PropertySearchService`)
3. 결제(PG) webhook 연동 + 도메인 이벤트(outbox)
4. 인증(`@LoopersAuth`) 연동으로 guestId 제거
5. 예약 API 인증 일원화 (현재 guestId 본문 값 → 헤더/토큰)
