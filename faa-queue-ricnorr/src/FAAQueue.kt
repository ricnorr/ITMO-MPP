import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val oldTail = tail.value // взяли хвост
            val enqIdx = oldTail.enqIdx.getAndAdd(1) // увеличили индекс
            if (enqIdx >= SEGMENT_SIZE) { // вылезли за сегмент
                val newTail = Segment(x) // создали новый
                if (oldTail.next.compareAndSet(null, newTail)) { // сюда может зайти только один поток
                    tail.compareAndSet(oldTail, newTail)
                    return
                } else { // поможем
                    val moveTail = oldTail.next.value
                    tail.compareAndSet(oldTail, moveTail!!)
                }
            } else {
                if (oldTail.elements[enqIdx].compareAndSet(null, x)) {
                    return
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val oldHead = head.value // голову
            val deqIdx = oldHead.deqIdx.getAndAdd(1)
            if (deqIdx >= SEGMENT_SIZE) {
                val newHead = oldHead.next.value ?: return null
                head.compareAndSet(oldHead, newHead)
            } else {
                val res = oldHead.elements[deqIdx].getAndSet(DONE)
                if (res != null) {
                    return res as T?
                }
            }
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val oldHead = head.value
                if (oldHead.isEmpty) {
                    if (oldHead.next.value == null) {
                        return true
                    }
                    head.compareAndSet(oldHead, oldHead.next.value!!)
                    continue
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx: AtomicInt = atomic(0) // index for the next enqueue operation
    val deqIdx: AtomicInt = atomic(0) // index for the next dequeue operation
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.value = 1
        elements[0].value = x
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE // TODO(вторая часть непонятна)

}

private val DONE = Any() // Marker for the "DONE" slot state; to avoid memory leaks
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

