import org.junit.Test

class SimpleTest {
    @Test
    fun test() {
        val x = FAAQueue<Int>()
        x.enqueue(1)
        x.enqueue(-8)
        x.dequeue()
        print("1")
    }
}