# Round 5 — 숙소 검색 성능 측정 보고서

인덱스 적용 전(AS-IS)과 후(TO-BE)의 검색 쿼리 실행 계획·실행 시간을 비교한다.
설계 결정의 "왜"는 `wiki/accommodation/design-decisions.md#search`(쿼리)와 `#search-cache`(캐시)에, 구현 현황은 `wiki/accommodation/status.md`에 있다.

## 1. 환경·시드

| 항목 | 값 |
|------|-----|
| DB | MySQL 8.0 (docker, `docker/infra-compose.yml`) |
| 시드 | `support/seed/Round5SeedRunner` — `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local,seed'` |
| property | 10,000 (도시 10개 × 1,000) |
| room_type | 50,000 (숙소당 5, capacity 2·2·3·4·6) |
| daily_room_inventory / daily_room_rate | 각 6,150,000 (객실 타입 5만 × 123일, 2026-05-01 ~ 08-31) |
| property_wishlist_count | 10,000 (1%가 5,000+, 9%가 500+, 나머지 0~49 — 멱분포 근사) |

시드는 난수 없이 결정적 공식으로 생성한다(재실행 시 동일 데이터 → 벤치마크 재현 가능). 상세 공식은 `Round5SeedRunner` KDoc 참조.

## 2. 측정 방법

- `EXPLAIN`(플랜 확인) + `EXPLAIN ANALYZE`(실제 실행·시간)를 mysql CLI로 직접 실행. 시나리오당 2회 실행해 2회차(웜, 버퍼 풀 적재 후)를 대표값으로 기록.
- 측정 대상은 검색 SELECT(정렬 + `LIMIT 20 OFFSET 0`). COUNT 쿼리는 동일한 파생 테이블을 공유하므로 경향이 같다.
- AS-IS = `idx_property_city`, `idx_room_type_property_capacity` 두 인덱스를 DROP한 상태. TO-BE = 재생성 후. daily 테이블의 `UNIQUE(room_type_id, date)`는 양쪽 모두 존재(예약 무결성 제약이므로 제거 대상이 아님).

## 3. 결과 요약

| 시나리오 | 조건 | 정렬 | AS-IS (콜드/웜) | TO-BE (콜드/웜) | 웜 기준 개선 |
|----------|------|------|----------------|----------------|--------------|
| A | 서울, 05-15~17 (2박), 2명 | 가격 오름차순 | 274 / **161 ms** | 154 / **128 ms** | −20% |
| B | 제주, 07-30~08-04 (5박, 성수기), 2명 | 찜 수 내림차순 | 260 / **195 ms** | 148 / **162 ms** | −17% |
| C | 부산, 05-10~11 (1박), 4명 | 추천(가중 스코어) | 89.5 / **74 ms** | 94.4 / **79.6 ms** | 오차 범위 |

- **시나리오 B는 결과 0건** — 시드 공식상 성수기(재고 −2)에는 5박 연속 가용 객실이 구조적으로 존재하지 않는다(전 숙소 매진 상황 재현). 쿼리는 전체 필터·집계를 동일하게 수행하므로 비용 측정으로는 유효하다.
- 시나리오 C의 개선이 작은 이유: capacity ≥ 4 필터가 room_type 스캔을 절반(2만 행)으로 줄여 AS-IS 풀스캔 비용 자체가 작았다. 1박이라 daily 조인량도 작다.

## 4. 실행 계획 전후 비교 (시나리오 A, 웜)

### AS-IS — room_type 풀스캔이 진입점

```
-> Table scan on rt  (rows=49880) (actual rows=50000)               ← 풀스캔
-> Filter: (rt.capacity >= 2 ...)  (actual rows=50000)
-> Single-row index lookup on p using PRIMARY (id=rt.property_id)
     (actual rows=1 loops=50000)                                    ← PK 룩업 5만 회
-> Filter: (p.city = '서울' ...)  (actual rows=0.1 loops=50000)      ← 도시 필터가 조인 "뒤"
```

tabular EXPLAIN: `rt type=ALL rows=49880, Extra=Using where; Using temporary`.
도시 인덱스가 없으니 옵티마이저가 room_type 전체를 훑고 property를 5만 번 PK 룩업한 뒤에야 도시를 거른다. 5만 행 중 살아남는 것은 서울 소재 5,000행(10%)뿐 — 90%가 버려지는 일을 매 요청 반복한다.

### TO-BE — 도시 인덱스가 진입점, 복합 인덱스로 조인

```
-> Index lookup on p using idx_property_city (city='서울')
     (actual rows=1000, 0.72ms)                                     ← 1,000행만 진입
-> Index lookup on rt using idx_room_type_property_capacity (property_id=p.id),
     with index condition: (rt.capacity >= 2)
     (actual rows=5 loops=1000)                                     ← 조인+capacity 필터를 인덱스에서
-> Index lookup on dr using uk_rate_room_type_date ... (rows=2 loops=5000)
-> Single-row index lookup on di using uk_inventory_room_type_date (loops=10000)
```

