package com.loopers.support.seed

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.system.exitProcess

/**
 * Round 5 검색 성능 벤치마크용 시드 데이터 생성기.
 *
 * 실행: ./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local,seed'
 * 시드 완료 후 프로세스를 스스로 종료한다. 데이터는 앱 종료 후에도 유지되지만,
 * local 프로필은 ddl-auto=create 라서 **앱을 다시 시작하면 초기화**된다 — 벤치마크는 앱 종료 상태에서 mysql 로 수행한다.
 *
 * 규모: 숙소 1만(도시 10개 × 1,000) / 객실 타입 5만(숙소당 5) /
 *      일자별 재고·요금 각 615만 행(객실 타입 5만 × 123일, 2026-05-01 ~ 2026-08-31)
 *
 * 분포는 난수 없이 결정적으로 만든다 — 재실행해도 같은 데이터가 나와 벤치마크를 재현할 수 있다.
 * - 재고: (roomTypeId*31 + dayIndex*17) % 7 로 0~6 순환(약 1/7이 매진), 성수기(7/15~8/15)는 2 차감해 매진 비율 확대
 * - 요금: 기본가(객실 타입별 4만~9.9만) + 수용 인원 가산, 주말(금·토) +30%, 성수기 +50%
 * - 찜 수: 숙소 1%가 5,000+, 9%가 500+, 나머지는 0~49 (멱분포 근사, 정렬 벤치마크용)
 */
