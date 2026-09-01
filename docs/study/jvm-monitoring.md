# JVM 모니터링: GC 이벤트 수집과 non-heap 메모리 관측

## 배경

week5 부하 테스트(`docs/perf/round5-load-test.md`, week5 브랜치)에서 TTL 지터 적용 전후 처리량을 비교할 때, JVM 웜업과 GC 동작 차이가 측정 결과를 오염시켰지만 이를 확인할 관측 수단이 없었다. `supports:monitoring` 모듈에 GC 이벤트 수집기와 커스텀 엔드포인트를 추가하고, Grafana JVM 대시보드를 프로비저닝해 이 갭을 메운다.

## JVM 메모리 구조 복습

`ManagementFactory.getMemoryPoolMXBeans()`가 노출하는 풀 기준 (JDK 21 + G1 기준):

| 영역 | 풀 | 용도 |
|------|-----|------|
| Heap | G1 Eden Space | 새 객체 할당. minor GC 대상 |
| Heap | G1 Survivor Space | minor GC 생존 객체 |
| Heap | G1 Old Gen | 승격(promotion)된 장수명 객체 |
| Non-Heap | Metaspace | 클래스 메타데이터. 클래스 로딩량에 비례해 증가 |
| Non-Heap | Compressed Class Space | 압축 클래스 포인터(-XX:+UseCompressedClassPointers) 전용 영역 |
| Non-Heap | CodeHeap 'non-nmethods' | JIT 컴파일러 내부 버퍼 |
| Non-Heap | CodeHeap 'profiled nmethods' | C1(티어 2~3) 컴파일 코드 |
| Non-Heap | CodeHeap 'non-profiled nmethods' | C2(티어 4) 컴파일 코드 |

non-heap에서 봐야 할 신호:

- **Metaspace 증가 추세**: 동적 프록시/리플렉션/클래스로더 누수 시 계속 증가한다. `MaxMetaspaceSize` 미설정이면 OS 메모리를 잠식하다 OOM. GC cause가 `Metadata GC Threshold`로 찍히면 Metaspace 부족이 GC를 유발했다는 직접 신호다.
- **CodeHeap**: JIT 웜업 동안 증가하다 수렴한다. 부하 테스트 초반 처리량이 낮은 이유(인터프리터 실행 → C1 → C2 승격)를 여기서 확인할 수 있다. 단, `gradlew bootRun`은 빠른 기동을 위해 `-XX:TieredStopAtLevel=1`(C1까지만)을 적용하므로 세그먼트가 합쳐진 `CodeCache` 단일 풀로 보인다. 3종 분리는 jar 직접 실행 시 관찰된다.
- **로드된 클래스 수**(`jvm_classes_loaded_classes`): Metaspace 증가와 함께 보면 클래스 누수인지 판별 가능.

## GC notification 메커니즘

`GarbageCollectorMXBean`은 `NotificationEmitter`를 구현하며, GC가 끝날 때마다 JMX notification을 발행한다. `com.sun.management.GarbageCollectionNotificationInfo`(JDK 21에서 `jdk.management` 모듈이 정식 export — `--add-exports` 불필요)로 파싱하면 GC 1회 단위의 상세 정보를 얻는다:

- `gcName` / `gcAction` / `gcCause`: 어떤 컬렉터가, 어떤 동작을, 왜 (예: `G1 Young Generation` / `end of minor GC` / `G1 Evacuation Pause`)
- `gcInfo.duration`: pause 시간
- `gcInfo.memoryUsageBeforeGc` / `AfterGc`: **풀별** 전후 사용량 → GC 1회당 회수량, Old 승격량을 풀 단위로 계산 가능

폴링(주기적 `collectionCount` 차분)과 달리 이벤트 단위 정밀 수집이 가능하다는 것이 핵심 차이다. 리스너는 JMX notification dispatcher 스레드에서 실행되므로 핸들러는 가볍게 유지하고, 스프링 빈 소멸 시 `removeNotificationListener`로 해제한다 (`GcEventCollector`의 `@PostConstruct`/`@PreDestroy`).

## 커스텀 메트릭 설계: 기본 바인더와의 갭

Spring Boot actuator가 자동 등록하는 `JvmMemoryMetrics`/`JvmGcMetrics`와 중복을 피하고, 갭만 커스텀으로 채웠다.

