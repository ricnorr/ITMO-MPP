package dijkstra

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.random.Random

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }


fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    val mQueue = MultiQueue(2 * workers)
    start.casDistance(start.distance, 0)
    mQueue.addElement(start)
    val onFinish = Phaser(workers + 1)
    repeat(workers) {
        thread {
            while (true) {
                val cur: Node = mQueue.getElement() ?: if (mQueue.valueCounter() > 0) {
                    continue
                } else {
                    break
                }
                val curDistance = cur.distance
                for (e in cur.outgoingEdges) {
                    while (true) {
                        val toDistance = e.to.distance
                        val newDistance = curDistance + e.weight
                        if (toDistance > newDistance) {
                            if (e.to.casDistance(toDistance, newDistance)) {
                                mQueue.addElement(e.to)
                            } else {
                                continue
                            }
                        }
                        break
                    }
                }
                mQueue.decCounter()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

class MultiQueue constructor(
    private val n: Int,
    private val queues: List<Queue<Node>>,
    private val random: Random = Random,
    private val locks: Array<ReentrantLock> = Array(n) { ReentrantLock() },
) {
    private val activeCounter: AtomicInt = atomic(0)

    constructor(n: Int) : this(n, List(n) { ArrayDeque() })

    fun decCounter() {
        activeCounter.decrementAndGet()
    }

    fun valueCounter(): Int = activeCounter.value

    fun getElement(): Node? {
        var i = 0
        var j = 0
        do {
            i = random.nextInt(0, n)
            j = random.nextInt(0, n)
        } while (i == j)
        locks[min(i, j)].withLock {
            locks[max(i, j)].withLock {
                val fQueue = queues[min(i, j)]
                val sQueue = queues[max(i, j)]
                if (fQueue.isEmpty() && sQueue.isNotEmpty()) {
                    return sQueue.poll()
                }
                if (fQueue.isEmpty() && sQueue.isEmpty()) {
                    return null
                }
                if (sQueue.isEmpty() && fQueue.isNotEmpty()) {
                    return fQueue.poll()
                }
                val fElement = fQueue.peek()
                val sElement = sQueue.peek()
                return if (NODE_DISTANCE_COMPARATOR.compare(fElement, sElement) < 0) {
                    fQueue.poll()
                    fElement
                } else {
                    sQueue.poll()
                    sElement
                }
            }
        }
    }

    fun addElement(node: Node) {
        val i = random.nextInt(0, n)
        activeCounter.incrementAndGet()
        locks[i].withLock {
            queues[i].add(node)
        }
    }
}