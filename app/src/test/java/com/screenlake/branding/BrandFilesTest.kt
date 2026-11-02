package com.screenlake.branding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An institution's fork rebrands the app by editing brand.xml, the icon and brand.properties
 * (docs/institution-branding.md). That only works if no participant-visible text names the app
 * or the institution anywhere else, so this fails when such text appears outside brand.xml.
 */
class BrandFilesTest {

    // Unit tests run from the app module directory.
    private val main = File("src/main")
    private val brandWords = Regex("""\bSRK\b|Screenlake|Princeton|screenlake\.com""")

    @Test
    fun `layouts, settings and the manifest take brand text from string resources`() {
        val files = File(main, "res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" && it.parentFile.name.let { d -> d.startsWith("layout") || d == "xml" || d == "menu" || d == "navigation" } }
            .plus(File(main, "AndroidManifest.xml"))
        val literal = Regex("""android:(text|title|summary|label|hint|contentDescription)="([^"@?][^"]*)"""")

        val offenders = files.flatMap { file ->
            literal.findAll(file.readText())
                .filter { brandWords.containsMatchIn(it.groupValues[2]) }
                .map { "${file.relativeTo(main)}: ${it.value}" }
                .toList()
        }.toList()

        assertEquals("Brand text outside brand.xml:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun `strings outside brand xml do not name the app or the institution`() {
        val stringValue = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val shared = File(main, "res").walkTopDown()
            .filter { it.isFile && it.parentFile.name.startsWith("values") && it.name != "brand.xml" }

        val offenders = shared.flatMap { file ->
            stringValue.findAll(file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""))
                .filter { it.groupValues[1] != "powered_by" && brandWords.containsMatchIn(it.groupValues[2]) }
                .map { "${file.relativeTo(main)}: ${it.groupValues[1]}" }
                .toList()
        }.toList()

        assertEquals("Move these to brand.xml:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun `every build keeps the Princeton Research Accelerator credit`() {
        val strings = File(main, "res/values/strings.xml").readText()
        assertTrue(strings.contains("""<string name="powered_by">Powered by the Princeton Research Accelerator</string>"""))
        assertTrue(File(main, "res/layout/login_or_register.xml").readText().contains("@string/powered_by"))
        assertTrue(File(main, "res/xml/preference.xml").readText().contains("@string/powered_by"))
    }
}
