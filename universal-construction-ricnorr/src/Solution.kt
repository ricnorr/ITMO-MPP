/**
 * @author :TODO: Коробейников Николай
 */
class Solution : AtomicCounter {
    // объявите здесь нужные вам поля

    private val last: ThreadLocal<Node> = ThreadLocal.withInitial { root }

    private val root: Node = Node(0, Consensus())

    override fun getAndAdd(x: Int): Int {
        // напишите здесь код
        while (true) {
            val old = last.get()
            val res = old.x + x
            val node = Node(res, Consensus())
            last.set(last.get().next.decide(node))
            if (last.get() == node) {
                return old.x
            }
        }

    }

    // вам наверняка потребуется дополнительный класс
    class Node(val x: Int, val next: Consensus<Node>)
}
