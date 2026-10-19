package com.personalai.instantread.manifest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestIntentFiltersTest {
    @Test
    fun applicationLabelUsesCurrentProductName() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val application = document.getElementsByTagName("application").item(0) as Element

        assertTrue(application.getAttribute("android:label") == "即阅")
    }

    @Test
    fun applicationUsesCustomLauncherIcon() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val application = document.getElementsByTagName("application").item(0) as Element

        assertTrue(application.getAttribute("android:icon") == "@mipmap/ic_launcher")
        assertTrue(application.getAttribute("android:roundIcon") == "@mipmap/ic_launcher_round")

        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { density ->
            val iconFile = File("src/main/res/mipmap-$density/ic_launcher.png")
            val foregroundFile = File("src/main/res/mipmap-$density/ic_launcher_foreground.png")
            assertTrue("Missing launcher icon for $density", iconFile.exists())
            assertTrue("Missing launcher icon foreground for $density", foregroundFile.exists())
        }

        val adaptiveIcon = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val adaptiveRoundIcon = File("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
        assertTrue("Missing adaptive launcher icon", adaptiveIcon.exists())
        assertTrue("Missing adaptive round launcher icon", adaptiveRoundIcon.exists())
        assertTrue(
            "Adaptive foreground should not reference the adaptive icon itself.",
            adaptiveIcon.readText().contains("@mipmap/ic_launcher_foreground")
        )
    }

    @Test
    fun viewFiltersDeclareSupportedMimeTypesAndBrowsableCategory() {
        val filters = intentFiltersForAction("android.intent.action.VIEW")
        val xml = filters.joinToString("\n") { it.toXmlText() }
        val mimeTypes = mimeTypesForAction("android.intent.action.VIEW")

        assertTrue(xml.contains("android.intent.category.DEFAULT"))
        assertTrue(xml.contains("android.intent.category.BROWSABLE"))
        assertTrue(mimeTypes.contains("text/html"))
        assertTrue(mimeTypes.contains("application/xhtml+xml"))
        assertTrue(mimeTypes.contains("text/markdown"))
        assertTrue(mimeTypes.contains("text/x-markdown"))
        assertTrue(mimeTypes.contains("text/plain"))
        assertTrue(mimeTypes.contains("application/zip"))
        assertTrue(mimeTypes.contains("application/x-zip"))
        assertTrue(mimeTypes.contains("application/x-zip-compressed"))
        assertTrue(mimeTypes.contains("application/octet-stream"))
        assertFalse(mimeTypes.contains("*/*"))
    }

    @Test
    fun sendFiltersStayLimitedToSupportedMimeTypes() {
        val mimeTypes = mimeTypesForAction("android.intent.action.SEND")

        assertFalse(mimeTypes.contains("*/*"))
        assertTrue(mimeTypes.contains("text/html"))
        assertTrue(mimeTypes.contains("application/xhtml+xml"))
        assertTrue(mimeTypes.contains("text/markdown"))
        assertTrue(mimeTypes.contains("text/x-markdown"))
        assertTrue(mimeTypes.contains("text/plain"))
        assertTrue(mimeTypes.contains("application/zip"))
        assertTrue(mimeTypes.contains("application/x-zip"))
        assertTrue(mimeTypes.contains("application/x-zip-compressed"))
        assertTrue(mimeTypes.contains("application/octet-stream"))
    }

    @Test
    fun sendMultipleFiltersStayLimitedToSupportedMimeTypes() {
        val mimeTypes = mimeTypesForAction("android.intent.action.SEND_MULTIPLE")

        assertFalse(mimeTypes.contains("*/*"))
        assertTrue(mimeTypes.contains("text/html"))
        assertTrue(mimeTypes.contains("application/xhtml+xml"))
        assertTrue(mimeTypes.contains("text/markdown"))
        assertTrue(mimeTypes.contains("text/x-markdown"))
        assertTrue(mimeTypes.contains("text/plain"))
        assertTrue(mimeTypes.contains("application/zip"))
        assertTrue(mimeTypes.contains("application/x-zip"))
        assertTrue(mimeTypes.contains("application/x-zip-compressed"))
        assertTrue(mimeTypes.contains("application/octet-stream"))
    }

    @Test
    fun fileSchemeViewFiltersDeclareCoreExtensions() {
        val filters = intentFiltersForAction("android.intent.action.VIEW")
        val xml = filters.joinToString("\n") { it.toXmlText() }

        listOf(".html", ".htm", ".md", ".markdown", ".txt", ".zip").forEach { extension ->
            assertTrue("Missing file path pattern for $extension", xml.contains(extension))
        }
    }

    private fun intentFiltersForAction(actionName: String): List<Element> {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val filters = document.getElementsByTagName("intent-filter")
        return (0 until filters.length)
            .map { filters.item(it) as Element }
            .filter { filter ->
                val actions = filter.getElementsByTagName("action")
                (0 until actions.length).any { index ->
                    val action = actions.item(index) as Element
                    action.getAttribute("android:name") == actionName
                }
            }
    }

    private fun mimeTypesForAction(actionName: String): Set<String> {
        return intentFiltersForAction(actionName)
            .flatMap { filter ->
                val dataElements = filter.getElementsByTagName("data")
                (0 until dataElements.length)
                    .map { dataElements.item(it) as Element }
                    .mapNotNull { data ->
                        data.getAttribute("android:mimeType").takeIf { it.isNotBlank() }
                    }
            }
            .toSet()
    }

    private fun Element.toXmlText(): String {
        val builder = StringBuilder(tagName)
        val attributes = attributes
        for (index in 0 until attributes.length) {
            val item = attributes.item(index)
            builder.append(' ').append(item.nodeName).append("=\"").append(item.nodeValue).append('"')
        }
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                builder.append('\n').append(child.toXmlText())
            }
        }
        return builder.toString()
    }
}
