package com.clawd.pet

fun argb(v: Long): Int = v.toInt()

data class Palette(val name: String, val body: Int, val eye: Int)

object Skins {

    val palettes: List<Palette> = listOf(
        Palette("Claude", argb(0xFFD97757), argb(0xFF1A1A1A)),
        Palette("Мятный", argb(0xFF4FBF98), argb(0xFF12241D)),
        Palette("Лавандовый", argb(0xFF9B8AEE), argb(0xFF1B1631)),
        Palette("Небесный", argb(0xFF4FA8E8), argb(0xFF0E1C29)),
        Palette("Розовый", argb(0xFFEE85AE), argb(0xFF2A1220)),
        Palette("Графит", argb(0xFF5E6670), argb(0xFFF2F2F2)),
        Palette("Золотой", argb(0xFFE8B84F), argb(0xFF2A2012)),
        Palette("Коралловый", argb(0xFFFF7A6B), argb(0xFF2A1512)),
        Palette("Изумруд", argb(0xFF3FB36B), argb(0xFF0E2417)),
        Palette("Снежный", argb(0xFFE8ECF2), argb(0xFF2A2E36)),
        Palette("Ночной", argb(0xFF3B4A73), argb(0xFFDDE3F0)),
        Palette("Пламя", argb(0xFFF2603C), argb(0xFF2A120C)),
        Palette("Океан", argb(0xFF2FA3B8), argb(0xFF08222A)),
        Palette("Сирень", argb(0xFFC99BE8), argb(0xFF2A1B33)),
    )

    val sizeNames = listOf("Маленький", "Средний", "Большой", "Огромный")
    val sizeDp = listOf(3.0, 4.0, 5.0, 6.5)

    val speedNames = listOf("Спокойный", "Обычный", "Шустрый")
    val speedFactor = listOf(0.6, 1.0, 1.6)

    val snackSpeedNames = listOf("Редко", "Обычно", "Часто")

    val accessoryNames =
        listOf("Нет", "Шапочка", "Кепка", "Очки", "Тёмные очки", "Нимб", "Бабочка", "Корона", "Наушники", "Цветок")

    val phraseFreqNames = listOf("Редко", "Иногда", "Часто")
    val phraseFreqChance = listOf(0.12, 0.28, 0.5)

    val sleepNames = listOf("Через 1 минуту", "Через 3 минуты", "Через 10 минут", "Никогда")
    val sleepSeconds = listOf(60, 180, 600, 0)

    val hungerNames = listOf("Медленно", "Обычно", "Быстро")

    val hungerFullSeconds = listOf(14400.0, 7200.0, 3600.0)

    const val HEART = 0xFFFF5C7A.toInt()
    const val ZZZ = 0xFF8FA3BF.toInt()
    const val SPARK = 0xFFFFC94D.toInt()
    const val STAR = 0xFFFFD76B.toInt()
    const val DROP = 0xFF7FB6E8.toInt()
    const val NOTE = 0xFFB98AEE.toInt()
    const val FOOD = 0xFFE8A24F.toInt()
    const val QUESTION = 0xFFFFC94D.toInt()
    const val FISH = 0xFF5FB0C0.toInt()
    const val COOKIE = 0xFFC8925A.toInt()
    const val BOLT = 0xFFFFE04D.toInt()

    val patterns: Map<String, List<String>> = mapOf(
        "heart" to listOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."),
        "zzz" to listOf("XXXXX", "...X.", "..X..", ".X...", "XXXXX"),
        "spark" to listOf("..X..", "..X..", "XX.XX", "..X..", "..X.."),
        "star" to listOf("..X..", ".XXX.", "XXXXX", ".XXX.", "X...X"),
        "drop" to listOf("..X..", "..X..", ".XXX.", "XXXXX", ".XXX."),
        "note" to listOf("...XX", "...X.", "...X.", "XX.X.", "XX..."),
        "question" to listOf(".XXX.", "X...X", "...X.", "..X..", "..X..", ".....", "..X.."),
        "food" to listOf(".XXX.", "XXXXX", "XXXXX", ".XXX."),
        "fish" to listOf(".XX.X", "XXXXX", ".XX.X"),
        "cookie" to listOf(".XX.", "XXXX", "XXXX", ".XX."),
        "bolt" to listOf("..XX", ".XX.", "XXXX", ".XX.", "XX.."),
    )
}
