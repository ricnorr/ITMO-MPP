import org.junit.Test
import java.lang.Thread.sleep
import kotlin.concurrent.thread

class SimpleTest {
    @Test
    fun test() {
        val x = DynamicArrayImpl<Int>()
        val list : MutableList<Thread> = mutableListOf()
        for (i in 0..20) {
            list.add(thread {
                x.pushBack(i)
            })
            if (i == 2) {
                thread {
                    x.put(0, 2023)
                }
            }
        }
        for (y in list) {
            y.join()
        }
        val lst  = (0..20).map {x.get(it)}.toList()
        print("2")

    }

}