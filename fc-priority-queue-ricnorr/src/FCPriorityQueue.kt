import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val field: AtomicArray<Wrap?> = atomicArrayOfNulls<Wrap?>(INIT_SIZE)
    private val lock = ReentrantLock()
    private val random = Random()

    companion object {
        val INIT_SIZE = 64
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return ((handleOp(Poll()) as Val<*>).el as E?)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return ((handleOp(Peek()) as Val<*>).el as E?)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        handleOp(Add(element))
    }


    private fun handleOp(action: Wrap): Result {
        if (lock.tryLock()) {
            return underLock {
                combine(action)
            }
        } else {
            var randIndex: Int
            while (true) {
                randIndex = random.nextInt(INIT_SIZE - 1)
                if (field[randIndex].compareAndSet(null, action)) {
                    break
                }
            }
            while (true) {
                if (field[randIndex].value is Result) {
                    val res = field[randIndex].value
                    field[randIndex].value = null
                    return (res as Result?)!!
                }
                if (lock.tryLock()) {
                    return underLock {
                        if (field[randIndex].value is Result) {
                            val res = field[randIndex].value
                            field[randIndex].value = null
                            return@underLock (res as Result?)!!
                        }
                        field[randIndex].value = null
                        return@underLock combine(action)
                    }
                }
            }
        }
    }


    private fun combine(action: Wrap): Result {
        for (i in 0 until field.size) {
            val oldVal = field[i].value
            if (oldVal == null || oldVal is Result) {
                continue
            }
            field[i].value = commitAction(field[i].value)
        }
        return commitAction(action)
    }


    private fun commitAction(ref: Wrap?): Result {
        return when (ref) {
            is Poll -> {
                Val(q.poll())
            }
            is Peek -> {
                Val(q.peek())
            }
            is Add<*> -> {
                q.add(ref.el as E)
                NoVal()
            }
            else -> throw IllegalStateException()
        }
    }

    private fun underLock(action: () -> Result): Result {
        val res: Result
        try {
            res = action.invoke()
        } catch (ex: Exception) {
            lock.unlock()
            throw ex
        }
        lock.unlock()
        return res
    }

    open class Wrap
    class Poll : Wrap()
    class Peek : Wrap()
    class Add<E>(val el: E) : Wrap()
    open class Result : Wrap()
    class NoVal : Result()
    class Val<E>(val el: E?) : Result()
}
