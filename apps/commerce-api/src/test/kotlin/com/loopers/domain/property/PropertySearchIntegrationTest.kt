package com.loopers.domain.property

import com.loopers.domain.accommodation.DailyRoomInventory
import com.loopers.domain.accommodation.DailyRoomRate
import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.wishlist.PropertyWishlistCount
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
import com.loopers.infrastructure.wishlist.PropertyWishlistCountJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class PropertySearchIntegrationTest @Autowired constructor(
    private val propertyService: PropertyService,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val propertyWishlistCountJpaRepository: PropertyWishlistCountJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val checkIn = LocalDate.of(2026, 5, 15)
    private val checkOut = LocalDate.of(2026, 5, 17) // 2박

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun condition(
        city: String = "서울",
        sort: PropertySortType = PropertySortType.PRICE_ASC,
        guestCount: Int = 2,
        page: Int = 0,
        size: Int = 20,
    ) = PropertySearchCondition(city, checkIn, checkOut, guestCount, sort, page, size)

    private fun property(name: String, city: String = "서울"): Property =
        propertyRepository.save(Property(name, city))

    private fun roomType(propertyId: Long, capacity: Int = 2, name: String = "스탠다드"): RoomType =
        roomTypeRepository.save(RoomType(propertyId, name, capacity))

    /** checkIn~checkOut 전 일자에 재고·요금을 넣어 "가용" 상태로 만든다. */
    private fun seedStay(roomTypeId: Long, nightlyAmount: Long = 50_000L, remaining: Int = 3) {
        var date = checkIn
        while (date < checkOut) {
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, date, remaining))
            dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, date, Money(nightlyAmount, "KRW")))
            date = date.plusDays(1)
        }
    }

    private fun wishlist(propertyId: Long, count: Long) {
        propertyWishlistCountJpaRepository.save(PropertyWishlistCount.of(propertyId, count))
    }

    @DisplayName("숙소를 검색할 때,")
    @Nested
    inner class Search {
        @DisplayName("다른 도시의 숙소는 제외된다.")
        @Test
        fun filtersByCity() {
            val seoul = property("서울숙소", "서울")
            val busan = property("부산숙소", "부산")
            seedStay(roomType(seoul.id).id)
            seedStay(roomType(busan.id).id)

            val result = propertyService.search(condition(city = "서울"))

            assertThat(result.content.map { it.propertyId }).containsExactly(seoul.id)
        }

        @DisplayName("인원을 수용할 객실 타입이 없는 숙소는 제외된다.")
        @Test
        fun filtersByCapacity() {
            val small = property("2인숙소")
            val large = property("4인숙소")
            seedStay(roomType(small.id, capacity = 2).id)
            seedStay(roomType(large.id, capacity = 4).id)

            val result = propertyService.search(condition(guestCount = 4))

            assertThat(result.content.map { it.propertyId }).containsExactly(large.id)
        }

        @DisplayName("기간 중 하루라도 재고가 없으면 제외된다.")
        @Test
        fun excludesSoldOutDate() {
            val soldOut = property("매진숙소")
            val available = property("가용숙소")
            seedStay(roomType(available.id).id)
            val rt = roomType(soldOut.id)
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(rt.id, checkIn, 3))
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(rt.id, checkIn.plusDays(1), 0))
            dailyRoomRateJpaRepository.save(DailyRoomRate(rt.id, checkIn, Money(50_000L, "KRW")))
            dailyRoomRateJpaRepository.save(DailyRoomRate(rt.id, checkIn.plusDays(1), Money(50_000L, "KRW")))

            val result = propertyService.search(condition())

            assertThat(result.content.map { it.propertyId }).containsExactly(available.id)
        }

        @DisplayName("기간 중 요금이 없는 날이 있으면 제외된다.")
        @Test
        fun excludesMissingRateDate() {
            val noRate = property("요금누락숙소")
            val rt = roomType(noRate.id)
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(rt.id, checkIn, 3))
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(rt.id, checkIn.plusDays(1), 3))
            dailyRoomRateJpaRepository.save(DailyRoomRate(rt.id, checkIn, Money(50_000L, "KRW")))

            val result = propertyService.search(condition())

            assertThat(result.content).isEmpty()
        }

        @DisplayName("총액은 기간 합이며, 숙소 내 최저가 객실 타입 기준이다.")
        @Test
        fun calculatesMinTotalAmount() {
            val target = property("두객실숙소")
            seedStay(roomType(target.id, name = "디럭스").id, nightlyAmount = 50_000L)
            seedStay(roomType(target.id, name = "스탠다드").id, nightlyAmount = 40_000L)

            val result = propertyService.search(condition())

            assertThat(result.content.single().minTotalAmount).isEqualTo(80_000L) // 40,000 × 2박
        }

        @DisplayName("가격 오름차순으로 정렬된다.")
        @Test
        fun sortsByPriceAsc() {
            val cheap = property("저가숙소")
            val mid = property("중가숙소")
            val expensive = property("고가숙소")
            seedStay(roomType(expensive.id).id, nightlyAmount = 60_000L)
            seedStay(roomType(cheap.id).id, nightlyAmount = 40_000L)
            seedStay(roomType(mid.id).id, nightlyAmount = 50_000L)

            val result = propertyService.search(condition(sort = PropertySortType.PRICE_ASC))

            assertThat(result.content.map { it.propertyId }).containsExactly(cheap.id, mid.id, expensive.id)
        }

        @DisplayName("찜 수 내림차순으로 정렬되며, 찜 수 행이 없는 숙소는 0으로 처리된다.")
        @Test
        fun sortsByWishlistDesc() {
            val few = property("찜적음")
            val many = property("찜많음")
            val none = property("찜없음")
            seedStay(roomType(few.id).id)
            seedStay(roomType(many.id).id)
            seedStay(roomType(none.id).id)
            wishlist(few.id, 5L)
            wishlist(many.id, 100L)

            val result = propertyService.search(condition(sort = PropertySortType.WISHLIST_DESC))

            assertAll(
                { assertThat(result.content.map { it.propertyId }).containsExactly(many.id, few.id, none.id) },
                { assertThat(result.content.last().wishlistCount).isEqualTo(0L) },
            )
        }

        @DisplayName("추천순은 찜수+가격 가중 스코어 내림차순이다.")
        @Test
        fun sortsByRecommended() {
            // best: 찜 많고 저렴 / popular: 찜 많고 비쌈 / cheap: 찜 없고 저렴 → 스코어 순서 best > popular > cheap
            val best = property("찜많고저렴")
            val popular = property("찜많고비쌈")
            val cheap = property("찜없고저렴")
            seedStay(roomType(best.id).id, nightlyAmount = 40_000L)
            seedStay(roomType(popular.id).id, nightlyAmount = 100_000L)
            seedStay(roomType(cheap.id).id, nightlyAmount = 40_000L)
            wishlist(best.id, 1_000L)
            wishlist(popular.id, 1_000L)

            val result = propertyService.search(condition(sort = PropertySortType.RECOMMENDED))

            assertThat(result.content.map { it.propertyId }).containsExactly(best.id, popular.id, cheap.id)
        }

        @DisplayName("페이지네이션이 적용된다.")
        @Test
        fun paginates() {
            val ids = (1..3).map { i ->
                val p = property("숙소$i")
                seedStay(roomType(p.id).id, nightlyAmount = 40_000L + i * 1_000L)
                p.id
            }

            val firstPage = propertyService.search(condition(size = 2, page = 0))
            val secondPage = propertyService.search(condition(size = 2, page = 1))

            assertAll(
                { assertThat(firstPage.totalElements).isEqualTo(3L) },
                { assertThat(firstPage.totalPages).isEqualTo(2) },
                { assertThat(firstPage.content.map { it.propertyId }).containsExactly(ids[0], ids[1]) },
                { assertThat(secondPage.content.map { it.propertyId }).containsExactly(ids[2]) },
            )
        }

        @DisplayName("범위 밖 페이지는 내용이 비어도 전체 건수를 반환한다.")
        @Test
        fun returnsTotalOnOutOfRangePage() {
            repeat(3) { i ->
                val p = property("숙소$i")
                seedStay(roomType(p.id).id)
            }

            val result = propertyService.search(condition(size = 2, page = 5))

            assertAll(
                { assertThat(result.content).isEmpty() },
                { assertThat(result.totalElements).isEqualTo(3L) },
                { assertThat(result.totalPages).isEqualTo(2) },
            )
        }
    }

    @DisplayName("숙소 상세를 조회할 때,")
    @Nested
    inner class GetDetail {
        @DisplayName("숙소와 객실 타입 목록을 반환한다.")
        @Test
        fun returnsDetail() {
            val target = property("상세숙소")
            roomType(target.id, name = "스탠다드")
            roomType(target.id, capacity = 4, name = "패밀리")

            val detail = propertyService.getDetail(target.id)

            assertAll(
                { assertThat(detail.property.name).isEqualTo("상세숙소") },
                { assertThat(detail.roomTypes).hasSize(2) },
            )
        }

        @DisplayName("존재하지 않으면 PROPERTY_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenAbsent() {
            val exception = assertThrows<CoreException> { propertyService.getDetail(999L) }

            assertThat(exception.errorType).isEqualTo(ErrorType.PROPERTY_NOT_FOUND)
        }

        @DisplayName("소프트 삭제된 숙소는 PROPERTY_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenDeleted() {
            val deleted = property("삭제된숙소")
            deleted.delete()
            propertyRepository.save(deleted)

            val exception = assertThrows<CoreException> { propertyService.getDetail(deleted.id) }

            assertThat(exception.errorType).isEqualTo(ErrorType.PROPERTY_NOT_FOUND)
        }
    }
}
