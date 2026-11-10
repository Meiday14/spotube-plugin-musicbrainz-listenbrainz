package org.community.listenbrainz

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.spotube.plugin_interfaces.host_apis.*
import kotlin.test.*

internal fun loginSession(html: String): String = json.parseToJsonElement(
    Regex("var session=(.*?);").find(html)!!.groupValues[1]
).text()!!

internal fun FakeWeb.sendLoginEvent(kind: String, token: String? = null, session: String = loginSession(html)) {
    messages.tryEmit(obj("kind" to str("brainz.$kind"), "session" to str(session), "token" to str(token)).toString())
}

internal fun readyWeb() = FakeWeb().apply { onNavigate = { sendLoginEvent("ready") } }
private fun validHttp() = FakeHttp { response("""{"valid":true,"user_name":"alice"}""") }

@OptIn(ExperimentalCoroutinesApi::class)
class LoginLifecycleTest {
    @Test fun nativeBackEndsPendingLoginAndRetryOpensOneFreshForm() = runTest {
        val web = readyWeb()
        val client = BrainzClient(validHttp(), MemoryStorage())
        val core = BrainzCore(client, web)
        val first = launch { core.login() }
        runCurrent()
        web.isOpen = false // Native Back: no pagehide callback is guaranteed on Android.
        advanceTimeBy(2_001); runCurrent()
        assertTrue(first.isCompleted)
        assertFalse(core.loggedInFlow.value)
        assertEquals(0, web.exitCount) // No stale close may be queued for the retry.
        assertEquals(1, web.openCount)
        val second = launch { core.login() }
        runCurrent()
        web.sendLoginEvent("login", "test-token")
        advanceUntilIdle()
        assertTrue(second.isCompleted)
        assertTrue(core.loggedInFlow.value)
        assertEquals(2, web.openCount)
        assertEquals(1, web.exitCount)
    }

    @Test fun backgroundedWebViewWithNullScriptsKeepsTheTokenForm() = runTest {
        val web = readyWeb().apply { scriptResult = null }
        val core = BrainzCore(BrainzClient(validHttp(), MemoryStorage()), web)
        val task = launch { core.login() }
        runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertTrue(task.isActive)
        assertTrue(web.isOpen)
        assertEquals(1, web.openCount)
        web.sendLoginEvent("login", "test-token")
        advanceUntilIdle()
        assertTrue(task.isCompleted)
        assertTrue(core.loggedInFlow.value)
    }

    @Test fun pausedScriptCallbacksDoNotLookLikeAClosedWindow() = runTest {
        val web = readyWeb().apply { evaluate = { delay(5_000); null } }
        val core = BrainzCore(BrainzClient(validHttp(), MemoryStorage()), web)
        val task = launch { core.login() }
        runCurrent()
        advanceTimeBy(12_000); runCurrent()
        assertTrue(task.isActive)
        assertTrue(web.isOpen)
        web.evaluate = null
        web.sendLoginEvent("cancel")
        advanceUntilIdle()
        assertTrue(task.isCompleted)
    }

    @Test fun concurrentSignInCallsDoNotOpenOrCloseEachOthersWindow() = runTest {
        val web = readyWeb()
        val core = BrainzCore(BrainzClient(validHttp(), MemoryStorage()), web)
        val task = launch { core.login() }
        runCurrent()
        core.login() // A second entry point, e.g. the home screen sign-in prompt.
        assertTrue(task.isActive)
        assertEquals(1, web.openCount)
        assertEquals(0, web.exitCount)
        web.sendLoginEvent("cancel")
        advanceUntilIdle()
        assertTrue(task.isCompleted)
    }

    @Test fun invalidTokenShowsAnErrorAndAllowsRetryInTheSameForm() = runTest {
        val http = FakeHttp { response("""{"valid":false}""") }
        val web = readyWeb()
        val client = BrainzClient(http, MemoryStorage())
        val core = BrainzCore(client, web)
        val task = launch { core.login() }
        runCurrent()
        web.sendLoginEvent("login", "invalid-token"); runCurrent()
        assertTrue(task.isActive)
        assertNull(client.token)
        assertTrue(web.scripts.any { "Invalid ListenBrainz user token" in it && "disabled=false" in it })
        http.responder = { response("""{"valid":true,"user_name":"alice"}""") }
        web.sendLoginEvent("login", "test-token")
        advanceUntilIdle()
        assertTrue(task.isCompleted)
        assertTrue(core.loggedInFlow.value)
        assertEquals(1, web.openCount)
    }

