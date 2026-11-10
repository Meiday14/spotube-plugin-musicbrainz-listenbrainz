package org.community.listenbrainz

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*
import net.swiftzer.semver.SemVer
import org.spotube.plugin_interfaces.host_apis.*
import org.spotube.plugin_interfaces.plugin_apis.core.*
import kotlin.random.Random

internal data class LoginTiming(
    val sessionTimeoutMs: Long = 600_000,
    val validationTimeoutMs: Long = 20_000,
    val initialLoadTimeoutMs: Long = 10_000,
    val ownershipCheckIntervalMs: Long = 2_000,
    val scriptTimeoutMs: Long = 2_000,
    val closeTimeoutMs: Long = 2_000,
)

private enum class LoginResult { FINISHED, CANCELLED, WINDOW_CLOSED }

internal class BrainzCore(
    private val client: BrainzClient,
    private val web: WebViewAPI,
    private val timing: LoginTiming = LoginTiming(),
): CoreAPI {
    private val state = MutableStateFlow(false)
    private val loginMutex = Mutex()
    private var cancelLogin: CompletableDeferred<Unit>? = null
    private var attempt = 0
    override val requiresAuthentication = true
    override val loggedInFlow: StateFlow<Boolean> = state.asStateFlow()

    suspend fun restore() {
        val value = client.storage.getString("listenbrainz.credentials") ?: return
        val creds = runCatching { json.parseToJsonElement(value) }.getOrNull() ?: return
        client.token = creds.s("token")?.takeIf { it.isNotBlank() }
        client.username = creds.s("username")?.takeIf { it.isNotBlank() }
        state.value = client.token != null && client.username != null
    }

    suspend fun acceptToken(token: String) {
        require(token.isNotBlank() && token.length < 512 && '\r' !in token && '\n' !in token) { "Invalid user token." }
        val data = client.request("https://api.listenbrainz.org/1/validate-token", authToken = token)
        require(data.b("valid") == true && !data.s("user_name").isNullOrBlank()) { "Invalid ListenBrainz user token." }
        val user = data.s("user_name")!!
        client.storage.putString("listenbrainz.credentials", obj("token" to str(token), "username" to str(user)).toString())
        client.token = token
        client.username = user
        state.value = true
    }

    override suspend fun login() {
        // Settings and the home sign-in prompt can call this independently.
        // A second call must not open another window or close the first attempt's window.
        if (!loginMutex.tryLock()) return
        val cancel = CompletableDeferred<Unit>()
        cancelLogin = cancel
        var windowAlreadyClosed = false
        try {
            val session = "listenbrainz-${++attempt}-${Random.nextLong().toString(16)}"
            val result = withTimeoutOrNull(timing.sessionTimeoutMs) { awaitLogin(session, cancel) }
            windowAlreadyClosed = result == LoginResult.WINDOW_CLOSED
        } finally {
            try {
                // A delayed host close for a dismissed window could affect the next attempt.
                if (!windowAlreadyClosed) closeLoginWindow()
            } finally {
                cancelLogin = null
                loginMutex.unlock()
            }
        }
    }

    private suspend fun awaitLogin(session: String, cancel: CompletableDeferred<Unit>): LoginResult = coroutineScope {
        val outcome = CompletableDeferred<LoginResult>()
        val ready = CompletableDeferred<Unit>()
        val messages = launch(start = CoroutineStart.UNDISPATCHED) {
            web.postMessagesFlow().collect { message ->
                val data = runCatching { json.parseToJsonElement(message) }.getOrNull() ?: return@collect
                // Ignore replayed messages and token submissions from a previous window.
                if (data.s("session") != session || outcome.isCompleted) return@collect
                when (data.s("kind")) {
                    "brainz.ready" -> ready.complete(Unit)
                    "brainz.cancel" -> outcome.complete(LoginResult.CANCELLED)
                    "brainz.login" -> {
                        val error = try {
                            val validated = withTimeoutOrNull(timing.validationTimeoutMs) {
                                acceptToken(data.s("token").orEmpty().trim())
                                true
                            } ?: false
                            if (validated) {
                                outcome.complete(LoginResult.FINISHED)
                                null
                            } else "Token validation timed out. Check your connection and try again."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.message ?: "Unable to sign in. Please try again."
                        }
                        if (error != null) {
                            try {
                                val script = "if(window.brainzLoginSession==${JsonPrimitive(session)}){" +
                                    "if(typeof window.brainzLoginError==='function'){window.brainzLoginError(${JsonPrimitive(session)},${JsonPrimitive(error)});}" +
                                    "else{document.getElementById('error').textContent=${JsonPrimitive(error)};" +
                                    "document.getElementById('submit').disabled=false;}}"
                                withTimeoutOrNull(timing.scriptTimeoutMs) { web.evaluateJavaScript(script) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (isClosedWindow(e)) outcome.complete(LoginResult.WINDOW_CLOSED)
                            }
                        }
                    }
                }
            }
        }
        val closedWindow = launch {
            withTimeoutOrNull(timing.initialLoadTimeoutMs) { ready.await() }
            while (isActive) {
                delay(timing.ownershipCheckIntervalMs)
                try {
                    // Spotube 6.0.0 filters the closing URL event out of urlChangeFlow().
                    // Only an explicit host ownership error means the window was closed.
                    // A null/slow script from a background Android WebView does not.
                    withTimeoutOrNull(timing.scriptTimeoutMs) { web.evaluateJavaScript("true") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isClosedWindow(e)) {
                        outcome.complete(LoginResult.WINDOW_CLOSED)
                        break
                    }
                }
            }
        }
        val cancelled = launch { cancel.await(); outcome.complete(LoginResult.CANCELLED) }
        try {
            web.navigateToHTML(loginHtml(session))
            outcome.await()
        } finally {
            messages.cancel()
            closedWindow.cancel()
            cancelled.cancel()
        }
    }

    private suspend fun closeLoginWindow() = withContext(NonCancellable) {
        // exitWebView is scheduled asynchronously by the host. Confirm ownership ended
        // before releasing the lock so a fresh attempt cannot race the queued close.
        try {
            withTimeoutOrNull(timing.scriptTimeoutMs) { web.evaluateJavaScript("true") }
        } catch (e: Exception) {
            if (isClosedWindow(e)) return@withContext
        }
        web.exitWebView()
        withTimeoutOrNull(timing.closeTimeoutMs) {
            while (true) {
                try { web.evaluateJavaScript("true") }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (isClosedWindow(e)) break }
                delay(50)
            }
        }
        Unit
    }

    override suspend fun logout() {
        cancelLogin?.complete(Unit)
        client.storage.remove("listenbrainz.credentials")
        client.token = null
        client.username = null
        state.value = false
    }

    override suspend fun checkPluginUpdates(currentVersion: SemVer): PluginUpdateInfo? = null
    override fun supportMarkdownText(currentVersion: SemVer) = """
        # MusicBrainz + ListenBrainz for Spotube V6
        Community plugin version $currentVersion.
        Browse the MusicBrainz catalogue and your ListenBrainz playlists and loved tracks.
        Get your user token in your regular browser, then paste it into the Spotube sign-in form.
        Your token is stored in this plugin's storage on this device. Saved albums, artists
        and playlist bookmarks are stored locally and are separated by account.
        [Support MetaBrainz](https://metabrainz.org/donate)
    """.trimIndent()
}

