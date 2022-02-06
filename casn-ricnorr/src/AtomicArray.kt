import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any?>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        var ind = 0
        while (true) {
            ind++
            if (ind == 1000) {
                for (x in 0 until a.size) {
                    print(a[x].value)
                }
            }
            val value = a[index].value
            fullComplete(value)
            if (value !is Descriptor<*> && value !is CASNDescriptor<*>) {
                return value as E
            }
        }
    }

    fun set(index: Int, value: E) {
        while (true) {
            val oldValue = a[index].value
            fullComplete(oldValue)
            if ((oldValue !is Descriptor<*>) && (oldValue !is CASNDescriptor<*>)) {
                if (a[index].compareAndSet(oldValue, value)) {
                    return
                }
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val oldValue = a[index].value
            fullComplete(oldValue)
            if ((oldValue !is Descriptor<*>) && (oldValue !is CASNDescriptor<*>)) {
                if (expected == oldValue) {
                    if (a[index].compareAndSet(expected, update)) {
                        return true
                    }
                } else {
                    return false
                }
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 == expected2) {
                return cas(index1, expected1, update2)
            } else {
                return false
            }
        }
        return CASN(index1, index2, expected1, expected2, update1, update2)
    }

    /**
     * проверяет, что если
     * casnDesc.b.value == bExp,
     * casnDesc.outcome == null
     * то меняеем casnDesc.b на casnDesc
     */
    fun DSCC( //
        casnDesc: CASNDescriptor<E>
    ) {
        while (true) {
            val aValue = a[casnDesc.b].value
            if (aValue is Descriptor<*>) {
                completeDSCC(aValue as Descriptor<E>)
                if (aValue.updateA == casnDesc) {
                    return
                }
            }
            if (aValue is CASNDescriptor<*>) {
                if (aValue == casnDesc) {
                    return
                }
                completeCASN(aValue as CASNDescriptor<E>)
            }
            if (aValue !is Descriptor<*> && aValue !is CASNDescriptor<*>) {
                if (aValue == casnDesc.expectB) {
                    val dsccDesc = Descriptor(
                        a = casnDesc.b,
                        expectA = casnDesc.expectB,
                        updateA = casnDesc
                    )
                    if (a[casnDesc.b].compareAndSet(aValue, dsccDesc)) {
                        completeDSCC(dsccDesc)
                        return
                    }
                } else {
                    return
                }
            }
        }
    }

    fun completeDSCC(descriptor: Descriptor<E>) {
        if (descriptor.updateA.outcome.value == null) {
            a[descriptor.a].compareAndSet(descriptor, descriptor.updateA)
        } else {
            a[descriptor.a].compareAndSet(descriptor, descriptor.expectA)
        }
    }

    fun CASN(aIndF: Int, bIndF: Int, aExpF: E, bExpF: E, updateAF: E, updateBF: E): Boolean {
        val aInd: Int
        val bInd: Int
        val aExp: E
        val bExp: E
        val updateA: E
        val updateB: E
        if (aIndF <= bIndF) {
            aInd = aIndF
            bInd = bIndF
            aExp = aExpF
            bExp = bExpF
            updateA = updateAF
            updateB = updateBF
        } else {
            aInd = bIndF
            bInd = aIndF
            aExp = bExpF
            bExp = aExpF
            updateA = updateBF
            updateB = updateAF
        }
        while (true) {
            val aFromArr = a[aInd].value
            if (aFromArr is CASNDescriptor<*>) {
                completeCASN(aFromArr as CASNDescriptor<E>)
            }
            if (aFromArr is Descriptor<*>) {
                completeDSCC(aFromArr as Descriptor<E>)
            }
            if (aFromArr !is Descriptor<*> && aFromArr !is CASNDescriptor<*>) {
                if (aExp == aFromArr) {
                    val descriptorCASN = CASNDescriptor(
                        a = aInd,
                        expectA = aExp,
                        updateA = updateA,
                        b = bInd,
                        expectB = bExp,
                        updateB = updateB
                    )
                    if (a[descriptorCASN.a].compareAndSet(descriptorCASN.expectA, descriptorCASN)) {
                        return completeCASN(descriptorCASN)
                    }
                } else {
                    return false
                }
            }
        }
    }

    private fun completeCASN(descriptorCASN: CASNDescriptor<E>): Boolean {
        DSCC(descriptorCASN)
        if (a[descriptorCASN.a].value == descriptorCASN && a[descriptorCASN.b].value == descriptorCASN) {
            if (descriptorCASN.outcome.compareAndSet(null, true)) {
                a[descriptorCASN.a].compareAndSet(descriptorCASN, descriptorCASN.updateA)
                a[descriptorCASN.b].compareAndSet(descriptorCASN, descriptorCASN.updateB)
            }
        } else {
            if (descriptorCASN.outcome.compareAndSet(null, false)) {
                a[descriptorCASN.a].compareAndSet(descriptorCASN, descriptorCASN.expectA)
            }
        }
        //a[descriptorCASN.b].compareAndSet(descriptorCASN, descriptorCASN.expectB)
        helpChangeCASN(descriptorCASN)
        return descriptorCASN.outcome.value!!
    }

    private fun helpChangeCASN(descriptorCASN: CASNDescriptor<E>) {
        if (descriptorCASN.outcome.value == true) {
            a[descriptorCASN.a].compareAndSet(descriptorCASN, descriptorCASN.updateA)
            a[descriptorCASN.b].compareAndSet(descriptorCASN, descriptorCASN.updateB)
        }
        if (descriptorCASN.outcome.value == false) {
            a[descriptorCASN.a].compareAndSet(descriptorCASN, descriptorCASN.expectA)
        }
        if (descriptorCASN.outcome.value == null) {
            throw RuntimeException()
        }
    }

    private fun fullComplete(value: Any?) {
        if (value is CASNDescriptor<*>) {
            completeCASN(value as CASNDescriptor<E>)
        }
        if (value is Descriptor<*>) {
            completeDSCC(value as Descriptor<E>)
        }
    }
}


class Descriptor<E>(
    val a: Int,
    val expectA: E,
    val updateA: CASNDescriptor<E>
)

class CASNDescriptor<E>(
    val a: Int,
    val expectA: E,
    val updateA: E,
    val b: Int,
    val expectB: E,
    val updateB: E,
    val outcome: AtomicRef<Boolean?> = atomic(null)
)
