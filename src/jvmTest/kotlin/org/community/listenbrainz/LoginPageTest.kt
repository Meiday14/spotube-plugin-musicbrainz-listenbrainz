package org.community.listenbrainz

import app.cash.zipline.QuickJs
import kotlin.test.*

class LoginPageTest {
    private val bootstrap = """
        function element(value){
          var node=value||{};node.listeners={};node.attrs={};
          node.addEventListener=function(name,callback){node.listeners[name]=callback;};
          node.setAttribute=function(name,value){node.attrs[name]=value;};return node;
        }
        var sent=[],listeners={},documentListeners={},nodes={
          submit:element({disabled:true}),error:element({textContent:''}),token:element({value:'  example-token  ',type:'password'}),
          form:element(),cancel:element(),settings:element({value:'https://listenbrainz.org/settings/'}),
          'toggle-token':element(),'toggle-label':element({textContent:'Show'})
        };
        var document={hidden:false,getElementById:function(id){return nodes[id];},addEventListener:function(name,callback){documentListeners[name]=callback;}};
        var window={addEventListener:function(name,callback){listeners[name]=callback;}};
        var timers=[],clock=0,nextId=0;
        function setTimeout(callback,delay){var id=++nextId;timers.push({id:id,at:clock+delay,callback:callback});return id;}
        function clearTimeout(id){timers=timers.filter(function(timer){return timer.id!==id;});}
        function advance(ms){
          var until=clock+ms,limit=200;
          while(limit-->0){
            timers.sort(function(a,b){return a.at-b.at;});
            if(!timers.length||timers[0].at>until)break;
            var timer=timers.shift();clock=timer.at;timer.callback();
          }
          if(limit<=0)throw new Error('Unbounded timer loop');clock=until;
        }
    """.trimIndent()
    private fun script() = Regex("<script>([\\s\\S]*?)</script>").find(loginHtml("test-session"))!!.groupValues[1]

    @Test fun formStaysLocalAndSubmitsTrimmedTokenWithTheCurrentSession() {
        assertFalse(Regex("<a\\b",RegexOption.IGNORE_CASE).containsMatchIn(loginHtml("test-session")))
        assertContains(loginHtml("test-session"),"<html lang=\"en\">")
        QuickJs.create().use { js ->
            js.evaluate(bootstrap)
            js.evaluate("window.sendMessage=function(text){sent.push(JSON.parse(text));};")
            js.evaluate(script())
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            js.evaluate("nodes.form.onsubmit({preventDefault:function(){}});")
            val message=json.parseToJsonElement(js.evaluate("JSON.stringify(sent[sent.length-1])") as String)
            assertEquals("brainz.login",message.s("kind"))
            assertEquals("test-session",message.s("session"))
            assertEquals("example-token",message.s("token"))
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            js.evaluate("listeners.pageshow();")
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            assertEquals("Checking your token...",js.evaluate("nodes.error.textContent"))
        }
    }

    @Test fun lateBridgeReadinessAndCancelMessagesAreHandled() {
        QuickJs.create().use { js ->
            js.evaluate(bootstrap)
            js.evaluate(script())
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            js.evaluate("window.sendMessage=function(text){sent.push(JSON.parse(text));};listeners.onBridgeReady();")
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            js.evaluate("nodes.cancel.onclick();")
            assertEquals("brainz.cancel",js.evaluate("sent[sent.length-1].kind"))
            assertEquals("test-session",js.evaluate("sent[sent.length-1].session"))
            js.evaluate("listeners.pagehide();")
            assertEquals("brainz.cancel",js.evaluate("sent[sent.length-1].kind"))
        }
    }

    @Test fun androidNativeBridgeWorksWithoutWindowHelperOrReadyEvent() {
        QuickJs.create().use { js ->
            js.evaluate(bootstrap);js.evaluate(script())
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            // Spotube's local HTML can have the library bridge without its
            // lastLoadedUrl-triggered window.sendMessage convenience wrapper.
            js.evaluate("window.kmpJsBridge={callNative:function(method,params){if(method!=='sendMessage')throw new Error('Wrong method');sent.push(JSON.parse(params));}};advance(500);")
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            js.evaluate("nodes.form.onsubmit({preventDefault:function(){}});")
            assertEquals("brainz.login",js.evaluate("sent[sent.length-1].kind"))
            assertEquals("example-token",js.evaluate("sent[sent.length-1].token"))
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
        }
    }

    @Test fun pasteEnablesSignInEvenWhenAndroidOmitsTheInputEvent() {
        QuickJs.create().use { js ->
            js.evaluate(bootstrap)
            js.evaluate("nodes.token.value='';window.sendMessage=function(text){sent.push(JSON.parse(text));};")
            js.evaluate(script())
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            // paste fires before the browser inserts clipboard text.
            js.evaluate("nodes.token.listeners.paste();nodes.token.value='  pasted-token  ';advance(0);")
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            js.evaluate("nodes.form.onsubmit({preventDefault:function(){}});")
            assertEquals("pasted-token",js.evaluate("sent[sent.length-1].token"))
        }
    }