@Profile("seed")
@Component
class Round5SeedRunner(
    dataSource: DataSource,
    private val context: ConfigurableApplicationContext,
) : CommandLineRunner {
    private val jdbcTemplate = JdbcTemplate(dataSource)

    override fun run(vararg args: String?) {
        val existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM property", Long::class.java) ?: 0L
        if (existing > 0) {
            logger.info("property {}건이 이미 존재하여 시드를 건너뛴다.", existing)
            exitProcess(SpringApplication.exit(context))
        }

        val startedAt = System.currentTimeMillis()
        seedProperties()
        seedRoomTypes()
        seedWishlistCounts()
        seedDailyRows()
        logger.info("시드 완료 ({}초). 앱을 종료한다.", (System.currentTimeMillis() - startedAt) / 1000)
        exitProcess(SpringApplication.exit(context))
    }

    private fun seedProperties() {
        val sql = "INSERT INTO property (id, name, city, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))"
        (1..PROPERTY_COUNT).chunked(PROPERTY_CHUNK).forEach { chunk ->
            jdbcTemplate.batchUpdate(
                sql,
                chunk.map { p -> arrayOf<Any>(p, "숙소-$p", CITIES[(p - 1) % CITIES.size]) },
            )
        }
        logger.info("property {}건 시드 완료", PROPERTY_COUNT)
    }

    private fun seedRoomTypes() {
        val sql =
            "INSERT INTO room_type (id, property_id, name, capacity, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(6), NOW(6))"
        (1..PROPERTY_COUNT).chunked(PROPERTY_CHUNK).forEach { chunk ->
            val rows = chunk.flatMap { p ->
                CAPACITIES.mapIndexed { k, capacity ->
                    arrayOf<Any>(roomTypeId(p, k), p, "객실타입-${k + 1}", capacity)
                }
            }
            jdbcTemplate.batchUpdate(sql, rows)
        }
        logger.info("room_type {}건 시드 완료", PROPERTY_COUNT * CAPACITIES.size)
    }

    private fun seedWishlistCounts() {
        val sql = "INSERT INTO property_wishlist_count (property_id, wishlist_count, created_at, updated_at)" +
            " VALUES (?, ?, NOW(6), NOW(6))"
        (1..PROPERTY_COUNT).chunked(PROPERTY_CHUNK).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { p -> arrayOf<Any>(p, wishlistCountOf(p.toLong())) })
        }
        logger.info("property_wishlist_count {}건 시드 완료", PROPERTY_COUNT)
    }

    private fun seedDailyRows() {
        val inventorySql = "INSERT INTO daily_room_inventory (room_type_id, date, remaining, created_at, updated_at)" +
            " VALUES (?, ?, ?, NOW(6), NOW(6))"
        val rateSql = "INSERT INTO daily_room_rate (room_type_id, date, amount, currency, created_at, updated_at)" +
            " VALUES (?, ?, ?, 'KRW', NOW(6), NOW(6))"
        val roomTypeCount = PROPERTY_COUNT * CAPACITIES.size
        var seeded = 0

        (1..roomTypeCount).chunked(ROOM_TYPE_CHUNK).forEach { chunk ->
            val inventoryRows = ArrayList<Array<Any>>(chunk.size * DAY_COUNT)
            val rateRows = ArrayList<Array<Any>>(chunk.size * DAY_COUNT)
            chunk.forEach { rt ->
                (0 until DAY_COUNT).forEach { dayIndex ->
                    val date = START_DATE.plusDays(dayIndex.toLong())
                    inventoryRows.add(arrayOf(rt, date, remainingOf(rt, dayIndex, date)))
                    rateRows.add(arrayOf(rt, date, amountOf(rt, date)))
                }
            }
            jdbcTemplate.batchUpdate(inventorySql, inventoryRows)
            jdbcTemplate.batchUpdate(rateSql, rateRows)
            seeded += chunk.size
            if (seeded % 5_000 == 0) {
                logger.info("일자별 재고·요금 시드 진행: 객실 타입 {}/{}", seeded, roomTypeCount)
            }
        }
        logger.info("daily_room_inventory / daily_room_rate 각 {}건 시드 완료", roomTypeCount * DAY_COUNT)
    }

    private fun roomTypeId(propertyId: Int, k: Int): Int = (propertyId - 1) * CAPACITIES.size + k + 1

    private fun wishlistCountOf(propertyId: Long): Long = when {
        propertyId % 100 == 0L -> 5_000L + propertyId % 1_000
        propertyId % 10 == 0L -> 500L + propertyId % 100
        else -> propertyId % 50
    }

    private fun remainingOf(roomTypeId: Int, dayIndex: Int, date: LocalDate): Int {
        val base = (roomTypeId * 31 + dayIndex * 17) % 7
        return if (isPeak(date)) maxOf(base - 2, 0) else base
    }

    private fun amountOf(roomTypeId: Int, date: LocalDate): Long {
        val capacity = CAPACITIES[(roomTypeId - 1) % CAPACITIES.size]
        var amount = (40_000 + (roomTypeId % 60) * 1_000 + capacity * 5_000).toDouble()
        if (date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY) {
            amount *= 1.3
        }
        if (isPeak(date)) {
            amount *= 1.5
        }
        return amount.toLong()
    }

    private fun isPeak(date: LocalDate): Boolean = !date.isBefore(PEAK_START) && !date.isAfter(PEAK_END)

    companion object {
        private val logger = LoggerFactory.getLogger(Round5SeedRunner::class.java)

        private val CITIES = listOf("서울", "부산", "제주", "인천", "대구", "대전", "광주", "수원", "강릉", "경주")
        private const val PROPERTY_COUNT = 10_000
        private val CAPACITIES = intArrayOf(2, 2, 3, 4, 6)

        private val START_DATE: LocalDate = LocalDate.of(2026, 5, 1)
        private const val DAY_COUNT = 123
        private val PEAK_START: LocalDate = LocalDate.of(2026, 7, 15)
        private val PEAK_END: LocalDate = LocalDate.of(2026, 8, 15)

        private const val PROPERTY_CHUNK = 1_000

        /** 250 객실 타입 × 123일 = 30,750행 단위 배치 (rewriteBatchedStatements 로 멀티로우 INSERT 재작성) */
        private const val ROOM_TYPE_CHUNK = 250
    }
}
