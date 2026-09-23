package com.kl.travel

import com.kl.travel.data.Versions
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Guards against shipping a change without bumping the version everywhere. */
class VersionTest {
    private val root = File(System.getProperty("user.dir")!!).let { if (File(it, "../CHANGELOG.md").exists()) File(it, "..") else it }
    private val changelog by lazy { File(root, "CHANGELOG.md").readText() }

    @Test fun changelogTopAppVersionMatchesBuild() {
        val top = Regex("## App (\\d+\\.\\d+\\.\\d+)").find(changelog)!!.groupValues[1]
        assertEquals(top, BuildConfig.VERSION_NAME)
        val (a, b, c) = top.split(".").map { it.toInt() }
        assertEquals(a * 10000 + b * 100 + c, BuildConfig.VERSION_CODE)
    }

    @Test fun changelogTopTemplateVersionMatchesCode() {
        assertEquals(Regex("## Template (\\d+\\.\\d+)").find(changelog)!!.groupValues[1], Versions.TEMPLATE)
    }

    @Test fun bundledTemplateCarriesItsVersion() {
        val xlsx = File(root, "app/src/main/assets/KLTravel_Template.xlsx")
        assertTrue(xlsx.exists())
        ZipFile(xlsx).use { z ->
            // Cell text is either in sharedStrings or inline in the sheet XML, depending on the writer.
            val text = z.entries().asSequence().filter { it.name.startsWith("xl/worksheets/") || it.name == "xl/sharedStrings.xml" }
                .joinToString("\n") { z.getInputStream(it).bufferedReader().readText() }
            assertTrue("template must contain its version", text.contains("${Versions.TEMPLATE}  (works with KL Travel"))
            val core = z.getInputStream(z.getEntry("docProps/core.xml")).bufferedReader().readText()
            assertTrue(core.contains("template v${Versions.TEMPLATE}"))
        }
    }
}