    @Test fun validationTimeoutAllowsRetryWithoutWaitingTenMinutes() = runTest {
        var slow = true
        val http = object: HttpClientAPI {
            override suspend fun request(method: HttpMethod, url: String, requestHeaders: Map<String,String>?, body: String?): HttpResponse {
                if (slow) delay(30_000)
                return response("""{"valid":true,"user_name":"alice"}""")
            }
        }
        val web = readyWeb()
        val core = BrainzCore(BrainzClient(http, MemoryStorage()), web)
        val task = launch { core.login() }
        runCurrent()
        web.sendLoginEvent("login", "test-token"); runCurrent()
        advanceTimeBy(20_001); runCurrent()
        assertTrue(task.isActive)
        assertFalse(core.loggedInFlow.value)
        assertTrue(web.scripts.any { "validation timed out" in it && "disabled=false" in it })
        slow = false
        web.sendLoginEvent("login", "test-token")
        advanceUntilIdle()
        assertTrue(task.isCompleted)
        assertTrue(core.loggedInFlow.value)
    }

    @Test fun replayedAndMalformedMessagesNeverSubmitAnOldToken() = runTest {
        val http = validHttp()
        val web = readyWeb()
        val core = BrainzCore(BrainzClient(http, MemoryStorage()), web)
        val task = launch { core.login() }
        runCurrent()
        web.sendLoginEvent("login", "old-token", "older-window")
        web.messages.emit("not-json")
        runCurrent()
        assertTrue(http.requests.isEmpty())
        assertTrue(task.isActive)
        web.sendLoginEvent("cancel")
        advanceUntilIdle()
    }

    @Test fun sessionTimeoutClosesTheFormAndReleasesTheLoginLock() = runTest {
        val web = readyWeb()
        val core = BrainzCore(BrainzClient(validHttp(), MemoryStorage()), web, LoginTiming(sessionTimeoutMs = 3_000))
        val first = launch { core.login() }
        advanceTimeBy(3_001); runCurrent()
        assertTrue(first.isCompleted)
        assertFalse(web.isOpen)
        val second = launch { core.login() }
        runCurrent()
        assertEquals(2, web.openCount)
        web.sendLoginEvent("cancel")
        advanceUntilIdle()
        assertTrue(second.isCompleted)
    }

    @Test fun closingDuringValidationCannotStoreCredentialsLater() = runTest {
        val http = object: HttpClientAPI {
            override suspend fun request(method: HttpMethod, url: String, requestHeaders: Map<String,String>?, body: String?): HttpResponse {
                delay(15_000)
                return response("""{"valid":true,"user_name":"alice"}""")
            }
        }
        val storage = MemoryStorage()
        val web = readyWeb()
        val core = BrainzCore(BrainzClient(http, storage), web)
        val task = launch { core.login() }
        runCurrent()
        web.sendLoginEvent("login", "test-token"); runCurrent()
        web.isOpen = false
        advanceTimeBy(20_000); runCurrent()
        assertTrue(task.isCompleted)
        assertFalse(core.loggedInFlow.value)
        assertTrue(storage.data.isEmpty())
        assertEquals(0, web.exitCount)
    }

    @Test fun closingBeforeBridgeReadyDoesNotLeaveTenMinuteSpinner() = runTest {
        val web = FakeWeb()
        val core = BrainzCore(BrainzClient(validHttp(), MemoryStorage()), web)
        val task = launch { core.login() }
        runCurrent()
        web.isOpen = false
        advanceTimeBy(12_001); runCurrent()
        assertTrue(task.isCompleted)
        assertEquals(0, web.exitCount)
    }

    @Test fun cancellingATokenFormPreservesAnExistingSession() = runTest {
        val web = readyWeb()
        val storage = MemoryStorage()
        val client = BrainzClient(validHttp(), storage)
        val core = BrainzCore(client, web)
        core.acceptToken("existing-token")
        val task = launch { core.login() }
        runCurrent()
        web.sendLoginEvent("cancel")
        advanceUntilIdle()
        assertTrue(task.isCompleted)
        assertEquals("existing-token", client.token)
        assertTrue(core.loggedInFlow.value)
    }
}
