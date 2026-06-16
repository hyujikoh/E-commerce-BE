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
| `Wishlist` | ⬜ | 후속 |

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

## 다음 슬라이스 후보
1. 예약 확정/취소/만료 + `InventoryService.release` + 만료 스케줄러
2. `Property`/`RoomType` 엔티티 + 검색(`PropertySearchService`)
3. 결제(PG) webhook 연동 + 도메인 이벤트(outbox)
4. 인증(`@LoopersAuth`) 연동으로 guestId 제거
