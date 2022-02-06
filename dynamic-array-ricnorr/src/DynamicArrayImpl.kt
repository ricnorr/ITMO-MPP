import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class DynamicArrayImpl<E> : DynamicArray<E> {

    private val core = atomic(Core<Wrap<E>>(INITIAL_CAPACITY))
    private val sizeRef: AtomicInt = atomic(0)

    override fun get(index: Int): E {
        val size = sizeRef.value
        if (index >= size) {
            throw IllegalArgumentException("Index ${index} out of ${size} length")
        }
        val curCore = core.value
        val el = curCore.array[index].value
        return el?.data ?: throw IllegalStateException("WTF")
    }


    override fun put(index: Int, element: E) {
        while (true) {
            val size = sizeRef.value
            if (index >= size) {
                throw IllegalArgumentException("Index ${index} out of ${size} length")
            }
            val curCore = core.value
            val prevEl = curCore.array[index].value
            if (prevEl is N) {
                if (curCore.array[index].compareAndSet(prevEl, N(element))) {
                    return
                }
            } else {
                helpMove()
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val size = sizeRef.value
            if (size >= curCore.array.size) {
                moveTable(curCore)
            } else {
                if (curCore.array[size].compareAndSet(null, N(element))) {
                    sizeRef.incrementAndGet()
                    return
                }
            }
        }
    }

    fun helpMove() {
        val curCore = core.value
        val nextCore = curCore.nextCore.value
        if (nextCore != null) {
            for (i in 0 until curCore.array.size) {
                moveElement(curCore, nextCore, i)
            }
            core.compareAndSet(curCore, nextCore)
        }
    }

    private fun moveTable(prevCore: Core<Wrap<E>>) {
        val prevCapacity = prevCore.array.size

        val newCapacity = prevCapacity * 2
        val newCore = Core<Wrap<E>>(newCapacity)

        if (!prevCore.nextCore.compareAndSet(null, newCore)) {
            helpMove()
            return
        }
        for (i in 0 until prevCapacity) {
            moveElement(prevCore, newCore, i)
        }
        core.compareAndSet(prevCore, newCore)
    }

    private fun moveElement(prevCore: Core<Wrap<E>>, newCore: Core<Wrap<E>>, index: Int) {
        while (true) {
            val oldValue = prevCore.array[index].value ?: throw IllegalStateException("WTF")
            if (prevCore.array[index].compareAndSet(oldValue, I(oldValue.data))) {
                newCore.array[index].compareAndSet(null, N(oldValue.data))
                break
            }
        }

    }

    override val size: Int
        get() {
            return sizeRef.value
        }
}

class Core<E>(
    capacity: Int,
) {
    val nextCore: AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<E>(capacity)
}

interface Wrap<E> {
    val data: E
}

class N<E>(override val data: E) : Wrap<E>

class I<E>(override val data: E) : Wrap<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME