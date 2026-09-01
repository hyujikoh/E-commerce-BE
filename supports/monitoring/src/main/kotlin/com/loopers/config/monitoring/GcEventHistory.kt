package com.loopers.config.monitoring

/**
 * 최근 GC 이벤트를 보관하는 고정 용량 링버퍼.
 * 쓰기(JMX notification 스레드, GC당 1회)와 읽기(HTTP 스레드, 수동 조회)가 모두
 * 저빈도이므로 동기화 + 방어적 복사로 충분하다.
 */
class GcEventHistory(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    companion object {
        const val DEFAULT_CAPACITY = 50
    }

    private val buffer = ArrayDeque<GcEvent>(capacity)

    @Synchronized
    fun add(event: GcEvent) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(event)
    }

    /** 최신 이벤트부터 순서로 복사본을 반환한다. */
    @Synchronized
    fun snapshot(): List<GcEvent> = buffer.reversed()
}
