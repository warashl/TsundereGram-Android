package app.yougram.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Локальное хранилище черновиков сообщений: по одному на чат (и тему/ветку комментариев). */
object DraftStore {
    private const val FILE = "yougram_drafts"

    private val _changes = MutableStateFlow(0)
    /** Растёт при каждом изменении черновика: по нему список чатов обновляет подпись «Черновик». */
    val changes: StateFlow<Int> = _changes

    private fun key(chatId: Long, topicId: Int, threadId: Long) = "$chatId:$topicId:$threadId"

    fun get(context: Context, chatId: Long, topicId: Int, threadId: Long): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(key(chatId, topicId, threadId), "").orEmpty()

    fun set(context: Context, chatId: Long, topicId: Int, threadId: Long, text: String) {
        val edit = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (text.isBlank()) edit.remove(key(chatId, topicId, threadId))
        else edit.putString(key(chatId, topicId, threadId), text)
        edit.apply()
        _changes.value++
    }
}