// Explicit ownership error from Spotube 6.0.0's RealWebViewAPI. Do not infer a close
// from timeouts, missing script responses, or switching to another Android app.
private fun isClosedWindow(error: Exception): Boolean =
    error.message?.contains("The WebView is not open for this plugin") == true

internal fun loginHtml(session: String): String = LOGIN_HTML.replace("__SESSION_JSON__", JsonPrimitive(session).toString())

internal val LOGIN_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; form-action 'none'">
<title>Connect to ListenBrainz</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}body{font:16px/1.5 system-ui,sans-serif;background:#141720;color:#f6f5f2;margin:0;padding:28px 20px}main{max-width:520px;margin:auto}h1{font-size:28px;line-height:1.2;margin:12px 0}p{color:#c7cad3}label,summary{font-weight:600}details{padding:16px;background:#202531;border:1px solid #414958;border-radius:12px;margin:22px 0}summary{cursor:pointer}ol{padding-left:22px}li{margin:10px 0}input,button{font:inherit;border-radius:8px;padding:12px;width:100%;margin:8px 0}input{background:#11151c;color:#fff;border:1px solid #707887}input:focus,button:focus-visible{outline:2px solid #ffae78;outline-offset:2px}input[readonly]{font-size:14px}button{border:0;background:#ff9a5a;color:#171923;font-weight:650;cursor:pointer}button:disabled{opacity:.5;cursor:not-allowed}.secondary{background:transparent;color:#dce0e9;border:1px solid #707887}.token-field{display:flex;gap:8px;align-items:center}.token-field input{flex:1;min-width:0}.token-field button{width:auto;min-width:80px;min-height:48px;display:flex;gap:6px;align-items:center;justify-content:center}.token-field svg{width:20px;height:20px;flex:none}.hint{font-size:14px}#error{color:#ffb4b4;min-height:24px;margin-bottom:8px}.tag{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#ffae78}
</style></head><body><main>
<span class="tag">Spotube community plugin</span><h1>Connect to ListenBrainz</h1>
<p>Paste your ListenBrainz user token to access your playlists.</p>
<p class="hint">You can switch to your browser to get the token. This form stays open for up to 10 minutes from when you open it.</p>
<details><summary>Need a user token?</summary>
<ol><li>Keep this form open and switch to your regular web browser.</li>
<li>Open the settings address below and sign in with your MusicBrainz account. Approve access if asked.</li>
<li>Copy your <strong>User token</strong>, then switch back to Spotube and paste it here.</li></ol>
<label for="settings">ListenBrainz settings address</label>
<input id="settings" value="https://listenbrainz.org/settings/" readonly aria-describedby="address-hint">
<p id="address-hint" class="hint">Press and hold the address to copy it. Using your browser keeps this form available in Spotube.</p></details>
<form id="form"><label for="token">User token</label>
<div class="token-field"><input id="token" type="password" required maxlength="511" autocomplete="off" autocapitalize="off" spellcheck="false" aria-describedby="privacy">
<button id="toggle-token" class="secondary" type="button" aria-label="Show token" aria-pressed="false" aria-controls="token"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg><span id="toggle-label">Show</span></button></div>
<p id="privacy" class="hint">Your token is stored on this device and sent only to the official ListenBrainz API.</p>
<p class="hint">The 20-second token check starts only after you press Sign in. It does not run while you retrieve or paste your token.</p>
<p id="error" role="status" aria-live="polite">Connecting to Spotube...</p>
<button id="submit" type="submit" disabled>Sign in</button></form>
<button id="cancel" class="secondary" type="button">Cancel</button>
<p class="hint">You can close this form and choose Sign in again later.</p>
<script>
(function(){
  var session=__SESSION_JSON__;
  window.brainzLoginSession=session;
  var token=document.getElementById('token'),submit=document.getElementById('submit'),error=document.getElementById('error');
  var readySent=false,busy=false,stopped=false,readyTimer=null,submitTimer=null,remaining=0;
  function controls(){submit.disabled=busy||!readySent||!token.value.trim();}
  function send(kind,token){
    var data={kind:kind,session:session};
    if(typeof token==='string')data.token=token;
    try{
      if(typeof window.sendMessage==='function')window.sendMessage(JSON.stringify(data));
      else if(window.kmpJsBridge&&typeof window.kmpJsBridge.callNative==='function')window.kmpJsBridge.callNative('sendMessage',JSON.stringify(data));
      else return false;
      return true;
    }catch(e){return false;}
  }
  function ready(){
    if(stopped)return;
    if(!readySent&&send('brainz.ready')){readySent=true;clearTimeout(readyTimer);readyTimer=null;if(!busy)error.textContent='';}
    controls();
  }
  function checkBridge(){
    ready();
    if(!readySent&&!stopped&&remaining-->0)readyTimer=setTimeout(checkBridge,500);
    else if(!readySent&&!stopped)error.textContent='Spotube connection is not ready. Close this form and choose Sign in again, or try pasting your token again.';
  }
  function connect(){
    if(stopped)return;
    clearTimeout(readyTimer);remaining=40;checkBridge();
  }
  window.brainzLoginError=function(owner,message){
    if(owner!==session||stopped)return;
    clearTimeout(submitTimer);submitTimer=null;busy=false;error.textContent=message;controls();
  };
  window.addEventListener('onBridgeReady',connect);
  window.addEventListener('pageshow',connect);
  window.addEventListener('focus',connect);
  document.addEventListener('visibilitychange',function(){if(!document.hidden)connect();});
  token.addEventListener('input',connect);
  token.addEventListener('change',connect);
  token.addEventListener('keyup',connect);
  token.addEventListener('focus',connect);
  token.addEventListener('paste',function(){setTimeout(connect,0);});
  document.getElementById('toggle-token').onclick=function(){
    var show=token.type==='password';token.type=show?'text':'password';
    this.setAttribute('aria-pressed',show?'true':'false');this.setAttribute('aria-label',show?'Hide token':'Show token');
    document.getElementById('toggle-label').textContent=show?'Hide':'Show';
    connect();
  };
  document.getElementById('form').onsubmit=function(e){
    e.preventDefault();
    if(busy||stopped)return;
    ready();var value=token.value.trim();
    if(!value){error.textContent='Paste your ListenBrainz user token first.';controls();return;}
    if(value.length>=512||/[\r\n]/.test(value)){error.textContent='Paste only the User token from ListenBrainz settings.';return;}
    if(send('brainz.login',value)){
      busy=true;controls();error.textContent='Checking your token...';
      // Native validation is limited to 20 seconds. Recover if Android drops
      // its reply so this local form cannot remain disabled indefinitely.
      submitTimer=setTimeout(function(){window.brainzLoginError(session,'No sign-in response received. Please try again.');},30000);
    }else{readySent=false;connect();error.textContent='Connecting to Spotube. Please try again in a moment.';}
  };
  function stop(){stopped=true;clearTimeout(readyTimer);clearTimeout(submitTimer);token.type='password';send('brainz.cancel');}
  document.getElementById('cancel').onclick=stop;
  window.addEventListener('pagehide',stop);
  connect();
})();
</script></main></body></html>
""".trimIndent()
