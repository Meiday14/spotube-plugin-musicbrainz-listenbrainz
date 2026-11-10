package org.community.listenbrainz

import app.cash.zipline.QuickJs
import kotlin.test.*

class LoginPageTest {
    private val bootstrap = """
        var sent=[],listeners={},nodes={
          submit:{disabled:true},error:{textContent:''},token:{value:'  example-token  '},
          form:{},cancel:{},settings:{value:'https://listenbrainz.org/settings/'}
        };
        var document={getElementById:function(id){return nodes[id];}};
        var window={addEventListener:function(name,callback){listeners[name]=callback;}};
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
}
