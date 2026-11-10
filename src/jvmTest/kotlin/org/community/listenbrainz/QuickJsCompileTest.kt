package org.community.listenbrainz

import app.cash.zipline.QuickJs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class QuickJsCompileTest {
    @Test fun generatedModulesCompileInHostQuickJs() {
        val directory=File("build/compileSync/js/main/productionExecutable/kotlin")
        if(!directory.exists()) return
        val failures=mutableListOf<String>()
        directory.listFiles()!!.filter { it.extension=="js" }.forEach { file ->
            try { QuickJs.create().use { it.compile(file.readText(),file.name) } }
            catch(e: Exception) { failures+="${file.name}: ${e.message}" }
        }
        assertTrue(failures.isEmpty(),failures.joinToString("\n"))
    }
}
