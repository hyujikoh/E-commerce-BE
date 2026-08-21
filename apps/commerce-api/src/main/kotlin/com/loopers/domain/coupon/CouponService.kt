package com.loopers.domain.coupon

import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 쿠폰 도메인 서비스. 템플릿 관리(admin), 발급/조회(user), 사용 처리(예약 흐름 합류)를 담당한다.
 *
 * [use] 는 예약 트랜잭션(REQUIRED)에 합류한다 — 쿠폰 사용·재고 차감·예약 저장이 한 트랜잭션으로 묶여
 * 하나라도 실패하면 함께 롤백된다. 단일 사용 보장은 원자적 UPDATE(markUsedIfAvailable)가 책임진다.
 */
@Component
class CouponService(
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {
    // ── admin: 템플릿 ──

    @Transactional
    fun createTemplate(
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
    ): Coupon = couponRepository.save(Coupon.create(name, type, value, minOrderAmount, expiredAt))

    @Transactional(readOnly = true)
    fun getTemplate(id: Long): Coupon =
        couponRepository.find(id) ?: throw CoreException(ErrorType.COUPON_NOT_FOUND)

    @Transactional(readOnly = true)
    fun listTemplates(page: Int, size: Int): PageResult<Coupon> = couponRepository.findPage(page, size)

    @Transactional
    fun updateTemplate(
        id: Long,
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
    ): Coupon {
        val coupon = getTemplate(id)
        coupon.update(name, type, value, minOrderAmount, expiredAt)
        return coupon
    }

    @Transactional
    fun deleteTemplate(id: Long) {
        val coupon = getTemplate(id)
        coupon.delete()
    }

    @Transactional(readOnly = true)
    fun listIssues(couponId: Long, page: Int, size: Int): PageResult<IssuedCoupon> {
        getTemplate(couponId) // 존재하지 않으면 COUPON_NOT_FOUND
        return issuedCouponRepository.findPageByCouponId(couponId, page, size)
    }

    // ── user: 발급/조회 ──

    @Transactional
    fun issue(userId: Long, templateId: Long, now: ZonedDateTime = ZonedDateTime.now()): IssuedCoupon {
        val coupon = getTemplate(templateId)
        if (coupon.isExpired(now)) {
            throw CoreException(ErrorType.COUPON_EXPIRED)
        }
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, templateId)) {
            throw CoreException(ErrorType.COUPON_ALREADY_ISSUED)
        }
        // UNIQUE(user_id, coupon_id) 가 동시 발급의 최종 backstop. existsBy 는 친절한 사전 검증.
        // 두 동시 요청이 existsBy 를 함께 통과하면 한쪽이 UNIQUE 위반으로 실패 — 도메인 에러로 변환해
        // 클라이언트가 사전 검증 실패와 동일한 COUPON_ALREADY_ISSUED 를 받도록 한다.
        return try {
            issuedCouponRepository.save(IssuedCoupon.issue(userId, templateId))
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.COUPON_ALREADY_ISSUED)
        }
    }

    @Transactional(readOnly = true)
    fun getMyCoupons(userId: Long, now: ZonedDateTime = ZonedDateTime.now()): List<OwnedCoupon> {
        val issued = issuedCouponRepository.findByUserId(userId)
        if (issued.isEmpty()) return emptyList()
        val couponsById = couponRepository.findAllByIds(issued.map { it.couponId }.distinct())
            .associateBy { it.id }
        // 템플릿이 소프트 삭제된 발급분은 노출하지 않는다.
        return issued.mapNotNull { ic -> couponsById[ic.couponId]?.let { OwnedCoupon.of(ic, it, now) } }
    }

    // ── 예약 흐름: 사용 처리 ──

    /**
     * 쿠폰을 사용 처리하고 할인액을 반환한다. (예약 트랜잭션에 합류)
     * 검증 순서: 존재 → 소유 → 템플릿 유효성(만료/최소금액)으로 할인 계산 → 원자적 사용 전이.
     * 사용 전이가 0행이면(이미 사용됨) COUPON_ALREADY_USED 로 전체 트랜잭션을 롤백시킨다.
     */
    @Transactional
    fun use(userId: Long, issuedCouponId: Long, orderAmount: Money, now: ZonedDateTime = ZonedDateTime.now()): Money {
        val issued = issuedCouponRepository.find(issuedCouponId)
            ?: throw CoreException(ErrorType.COUPON_NOT_FOUND)
        if (issued.userId != userId) {
            throw CoreException(ErrorType.COUPON_NOT_OWNED)
        }
        val coupon = couponRepository.find(issued.couponId)
            ?: throw CoreException(ErrorType.COUPON_NOT_FOUND)

        val discount = coupon.calculateDiscount(orderAmount, now)

        val affected = issuedCouponRepository.markUsedIfAvailable(issuedCouponId, now)
        if (affected == 0) {
            throw CoreException(ErrorType.COUPON_ALREADY_USED)
        }
        return discount
    }

    /**
     * 사용 처리된 쿠폰을 복구한다(USED → AVAILABLE). 예약 취소/만료 트랜잭션에 합류한다.
     * Reservation 상태 전이 가드가 중복 취소를 차단하므로 정상 경로에서 0행은 발생하지 않지만,
     * 복구는 멱등으로 두어 재시도에 안전하게 한다.
     */
    @Transactional
    fun restore(issuedCouponId: Long) {
        issuedCouponRepository.restoreIfUsed(issuedCouponId)
    }
}
