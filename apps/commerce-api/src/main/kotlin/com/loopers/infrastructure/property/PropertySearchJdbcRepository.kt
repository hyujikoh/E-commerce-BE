package com.loopers.infrastructure.property

import com.loopers.domain.common.PageResult
import com.loopers.domain.property.PropertySearchCondition
import com.loopers.domain.property.PropertySearchRepository
import com.loopers.domain.property.PropertySearchRow
import com.loopers.domain.property.PropertySortType
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * 숙소 검색 native SQL 구현.
 *
 * JPQL 대신 native 를 쓰는 이유 — 가용 판정(기간 내 전 일자 요금·재고 존재)이
 * "일자별 조인 후 GROUP BY ... HAVING COUNT(*) = 박수" 형태의 집계라 JPQL 로는 파생 테이블을 표현할 수 없고,
 * EXPLAIN 으로 실행 계획을 직접 검증해야 하는 성능 슬라이스이기 때문이다.
 *
 * 정렬 기준·인덱스 선정 근거와 EXPLAIN 전후 비교는 docs/perf/round5-search-optimization.md 참조.
 */
@Component
class PropertySearchJdbcRepository(
    dataSource: DataSource,
) : PropertySearchRepository {
    private val jdbcTemplate = NamedParameterJdbcTemplate(dataSource)

    override fun search(condition: PropertySearchCondition): PageResult<PropertySearchRow> {
        val params = MapSqlParameterSource()
            .addValue("city", condition.city)
            .addValue("checkIn", condition.checkIn)
            .addValue("checkOut", condition.checkOut)
            .addValue("guestCount", condition.guestCount)
            .addValue("nights", condition.nights)
            .addValue("limit", condition.size)
            .addValue("offset", condition.page.toLong() * condition.size)

        val sql = "$SELECT_SQL ORDER BY ${orderBy(condition.sort)} LIMIT :limit OFFSET :offset"
        var totalElements = 0L
        val content = jdbcTemplate.query(sql, params) { rs, _ ->
            totalElements = rs.getLong("total_count")
            PropertySearchRow(
                propertyId = rs.getLong("id"),
                name = rs.getString("name"),
                city = rs.getString("city"),
                wishlistCount = rs.getLong("wishlist_count"),
                minTotalAmount = rs.getLong("min_total_amount"),
            )
        }
        // 빈 결과는 "조건 일치 0건"과 "범위 밖 페이지" 두 경우가 있어 이때만 COUNT 를 별도 실행한다.
        if (content.isEmpty()) {
            totalElements = jdbcTemplate.queryForObject(COUNT_SQL, params, Long::class.java) ?: 0L
        }
        val totalPages = ((totalElements + condition.size - 1) / condition.size).toInt()
        return PageResult(content, condition.page, condition.size, totalElements, totalPages)
    }

    private fun orderBy(sort: PropertySortType): String = when (sort) {
        PropertySortType.PRICE_ASC -> "s.min_total_amount ASC, p.id ASC"
        PropertySortType.WISHLIST_DESC -> "COALESCE(pwc.wishlist_count, 0) DESC, p.id ASC"
        PropertySortType.RECOMMENDED ->
            "($WISHLIST_WEIGHT * LN(1 + COALESCE(pwc.wishlist_count, 0))" +
                " - $PRICE_WEIGHT * LN(GREATEST(s.min_total_amount, 1))) DESC, p.id ASC"
    }

    companion object {
        /** 추천순 가중치. 찜수·가격 모두 멱분포라 LN 스케일 후 가중한다(PropertySortType KDoc 참조). */
        private const val WISHLIST_WEIGHT = 0.7
        private const val PRICE_WEIGHT = 0.3

        /**
         * 가용 숙소 판정 파생 테이블: 객실 타입별로 기간 내 (요금 존재 ∧ 재고>0)인 일자를 세어
         * 박수와 일치하는 것만 남기고, 숙소 단위로 최저 총액을 집계한다.
         */
        private val AVAILABLE_PER_PROPERTY = """
            SELECT t.property_id, MIN(t.total_amount) AS min_total_amount
            FROM (
                SELECT rt.property_id AS property_id, SUM(dr.amount) AS total_amount
                FROM room_type rt
                JOIN property p
                  ON p.id = rt.property_id AND p.city = :city AND p.deleted_at IS NULL
                JOIN daily_room_rate dr
                  ON dr.room_type_id = rt.id AND dr.date >= :checkIn AND dr.date < :checkOut
                JOIN daily_room_inventory di
                  ON di.room_type_id = rt.id AND di.date = dr.date AND di.remaining > 0
                WHERE rt.capacity >= :guestCount AND rt.deleted_at IS NULL
                GROUP BY rt.id, rt.property_id
                HAVING COUNT(*) = :nights
            ) t
            GROUP BY t.property_id
        """.trimIndent()

        /** 파생 테이블이 검색 비용의 대부분이라 총 건수는 COUNT(*) OVER() 윈도 함수로 본 쿼리에서 함께 얻는다. */
        private val SELECT_SQL = """
            SELECT p.id, p.name, p.city,
                   COALESCE(pwc.wishlist_count, 0) AS wishlist_count,
                   s.min_total_amount,
                   COUNT(*) OVER() AS total_count
            FROM ( $AVAILABLE_PER_PROPERTY ) s
            JOIN property p ON p.id = s.property_id
            LEFT JOIN property_wishlist_count pwc ON pwc.property_id = s.property_id
        """.trimIndent()

        /** 본 쿼리가 0행일 때(조건 일치 0건 vs 범위 밖 페이지 구분)만 쓰는 폴백. */
        private val COUNT_SQL = "SELECT COUNT(*) FROM ( $AVAILABLE_PER_PROPERTY ) s"
    }
}