tabular EXPLAIN: `p type=ref key=idx_property_city rows=1000` / `rt type=ref key=idx_room_type_property_capacity, Extra=Using index condition`.
진입이 "서울 1,000 숙소"로 시작하고, room_type 조인과 capacity 필터가 복합 인덱스 안에서 처리된다(ICP). AS-IS 대비 접근 행 수가 약 10만 → 6천으로 감소.

### 남은 병목 — daily 조인·집계

TO-BE 웜 128ms의 내역: p+rt 접근 ~8ms, **dr 인덱스 룩업 5,000회 ≈ 75ms + di 룩업 10,000회 ≈ 33ms**, 집계·정렬 ~4ms.
즉 인덱스 후에도 비용의 85%는 (가용 객실 타입 × 일자) 조인과 GROUP BY 집계다. 이것은 "도시 내 모든 후보의 기간 가용·총액"을 계산하는 검색의 본질 비용이라 단일 쿼리 인덱스로는 더 줄지 않는다 — **이 반복 비용을 흡수하는 것이 Redis 검색 캐시(TTL 60초)의 역할**이다. 캐시 적중 시 DB를 전혀 타지 않는다.

## 5. 인덱스 선정 근거

| 인덱스 | 컬럼 | 역할 |
|--------|------|------|
| `idx_property_city` | `property(city)` | 검색 진입점. 도시당 1,000행으로 후보를 즉시 축소 |
| `idx_room_type_property_capacity` | `room_type(property_id, capacity)` | property→room_type 조인 + `capacity >= 인원` 필터를 인덱스 내에서 처리(ICP) |
| (기존) `uk_rate_room_type_date` | `daily_room_rate(room_type_id, date)` | 객실 타입별 일자 범위 룩업. UNIQUE 제약이 조인 인덱스를 겸함 |
| (기존) `uk_inventory_room_type_date` | `daily_room_inventory(room_type_id, date)` | 동일. `(room_type_id, date)` 단건 룩업 |

추가하지 않은 것: `daily_*` 테이블의 별도 보조 인덱스(기존 UNIQUE가 커버, 615만 행 테이블의 쓰기 비용만 증가), `property(city, id)` 커버링(정렬이 집계 결과 기준이라 커버링 이득 없음).

## 6. 정렬 3종과 추천 스코어

- `PRICE_ASC`: `min_total_amount ASC` / `WISHLIST_DESC`: `COALESCE(wishlist_count, 0) DESC` — 찜 수는 Round 4의 비정규화 카운터 `property_wishlist_count`를 LEFT JOIN (없으면 0).
- `RECOMMENDED`: `0.7 × LN(1 + 찜수) − 0.3 × LN(GREATEST(총액, 1)) DESC`. 찜 수가 멱분포(상위 1%가 5,000+)라 로그로 눌러 가격 신호가 죽지 않게 했다. 정렬은 어차피 집계 결과(도시당 ≤1,000행) 위에서의 filesort라 스코어 식 계산 비용은 무시 가능(시나리오 C 참조).

## 7. 캐시 (요약)

전략 상세와 "왜"는 `wiki/accommodation/design-decisions.md#search-cache`. 요약:

- cache-aside — 검색 페이지 TTL 60초, 상세 기본 정보 TTL 10분. 찜 수는 캐시하지 않고 매 조회 병합.
- **일자별 재고·요금 원본은 캐시하지 않는다.** 검색 결과가 낡아도 예약 생성의 원자적 조건부 UPDATE가 DB 재고를 재확인하므로 초과 판매는 불가능하다.
- 캐시(Redis) 장애 시 warn 로그 + DB 폴백으로 정상 동작. 동작 검증: `PropertyFacadeCacheIntegrationTest`.

## 8. 재현 절차·주의사항

```bash
# 1. 시드 (완료 후 프로세스 자동 종료, 재실행 시 기존 데이터 있으면 스킵)
./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local,seed'

# 2. AS-IS: 인덱스 제거
docker exec mysql mysql -uapplication -papplication loopers -e \
  "DROP INDEX idx_property_city ON property; DROP INDEX idx_room_type_property_capacity ON room_type;"

# 3. 측정 (시나리오별 EXPLAIN + EXPLAIN ANALYZE ×2)
docker exec -i mysql mysql --default-character-set=utf8mb4 -uapplication -papplication -r loopers < scenario.sql

# 4. TO-BE: 인덱스 재생성 후 3을 반복
docker exec mysql mysql -uapplication -papplication loopers -e \
  "CREATE INDEX idx_property_city ON property(city); CREATE INDEX idx_room_type_property_capacity ON room_type(property_id, capacity);"
```

- **local 프로필은 `ddl-auto=create`** — 앱(또는 테스트) 재기동 시 스키마가 재생성되어 시드가 소멸한다. 순서는 반드시 "테스트 → 시드 → 벤치마크". `/commit`의 테스트 pre-check도 시드를 지우므로 수치 기록을 먼저 끝낸다.
- mysql CLI에는 **`--default-character-set=utf8mb4`가 필수** — 없으면 `'서울'` 리터럴이 latin1로 전송되어 0건 매치된다(앱의 JDBC 경로는 무관).
