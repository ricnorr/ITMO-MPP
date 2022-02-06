import java.lang.Integer.max

/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author Коробейников Николай
 */
class SolutionTemplateKt : MonotonicClock {
    private var c11 by RegularInt(0)
    private var c12 by RegularInt(0)
    private var c13 by RegularInt(0)
    private var c21 by RegularInt(0)
    private var c22 by RegularInt(0)
    private var c23 by RegularInt(0)

    override fun write(time: Time) {
        // write right-to-left
        c21 = time.d1
        c22 = time.d2
        c23 = time.d3
        c13 = time.d3
        c12 = time.d2
        c11 = time.d1

    }

    override fun read(): Time {
        // read left-to-right
        val currentC11 = c11
        val currentC12 = c12
        val currentC13 = c13
        val currentC23 = c23
        val currentC22 = c22
        val currentC21 = c21
        val arr1 : Array<Int> = arrayOf(currentC11, currentC12, currentC13)
        val arr2 = arrayOf(currentC21, currentC22, currentC23)
        val res = arrayOf(0, 0, 0)
        var i = 0
        while (i < 3) {
            if (arr1[i] == arr2[i]) {
                res[i] = arr1[i]
                i++
                continue
            }
            if (arr1[i] < arr2[i]) {
                res[i] = arr2[i]
                i++
                break
            }
        }
        while (i < 3) {
            res[i] = 0
            i++
        }
        return Time(res[0], res[1], res[2])
    }
}