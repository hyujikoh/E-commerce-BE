# Week5 코드 리뷰 기록 — 검색 최적화 (인덱스·네이티브 쿼리·Redis 캐시) (PR #13)

- **리뷰 일자**: 2026-08-19
- **대상**: `main..week5` diff (PR #13, 커밋 `8edb4306`)
- **방식**: [OpenCodeReview](https://github.com/alibaba/open-code-review) delegation 모드 — `ocr delegate preview` / `ocr delegate rule`로 리뷰 대상·룰을 산출하고, 호스트 에이전트(Claude Code)가 리뷰 수행
- **범위**: 리뷰 대상 34개 파일 (Kotlin 33 + ontology YAML 1, 테스트 3개·문서 5개 자동 제외)
  - 룰 그룹 1 (Kotlin): Null Safety·데드코드, 간결성, 컬렉션 연산, 코루틴, 클래스/객체 설계, 리소스 관리, 성능 함정, Java interop, 불변성·문자열 템플릿
  - 룰 그룹 2 (YAML): 키 철자 검사 (값 내용은 제외)
- **원칙**: OCR의 precision-over-recall — 근거가 확인된 항목만 보고, 추측성 지적은 제외
- **사전 검증**: commerce-api 테스트 79개 스위트 전부 통과 (커밋 pre-check 실제 실행, Testcontainers MySQL·Redis). commerce-batch 실패는 main과 diff 0건인 기존 문제로 리뷰 범위 외.

## 결론

**Critical 없음. Major 2건(가용성·성능), Minor 2건, 정책 확인 질문(QUESTION) 2건.**
Major 2건 모두 기능 정확성 문제가 아니라 캐시 미스 경로의 비용·보호장치에 관한 것으로, 하나는 부하 테스트로 실증됐다.

---

## 🟠 Major (수정 권장)

### M1. 검색 캐시 고정 TTL 60초 — 동시 만료 스탬피드로 커넥션 풀 고갈 (부하 테스트 실증)

- **위치**: `infrastructure/property/PropertyRedisCacheRepository.kt` (`SEARCH_TTL`), `application/property/PropertyFacade.kt` (cache-aside 미스 경로)
- **내용**: 같은 시점에 적재된 인기 검색 키들이 60초 뒤 **동시에 만료**되고, 만료 순간 모든 동시 요청이 미스로 DB에 직행한다(단일 비행 없음). 부하 테스트(`docs/perf/round5-load-test.md` § 3)에서 100 VU 기준 TTL 경계(정확히 60초 간격)마다 Hikari 풀(40) 고갈 → `CannotCreateTransactionException` → 500 응답 203건이 실증됐다. 트래픽·쿼리 무게에 비례해 스파이크가 길어지는 구조적 문제.
- **제안**: TTL 지터(60s ± 랜덤 10s) 즉시 적용 + stale-while-revalidate 후속 검토. 검색은 "낡아도 예약 생성의 조건부 UPDATE가 DB 재고를 재확인"하는 설계(`design-decisions.md#search-cache`)라 신선도 트레이드오프 부담이 없다.

### M2. 캐시 미스마다 COUNT 쿼리가 동일 파생 테이블을 한 번 더 실행 — 미스 경로 DB 비용 2배

- **위치**: `infrastructure/property/PropertySearchJdbcRepository.kt`
- **내용**: `findPage`가 COUNT 쿼리를 항상 먼저 실행하고, 0이 아니면 본 SELECT를 다시 실행한다. 두 쿼리가 같은 (객실타입 × 일자) 조인·집계 파생 테이블을 공유하므로(`round5-search-optimization.md` § 2에도 명시), 캐시 미스 1회의 DB 비용이 단일 쿼리 웜 기준 ~130ms의 약 2배가 된다. 검색 비용의 85%가 이 파생 테이블(§ 4)인데 그것을 요청마다 두 번 계산하는 셈 — 부하 테스트 cold 경로 평균 1.37s(40 VU)와 정합한다.
- **제안**: MySQL 8 윈도 함수 `COUNT(*) OVER()`를 본 SELECT에 포함해 1회 실행으로 합치기(범위 밖 페이지의 빈 결과만 COUNT 폴백 처리), 또는 total 없이 `size+1` 조회로 hasNext만 제공하는 방식 재검토.

---

## 🟡 Minor (수정 권장)

### m1. wiki "찜 수는 캐시하지 않는다" 서술이 검색 페이지 캐시와 불일치

- **위치**: `wiki/accommodation/design-decisions.md#search-cache` ↔ `domain/property/PropertyInfo.kt`(`SearchItem.wishlistCount`)
- **내용**: wiki는 "찜 수는 캐시하지 않고 매 조회 시 DB에서 병합한다"고 한정 없이 서술하지만, 매 조회 병합은 **상세 경로만**이다. 검색 페이지 캐시에는 `SearchItem.wishlistCount` 값과 찜 수 기반 정렬 순서가 페이지와 함께 최대 60초 캐시된다. 코드 자체는 일관된 선택(페이지 통째 캐시)이므로, 문서에 "매 조회 병합은 상세 기준이며 검색 결과 내 찜 수는 페이지와 함께 최대 60초 낡을 수 있다"를 명시하는 쪽을 권장.

### m2. coupon 3개 파일의 `PageResult` import가 import 블록과 빈 줄로 분리됨

- **위치**: `domain/coupon/CouponService.kt`, `CouponRepository.kt`, `IssuedCouponRepository.kt`
- **내용**: `PageResult` 패키지 이동(`domain.coupon` → `domain.common`) 여파로 새 import가 기존 import 블록 위에 빈 줄로 분리된 채 삽입돼 있다. ktlint는 통과하지만 세 파일 모두에서 유일한 스타일 이탈. 빈 줄을 제거해 기존 블록에 정렬 병합만 하면 된다.

---

## ❓ Question (의도 확인 필요)

### Q1. 존재하지 않는 숙소 상세의 negative cache 부재

`PropertyFacade.getDetail`은 캐시 미스 → DB 조회 → 없으면 404 throw로 끝나 캐시에 아무것도 남지 않는다. 같은 무효 id를 반복 조회(봇·스캐너)하면 매번 DB에 도달한다. PK 룩업이라 저렴해 Minor로 올리지 않음 — 짧은 TTL의 "없음" 마킹(negative cache)을 둘지는 트래픽 성격에 대한 정책 판단.

### Q2. `PropertyRepository.save` / `RoomTypeRepository.save`는 프로덕션 호출부가 없음

도메인 포트의 `save`는 통합 테스트 픽스처 3곳에서만 사용된다(시드는 `Round5SeedRunner`가 JdbcTemplate로 직접 INSERT). 픽스처 편의를 위한 의도된 노출인지 확인 — 아니라면 포트에서 제거하고 testFixtures 쪽 유틸로 이동하는 것이 도메인 계약을 좁게 유지한다.

---

## ✅ 검증 통과 (의심 후 근거 확인으로 기각한 항목)

| 항목 | 판정 근거 |
|------|-----------|
| `orderBy` 정렬식 SQL 인젝션 | enum `when` 화이트리스트 — 사용자 입력 문자열이 SQL에 삽입되는 경로 없음 |
| `@Suppress("UNCHECKED_CAST")` RedisTemplate 캐스팅 | `RedisConfig`가 default/master 템플릿 모두 `RedisTemplate<String, String>` + `StringRedisSerializer`로 생성 → 런타임 안전 |
| Redis 장애 시 동작 | 모든 캐시 연산 `runCatching` + warn 로그 폴백, `PropertyFacadeCacheIntegrationTest`로 검증 |
| 캐시 키에 city 원문 포함 (`:` 포함 입력 시 키 충돌?) | 키 뒤 6개 세그먼트가 고정 포맷(날짜 2·정수 3·enum 1) — city에 `:`가 들어가면 세그먼트가 밀려 다른 유효 키와 문자열이 일치할 수 없음 → 충돌 불가 |
| 검색 파라미터 누락/타입 오류 | `MissingServletRequestParameterException`·`MethodArgumentTypeMismatchException` 핸들러 존재 → 400 정상 |
| 캐시 읽기 replica / 쓰기 master 비대칭 | 복제 지연 시 미스 1회 추가(DB 재조회)로 끝남 — 정합성 영향 없음 |
| `Round5SeedRunner` 루프 내 컬렉션 생성 | 1회성 시드 코드, chunked 배치 INSERT로 충분 — 성능 함정 룰 비적용 |
| ontology YAML 키 철자 | 추가된 키 전부 표준 키(`name`/`note`/`package`/`status`/`summary`/`to`/`type`) — 룰 그룹 2 통과 |

---

## 후속 작업 후보

- [x] M1: `SEARCH_TTL` 지터 적용 → 부하 테스트 재측정 완료. **지터 단독으로는 부족** — 개별 키의 만료 herd만으로 풀 고갈이 재현됐다(`round5-load-test.md` § 5). stale-while-revalidate 또는 single-flight가 후속 필요.
- [ ] M2: `COUNT(*) OVER()` 단일 쿼리 전환 검토 → `round5-search-optimization.md` 수치 갱신
- [ ] m1: `design-decisions.md#search-cache` 찜 수 서술에 한정어 추가
- [ ] m2: coupon 3개 파일 import 블록 정리
- [ ] Q1·Q2: 정책 결정 후 코드 반영 또는 ontology `notes`에 정책 기록