    @Test fun showHidePreservesTheTokenAndNeverSubmitsIt() {
        assertContains(loginHtml("test-session"),"aria-label=\"Show token\"")
        assertContains(loginHtml("test-session"),"id=\"toggle-token\"")
        QuickJs.create().use { js ->
            js.evaluate(bootstrap)
            js.evaluate("window.sendMessage=function(text){sent.push(JSON.parse(text));};")
            js.evaluate(script())
            js.evaluate("nodes['toggle-token'].onclick();")
            assertEquals("text",js.evaluate("nodes.token.type"))
            assertEquals("Hide",js.evaluate("nodes['toggle-label'].textContent"))
            assertEquals("true",js.evaluate("nodes['toggle-token'].attrs['aria-pressed']"))
            assertEquals("  example-token  ",js.evaluate("nodes.token.value"))
            js.evaluate("nodes['toggle-token'].onclick();")
            assertEquals("password",js.evaluate("nodes.token.type"))
            assertEquals("Show token",js.evaluate("nodes['toggle-token'].attrs['aria-label']"))
            assertEquals(0,js.evaluate("sent.filter(function(m){return typeof m.token==='string';}).length"))
        }
    }

    @Test fun nativeErrorsAndDroppedRepliesAllowExplicitRetryInTheSameForm() {
        QuickJs.create().use { js ->
            js.evaluate(bootstrap)
            js.evaluate("window.sendMessage=function(text){sent.push(JSON.parse(text));};")
            js.evaluate(script())
            js.evaluate("nodes.form.onsubmit({preventDefault:function(){}});nodes.token.listeners.input();listeners.focus();")
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            js.evaluate("window.brainzLoginError('old-session','Stale error');")
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            js.evaluate("window.brainzLoginError('test-session','Invalid token');")
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            assertEquals("Invalid token",js.evaluate("nodes.error.textContent"))
            js.evaluate("nodes.form.onsubmit({preventDefault:function(){}});advance(30000);")
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            assertContains(js.evaluate("nodes.error.textContent") as String,"No sign-in response")
            assertEquals(2,js.evaluate("sent.filter(function(m){return m.kind==='brainz.login';}).length"))
            js.evaluate("nodes.form.onsubmit({preventDefault:function(){}});")
            assertEquals(3,js.evaluate("sent.filter(function(m){return m.kind==='brainz.login';}).length"))
        }
    }

    @Test fun returningFromBrowserCanRecoverAfterBridgeStartupTimedOut() {
        QuickJs.create().use { js ->
            js.evaluate(bootstrap);js.evaluate(script());js.evaluate("advance(21000);")
            assertEquals(0,js.evaluate("timers.length"))
            assertContains(js.evaluate("nodes.error.textContent") as String,"connection is not ready")
            js.evaluate("window.kmpJsBridge={callNative:function(method,params){sent.push(JSON.parse(params));}};documentListeners.visibilitychange();")
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            assertEquals("",js.evaluate("nodes.error.textContent"))
            js.evaluate("nodes.form.onsubmit({preventDefault:function(){}});nodes.form.onsubmit({preventDefault:function(){}});")
            assertEquals(1,js.evaluate("sent.filter(function(m){return m.kind==='brainz.login';}).length"))
        }
    }

    @Test fun aTransientBridgeExceptionCanRecoverOnChangeAndDoesNotExposeTheToken() {
        QuickJs.create().use { js ->
            js.evaluate(bootstrap)
            js.evaluate("window.sendMessage=function(){throw new Error('Native bridge is still loading');};")
            js.evaluate(script())
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            js.evaluate("window.sendMessage=function(text){sent.push(JSON.parse(text));};nodes.token.listeners.change();")
            assertEquals(false,js.evaluate("nodes.submit.disabled"))
            assertEquals(0,js.evaluate("sent.filter(function(m){return typeof m.token==='string';}).length"))
            assertEquals("",js.evaluate("nodes.error.textContent"))
        }
    }

    @Test fun clearingTheFieldAndCancellingStopsTimersAndMasksTheToken() {
        QuickJs.create().use { js ->
            js.evaluate(bootstrap)
            js.evaluate("window.sendMessage=function(text){sent.push(JSON.parse(text));};")
            js.evaluate(script())
            js.evaluate("nodes['toggle-token'].onclick();nodes.token.value='';nodes.token.listeners.input();")
            assertEquals(true,js.evaluate("nodes.submit.disabled"))
            js.evaluate("nodes.token.value='example-token';nodes.token.listeners.input();nodes.form.onsubmit({preventDefault:function(){}});nodes.cancel.onclick();")
            assertEquals("password",js.evaluate("nodes.token.type"))
            assertEquals(0,js.evaluate("timers.length"))
            assertEquals("brainz.cancel",js.evaluate("sent[sent.length-1].kind"))
        }
    }
}