| 관심사 | 기본 제공 | 갭 → 커스텀 |
|--------|-----------|--------------|
| 풀별 현재 사용량 (non-heap 포함) | `jvm_memory_used_bytes{area, id}` | 없음 (기본으로 충분) |
| GC pause (gc/action/cause 분해) | `jvm_gc_pause_seconds` | 없음 (기본으로 충분) — **pause 커스텀 메트릭을 만들지 않은 이유** |
| 할당률/승격률 | `jvm_gc_memory_allocated_bytes_total`, `jvm_gc_memory_promoted_bytes_total` | 없음 |
| GC 1회당 heap 회수량 (cause별) | 없음 | `jvm_gc_reclaimed_bytes` (DistributionSummary, 태그 gc/cause). `_count`=GC 횟수, `_sum`=총 회수량, `_max`=이벤트당 최대 |
| GC 직후 풀별 사용량 (live set 근사) | `jvm_memory_usage_after_gc_percent` (장수명 풀 % 하나뿐) | `jvm_gc_pool_used_after_bytes` (Gauge, 태그 pool) — Old Gen의 GC 직후 바닥값이 실제 살아있는 객체 크기의 근사치 |
| GC 이벤트 이력 (개별 이벤트 상세) | 없음 | `/actuator/jvm` 엔드포인트의 `recentGcEvents` (링버퍼 50건) |

태그 카디널리티: gc 이름 ≤3종 × cause 15종 미만으로 유한 소집합이라 안전하다. 임의 문자열(사용자 입력 등)을 태그로 쓰면 시계열이 폭발하므로 금지.

Metaspace 임계 경고는 별도 스케줄러 없이 GC 이벤트 처리에 편승한다: cause가 `Metadata GC Threshold`이거나, `MaxMetaspaceSize`가 설정된 환경에서 사용률 90% 이상이면 warn 로그를 남긴다.

## 체크 방법: /actuator/jvm 엔드포인트

관리 포트(8081)의 커스텀 엔드포인트로 현재 상태를 즉석 확인한다:

```bash
curl -s localhost:8081/actuator/jvm | jq .
```

응답 구성: `heap`/`nonHeap` 요약, `pools`(풀별 init/used/committed/max/usedRatio — max 미설정 풀은 usedRatio가 null), `gc`(컬렉터별 누적 횟수/시간), `recentGcEvents`(최근 GC 50건: cause, pause, 회수량, 풀별 전후 변화).

## 대시보드 읽는 법 (Grafana `JVM Monitoring`)

- **Heap Used vs Max가 톱니파**를 그리는 것이 정상. 톱니 바닥(= GC 직후 사용량, 커스텀 패널의 Old Gen 값)이 계속 상승하면 누수 의심.
- **GC 빈도 + 평균 pause**: 빈도가 높아지고 pause가 길어지면 heap 압박. cause별로 나뉘므로 `G1 Evacuation Pause`(정상 young GC)와 `G1 Humongous Allocation`(거대 객체), `Metadata GC Threshold`(Metaspace 부족)를 구분할 수 있다.
- **초당 회수량 vs 할당률**: 할당률 ≈ 회수율이면 정상 순환. 할당률만 높고 승격률이 함께 오르면 객체가 Old로 새는 중.
- **Non-Heap 패널**: Metaspace는 기동 후 수렴해야 정상. CodeHeap 증가는 JIT 웜업 — 부하 테스트 초반 구간 해석에 사용.

## 로컬 검증 절차

```bash
docker compose -f docker/infra-compose.yml up -d        # MySQL/Redis
docker compose -f docker/monitoring-compose.yml up -d   # Prometheus(9090) + Grafana(3000)
./gradlew :apps:commerce-api:bootRun

curl -s localhost:8081/actuator/jvm | jq .
curl -s localhost:8081/actuator/prometheus | grep -E "jvm_gc_reclaimed|jvm_gc_pool_used_after"

# GC 강제 유발 후 대시보드/엔드포인트 변화 확인
jcmd $(jcmd | grep CommerceApiApplication | cut -d' ' -f1) GC.run
```

Grafana는 http://localhost:3000 (admin/admin) → `JVM Monitoring` 대시보드가 프로비저닝으로 자동 등록된다.
