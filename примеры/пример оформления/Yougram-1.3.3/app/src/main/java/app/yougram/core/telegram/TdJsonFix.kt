package app.yougram.core.telegram

/**
 * Правка JSON от TDLib до разбора библиотекой. У gift поле background может отсутствовать (подарок без фона),
 * а библиотека считает его обязательным и роняет приём всех обновлений (NoSuchElementException: Key background
 * is missing in the map). Подставляем пустой фон; если настоящий background есть, он идёт позже и перекрывает заглушку.
 * Вызывается из байткода библиотеки, см. buildSrc/TdJsonPatchFactory.kt.
 */
object TdJsonFix {
    private const val GIFT = "\"@type\":\"gift\","
    private const val GIFT_FIXED = GIFT +
        "\"background\":{\"@type\":\"giftBackground\",\"center_color\":0,\"edge_color\":0,\"text_color\":0},"

    @JvmStatic
    fun patch(json: String?): String? {
        if (json == null || !json.contains(GIFT)) return json
        return json.replace(GIFT, GIFT_FIXED)
    }
}
