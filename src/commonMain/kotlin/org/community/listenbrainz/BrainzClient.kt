package org.community.listenbrainz

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.*
import kotlin.time.Clock

internal class ApiException(val status: Int): Exception(when(status) {
    401 -> "Your ListenBrainz session is invalid. Sign in again in plugin settings."
    403 -> "This account is not allowed to perform this action."
    404 -> "The requested item was not found."
    429,503 -> "The service is temporarily busy. Please try again shortly."
    else -> "Brainz service error (HTTP $status)."
})

internal class BrainzClient(val http: HttpClientAPI, val storage: PersistedStorageAPI) {
    var token: String? = null
    var username: String? = null
    private val mbMutex = Mutex()
    private val storageMutex = Mutex()
    private var lastMbRequest = 0L
    private val cache = linkedMapOf<String,Pair<Long,JsonElement>>()
    private fun now() = Clock.System.now().toEpochMilliseconds()

    suspend fun request(url: String, method: HttpMethod = HttpMethod.Get, body: JsonElement? = null,
                        authToken: String? = null): JsonElement {
        val headers = mutableMapOf("Accept" to "application/json", "User-Agent" to
            "Spotube-ListenBrainz-Community/$PLUGIN_VERSION ($PLUGIN_CONTACT_URL)")
        if(body != null) headers["Content-Type"]="application/json"
        if(authToken != null) headers["Authorization"]="Token $authToken"
        var attempt=0
        while(true) {
            val response = http.request(method,url,headers,body?.toString())
            if(response.statusCode in 200..299) return response.body?.takeIf { it.isNotBlank() }?.let(json::parseToJsonElement) ?: JsonNull
            // Never retry mutations: a lost reply may already have applied the operation.
            if(method==HttpMethod.Get && response.statusCode in listOf(429,503) && attempt++ < 2) {
                val retry = response.headers.entries.firstOrNull { it.key.equals("Retry-After",true) }?.value?.toLongOrNull() ?: (2L*attempt)
                if(retry > 60) throw ApiException(response.statusCode)
                delay(retry.coerceAtLeast(1)*1000); continue
            }
            throw ApiException(response.statusCode)
        }
    }
    suspend fun mb(path: String, params: Map<String,String> = emptyMap()): JsonElement = mbMutex.withLock {
        val url = url("https://musicbrainz.org/ws/2",path,params + ("fmt" to "json"))
        cache[url]?.takeIf { now()-it.first < 300_000 }?.let { return@withLock it.second }
        delay((1100-(now()-lastMbRequest)).coerceAtLeast(0))
        lastMbRequest=now()
        val result=request(url)
        if(cache.size >= 128) cache.remove(cache.keys.first())
        cache[url]=now() to result
        result
    }
    suspend fun lb(path: String, params: Map<String,String> = emptyMap(), method: HttpMethod=HttpMethod.Get,
                   body: JsonElement? = null, authenticated: Boolean=false): JsonElement {
        if(authenticated) requireLogin()
        return request(url("https://api.listenbrainz.org/1",path,params),method,body,token)
    }
    fun requireLogin(): String = checkNotNull(username?.takeIf { token != null }) { "Connect your ListenBrainz account in plugin settings." }
    suspend fun ids(kind: String): List<String> = storage.getString("${username.orEmpty()}:$kind")?.let { json.parseToJsonElement(it).array().mapNotNull(JsonElement::text) }.orEmpty()
    suspend fun saveIds(kind: String, ids: List<String>, remove: Boolean=false) = storageMutex.withLock {
        val previous=ids(kind)
        storage.putString("${username.orEmpty()}:$kind",strings(if(remove) previous.filterNot { it in ids } else (previous+ids).distinct()).toString())
    }
    private fun url(base: String,path: String,params: Map<String,String>): String = base+path+
        if(params.isEmpty()) "" else params.entries.joinToString("&","?") { encode(it.key)+"="+encode(it.value) }
}
