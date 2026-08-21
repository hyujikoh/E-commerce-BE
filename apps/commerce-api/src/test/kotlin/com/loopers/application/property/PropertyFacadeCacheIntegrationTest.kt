package com.loopers.application.property

import com.loopers.domain.accommodation.DailyRoomInventory
import com.loopers.domain.accommodation.DailyRoomRate
import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.property.Property
import com.loopers.domain.property.PropertyRepository
import com.loopers.domain.property.PropertySearchCondition
import com.loopers.domain.property.PropertySortType
import com.loopers.domain.property.RoomType
import com.loopers.domain.property.RoomTypeRepository
import com.loopers.domain.wishlist.WishlistService
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import javax.sql.DataSource

@SpringBootTest
class PropertyFacadeCacheIntegrationTest @Autowired constructor(
    private val propertyFacade: PropertyFacade,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val wishlistService: WishlistService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    dataSource: DataSource,
) {
    private val jdbcTemplate = JdbcTemplate(dataSource)

    private val checkIn = LocalDate.of(2026, 5, 15)
    private val checkOut = LocalDate.of(2026, 5, 17)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun seedAvailableProperty(name: String = "캐시숙소", city: String = "서울"): Pair<Property, RoomType> {
        val property = propertyRepository.save(Property(name, city))
        val roomType = roomTypeRepository.save(RoomType(property.id, "스탠다드", 2))
        var date = checkIn
        while (date < checkOut) {
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomType.id, date, 3))
            dailyRoomRateJpaRepository.save(DailyRoomRate(roomType.id, date, Money(50_000L, "KRW")))
            date = date.plusDays(1)
        }
        return property to roomType
    }

    private fun condition() =
        PropertySearchCondition("서울", checkIn, checkOut, 2, PropertySortType.PRICE_ASC, 0, 20)

    @DisplayName("상세 캐시는,")
    @Nested
    inner class DetailCache {
        @DisplayName("두 번째 조회부터 캐시를 사용한다 — DB 이름을 바꿔도 캐시된 기존 이름이 반환된다.")
        @Test
        fun servesFromCache_onSecondRead() {
            val (property, _) = seedAvailableProperty(name = "원래이름")
            propertyFacade.getDetail(property.id)
            jdbcTemplate.update("UPDATE property SET name = '바뀐이름' WHERE id = ?", property.id)

            val detail = propertyFacade.getDetail(property.id)

            assertThat(detail.name).isEqualTo("원래이름")
        }

        @DisplayName("찜 수는 캐시하지 않는다 — 캐시 적중 상태에서도 최신 찜 수가 반영된다.")
        @Test
        fun mergesFreshWishlistCount() {
            val (property, _) = seedAvailableProperty()
            val before = propertyFacade.getDetail(property.id)
            wishlistService.add(1L, property.id)

            val after = propertyFacade.getDetail(property.id)

            assertAll(
                { assertThat(before.wishlistCount).isEqualTo(0L) },
                { assertThat(after.wishlistCount).isEqualTo(1L) },
            )
        }

        @DisplayName("캐시가 비어 있어도(미스) 정상 조회된다.")
        @Test
        fun worksOnCacheMiss() {
            val (property, _) = seedAvailableProperty(name = "미스숙소")
            redisCleanUp.truncateAll()

            val detail = propertyFacade.getDetail(property.id)

            assertAll(
                { assertThat(detail.name).isEqualTo("미스숙소") },
                { assertThat(detail.roomTypes).hasSize(1) },
            )
        }
    }

    @DisplayName("검색 캐시는,")
    @Nested
    inner class SearchCache {
        @DisplayName("TTL 내 같은 조건 재검색은 캐시를 사용한다 — 재고를 0으로 바꿔도 결과가 유지된다.")
        @Test
        fun servesFromCache_withinTtl() {
            val (property, roomType) = seedAvailableProperty()
            propertyFacade.search(condition())
            jdbcTemplate.update("UPDATE daily_room_inventory SET remaining = 0 WHERE room_type_id = ?", roomType.id)

            val cached = propertyFacade.search(condition())

            assertThat(cached.items.map { it.propertyId }).containsExactly(property.id)
        }

        @DisplayName("캐시가 비면(미스) DB 기준 최신 결과를 반환한다.")
        @Test
        fun reflectsDb_onCacheMiss() {
            val (_, roomType) = seedAvailableProperty()
            propertyFacade.search(condition())
            jdbcTemplate.update("UPDATE daily_room_inventory SET remaining = 0 WHERE room_type_id = ?", roomType.id)
            redisCleanUp.truncateAll()

            val fresh = propertyFacade.search(condition())

            assertThat(fresh.items).isEmpty()
        }
    }
}
