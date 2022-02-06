import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 */
class IntIntHashMap {
    private val core: AtomicRef<Core?> = atomic(Core(INITIAL_CAPACITY))

    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        while (true) {
            val oldCore = core.value
            val oldValue = oldCore!!.getInternal(key)
            if (oldValue != NEEDS_REHASH) return toValue(oldValue)
            core.compareAndSet(oldCore, oldCore.rehash())
        }
    }

    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val oldCore = core.value
            val oldValue = oldCore!!.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            core.compareAndSet(oldCore, oldCore.rehash())
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val flag: AtomicBoolean = atomic(true)
        val map: AtomicIntArray = AtomicIntArray(2 * capacity)
        val shift: Int
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val mapKey = map[index].value
                if (mapKey == key) {
                    val oldValue = map[index + 1].value
                    return if (oldValue < 0) {
                        return NEEDS_REHASH
                    } else {
                        oldValue
                    }
                }
                if (mapKey == NULL_KEY) return NULL_VALUE
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var keyIndex = index(key)
            var probes = 0
            while (true) {
                val mapKey = map[keyIndex].value
                val mapValue = map[keyIndex + 1].value
                when (mapKey) {
                    key -> {
                        if (mapValue < 0) {
                            return NEEDS_REHASH
                        }
                        if (map[keyIndex + 1].compareAndSet(mapValue, value)) {
                            return mapValue
                        }
                        continue
                    }
                    NULL_KEY -> {
                        if (mapValue < 0) {
                            return NEEDS_REHASH
                        }
                        if (value == DEL_VALUE) {
                            return NULL_VALUE
                        }
                        if (map[keyIndex].compareAndSet(mapKey, key)) { // 0 -> 2
                            flag.compareAndSet(true, true)
                            if (map[keyIndex + 1].compareAndSet(mapValue, value)) {
                                return mapValue
                            } else {
                                continue
                            }
                        }
                        if (map[keyIndex].value == key) {
                            continue
                        }
                    }
                }
                if (++probes >= MAX_PROBES) {
                    return NEEDS_REHASH
                }
                if (keyIndex == 0) keyIndex = map.size
                keyIndex -= 2
            }
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size * 2))
            val newCore = next.value!!
            var index = 0
            while (index < map.size) {
                val key = map[index].value
                val value = map[index + 1].value
                if (isValue(value)) {
                    if(map[index + 1].compareAndSet(value, value * -1)) {
                        newCore.copyMap(key, value)
                        map[index + 1].compareAndSet(value * -1, DONE_VALUE)
                    } else {
                        continue
                    }
                }
                if (beingMoved(value)) {
                    newCore.copyMap(key, value * -1)
                    map[index + 1].compareAndSet(value, DONE_VALUE)
                }
                if (value == NULL_VALUE || value == DEL_VALUE) {
                    if (!map[index + 1].compareAndSet(value, DONE_VALUE)) {
                        continue
                    }
                }
                index += 2
            }
            return newCore
        }

        fun copyMap(key: Int, value: Int) {
            var keyIndex = index(key)
            var probes = 0
            while (true) {
                val mapKey = map[keyIndex].value
                if (mapKey == key) {
                    map[keyIndex + 1].compareAndSet(NULL_VALUE, value)
                    return
                }
                if (mapKey == NULL_KEY) {
                    if (map[keyIndex].compareAndSet(mapKey, key)) {
                        map[keyIndex + 1].compareAndSet(NULL_VALUE, value)
                        return
                    } else {
                        if (map[keyIndex].value == key) {
                            continue
                        }
                    }// кто-то уже положил, ну и фиг с ним
                }
                if (keyIndex == 0) keyIndex = map.size
                if (++probes >= MAX_PROBES) throw RuntimeException()
                keyIndex -= 2
            }
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val DONE_VALUE = Int.MIN_VALUE // mark for removed value
private const val NEEDS_REHASH = Int.MIN_VALUE + 1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int {
    if (isValue(value)) {
        return value
    }
    if (value >= 0) {
        return 0
    }
    throw java.lang.RuntimeException()
}

private fun beingMoved(value: Int): Boolean = value < 0 && value != DONE_VALUE