package org.community.listenbrainz

import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*

internal val json = Json { ignoreUnknownKeys = true }
internal fun JsonElement.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonElement.array(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()
internal fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull
internal fun JsonElement.at(key: String): JsonElement = obj()[key] ?: JsonNull
internal fun JsonElement.s(key: String): String? = at(key).text()
internal fun JsonElement.n(key: String): Int? = (at(key) as? JsonPrimitive)?.intOrNull
internal fun JsonElement.l(key: String): Long? = (at(key) as? JsonPrimitive)?.longOrNull
internal fun JsonElement.a(key: String): List<JsonElement> = at(key).array()
internal fun JsonElement.b(key: String): Boolean? = (at(key) as? JsonPrimitive)?.booleanOrNull
internal fun obj(vararg values: Pair<String, JsonElement?>): JsonObject = JsonObject(values.mapNotNull { (k,v) -> v?.let { k to it } }.toMap())
internal fun str(value: String?): JsonPrimitive? = value?.let(::JsonPrimitive)
internal fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))
internal fun mbid(value: String?): String? {
    val id = value?.trim()?.trimEnd('/')?.substringAfterLast('/') ?: return null
    return id.lowercase().takeIf { Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(it) }
}
internal fun requireMbid(value: String): String = requireNotNull(mbid(value)) { "Invalid MusicBrainz identifier." }
internal fun encode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 255
        if (c in 65..90 || c in 97..122 || c in 48..57 || c in listOf(45,46,95,126)) append(c.toChar())
        else append('%').append(c.toString(16).uppercase().padStart(2,'0'))
    }
}
internal data class Window(val offset: Int, val limit: Int)
internal fun window(p: PaginationStrategy?): Window = when(p) {
    null -> Window(0,20)
    is PaginationStrategy.Offset -> Window(p.offset.coerceAtLeast(0),p.limit.coerceIn(1,100))
    is PaginationStrategy.Page -> Window(((p.page.coerceAtLeast(1)-1).toLong()*p.pageSize.coerceIn(1,100)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),p.pageSize.coerceIn(1,100))
    is PaginationStrategy.Cursor -> Window(p.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0,p.limit.coerceIn(1,100))
    is PaginationStrategy.Continuation -> Window(p.continuationToken?.toIntOrNull()?.coerceAtLeast(0) ?: 0,20)
}
internal fun <T> page(items: List<T>, total: Int, w: Window, consumed: Int = items.size): PaginationResult<T> =
    PaginationResult(items,total, if(consumed > 0 && w.offset + consumed < total) PaginationStrategy.Offset(w.offset+consumed,w.limit) else null)
internal fun <T> slice(items: List<T>, p: PaginationStrategy?): PaginationResult<T> {
    val w=window(p); return page(items.drop(w.offset).take(w.limit),items.size,w)
}
internal fun <A,B> PaginationResult<A>.mapItems(f:(A)->B) = PaginationResult(items.map(f),totalCount,nextPagination)
