// Round 5 검색 API 부하 테스트 (k6)
//
// 실행 전제: 시드 데이터 적재 후, 앱을 시드 보존 모드로 기동
//   ./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local,seed'
//   ./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local --spring.jpa.hibernate.ddl-auto=none --spring.jpa.show-sql=false'
// 실행:
//   k6 run docs/perf/load/search-load.js
//
// 시나리오 2종 (순차 실행):
//   hot  — 인기 검색 12개 조합 반복 (검색 캐시 TTL 60초 내 재사용 → 캐시 적중 위주) + 상세 20%
//   cold — 도시·날짜·인원·정렬을 전부 랜덤화 (캐시 키 공간 ≫ 요청 수 → 사실상 전부 DB 경유) + 상세 20%
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

const SORTS = ['PRICE_ASC', 'WISHLIST_DESC', 'RECOMMENDED'];
const CITIES = ['서울', '부산', '제주', '인천', '대구', '대전', '광주', '수원', '강릉', '경주'];

// 인기 검색 조합 4개 × 정렬 3종 = 검색 캐시 키 12개
const HOT_QUERIES = [
  { city: '서울', checkIn: '2026-05-15', checkOut: '2026-05-17', guests: 2 },
  { city: '부산', checkIn: '2026-05-10', checkOut: '2026-05-11', guests: 4 },
  { city: '제주', checkIn: '2026-06-05', checkOut: '2026-06-08', guests: 2 },
  { city: '강릉', checkIn: '2026-06-12', checkOut: '2026-06-14', guests: 3 },
];

export const options = {
  scenarios: {
    hot: {
      executor: 'ramping-vus',
      exec: 'hot',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 30 },
        { duration: '50s', target: 100 },
        { duration: '60s', target: 100 },
        { duration: '10s', target: 0 },
      ],
    },
    cold: {
      executor: 'ramping-vus',
      exec: 'cold',
      startTime: '2m40s',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 40 },
        { duration: '60s', target: 40 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_failed{scenario:hot}': ['rate<0.01'],
    'http_req_failed{scenario:cold}': ['rate<0.01'],
    'http_req_duration{scenario:hot}': ['p(95)<300'],
    'http_req_duration{scenario:cold}': ['p(95)<1500'],
    // 시나리오별 처리량이 요약에 submetric 으로 표시되도록 등록
    'http_reqs{scenario:hot}': ['count>=0'],
    'http_reqs{scenario:cold}': ['count>=0'],
  },
};

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function searchUrl(q, sort, page) {
  return (
    `${BASE}/api/v1/properties?city=${encodeURIComponent(q.city)}` +
    `&checkIn=${q.checkIn}&checkOut=${q.checkOut}&guestCount=${q.guests}` +
    `&sort=${sort}&page=${page}&size=20`
  );
}

function detailUrl() {
  return `${BASE}/api/v1/properties/${randInt(1, 10000)}`;
}

function hit(url, name) {
  const r = http.get(url, { tags: { name } });
  check(r, { 'status 200': (res) => res.status === 200 });
}

export function hot() {
  if (Math.random() < 0.2) {
    hit(detailUrl(), 'detail');
    return;
  }
  const q = HOT_QUERIES[randInt(0, HOT_QUERIES.length - 1)];
  hit(searchUrl(q, SORTS[randInt(0, SORTS.length - 1)], 0), 'search-hot');
}

export function cold() {
  if (Math.random() < 0.2) {
    hit(detailUrl(), 'detail');
    return;
  }
  const nights = randInt(1, 5);
  const offset = randInt(0, 122 - nights); // 시드 범위 2026-05-01 ~ 08-31 내로 제한
  const base = new Date('2026-05-01T00:00:00Z');
  const checkIn = new Date(base.getTime() + offset * 86400000).toISOString().slice(0, 10);
  const checkOut = new Date(base.getTime() + (offset + nights) * 86400000).toISOString().slice(0, 10);
  const q = {
    city: CITIES[randInt(0, CITIES.length - 1)],
    checkIn,
    checkOut,
    guests: randInt(1, 6),
  };
  hit(searchUrl(q, SORTS[randInt(0, SORTS.length - 1)], randInt(0, 2)), 'search-cold');
}
