import kotlinx.atomicfu.atomic

fun <T> atomic(block: TxScope.() -> T): T {
    while (true) {
        val transaction = Transaction()
        try {
            val result = block(transaction)
            if (transaction.commit()) return result
            transaction.abort()
        } catch (e: AbortException) {
            transaction.abort()
        }
    }
}

abstract class TxScope {
    abstract fun <T> TxVar<T>.read(): T
    abstract fun <T> TxVar<T>.write(x: T): T
}

class TxVar<T>(initial: T) {
    private val loc = atomic(Loc(initial, initial, rootTx))

    fun openIn(tx: Transaction, update: (T) -> T): T {
        while (true) {
            val curLoc = loc.value
            val curVal = curLoc.valueIn(tx) { owner -> owner.abort() }
            if (curVal == TxStatus.ACTIVE) continue
            val newVal = update(curVal as T)
            if (loc.compareAndSet(curLoc, Loc(curVal, newVal, tx))) {
                if (tx.status == TxStatus.ABORTED) throw AbortException
                return newVal
            }
        }
    }
}

private class Loc<T>(
    val oldValue: T,
    val newValue: T,
    val owner: Transaction
) {
    fun valueIn(
        transaction: Transaction,
        onActive: (Transaction) -> Unit
    ): Any? {
        return if (owner == transaction) {
            newValue
        } else {
            when (owner.status) {
                TxStatus.COMMITTED -> newValue
                TxStatus.ABORTED -> oldValue
                TxStatus.ACTIVE -> {
                    onActive(owner)
                    TxStatus.ACTIVE
                }
            }
        }
    }
}

private val rootTx = Transaction().apply { commit() }

/**
 * Transaction status.
 */
enum class TxStatus { ACTIVE, COMMITTED, ABORTED }

/**
 * Transaction implementation.
 */
class Transaction : TxScope() {
    private val _status = atomic(TxStatus.ACTIVE)
    val status: TxStatus get() = _status.value

    fun commit(): Boolean =
        _status.compareAndSet(TxStatus.ACTIVE, TxStatus.COMMITTED)

    fun abort() {
        _status.compareAndSet(TxStatus.ACTIVE, TxStatus.ABORTED)
    }

    override fun <T> TxVar<T>.read(): T = openIn(this@Transaction) { it }
    override fun <T> TxVar<T>.write(x: T) = openIn(this@Transaction) { x }
}

/**
 * This exception is thrown when transaction is aborted.
 */
private object AbortException : Exception() {
    override fun fillInStackTrace(): Throwable = this
}