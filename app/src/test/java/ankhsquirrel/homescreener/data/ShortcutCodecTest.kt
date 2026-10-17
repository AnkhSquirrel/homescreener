package ankhsquirrel.homescreener.data

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutCodecTest {

    private val entry = ShortcutEntry(
        id = "0b6f1c1e-1111-4222-8333-944445555666",
        uri = "content://com.android.externalstorage.documents/document/primary%3ADocuments%2Fmanual.pdf",
        mimeType = "application/pdf",
        fileName = "manual.pdf",
        label = "Manual",
        createdAt = 1_760_000_000_000,
    )

    @Test
    fun `round trips entries`() {
        val entries = listOf(entry, entry.copy(id = "other", label = "Other"))
        assertEquals(entries, ShortcutCodec.decode(ShortcutCodec.encode(entries)))
    }

    @Test
    fun `ignores unknown fields from newer versions`() {
        val text = """{"version":1,"future":true,"entries":[{"id":"a","uri":"content://x/1","mimeType":"text/plain",""" +
            """"fileName":"a.txt","label":"a","createdAt":1,"color":"red"}]}"""
        assertEquals("a", ShortcutCodec.decode(text).single().id)
    }

    @Test(expected = SerializationException::class)
    fun `rejects corrupt input`() {
        ShortcutCodec.decode("{not json")
    }

    @Test
    fun `default label strips extension`() {
        assertEquals("manual", defaultLabel("manual.pdf"))
        assertEquals("archive.tar", defaultLabel("archive.tar.gz"))
        assertEquals("README", defaultLabel("README"))
        assertEquals(".bashrc", defaultLabel(".bashrc"))
    }
}
