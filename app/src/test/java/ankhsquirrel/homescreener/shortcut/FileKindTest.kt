package ankhsquirrel.homescreener.shortcut

import org.junit.Assert.assertEquals
import org.junit.Test

class FileKindTest {

    @Test
    fun `classifies by mime type`() {
        assertEquals(FileKind.PDF, FileKind.of("application/pdf", "x"))
        assertEquals(FileKind.IMAGE, FileKind.of("image/jpeg", "x"))
        assertEquals(FileKind.VIDEO, FileKind.of("video/mp4", "x"))
        assertEquals(FileKind.AUDIO, FileKind.of("audio/ogg", "x"))
        assertEquals(FileKind.SPREADSHEET, FileKind.of("text/csv", "x"))
        assertEquals(FileKind.DOCUMENT, FileKind.of("application/vnd.oasis.opendocument.text", "x"))
        assertEquals(FileKind.TEXT, FileKind.of("text/plain", "x"))
    }

    @Test
    fun `falls back to extension for generic mime types`() {
        assertEquals(FileKind.PDF, FileKind.of("application/octet-stream", "scan.PDF"))
        assertEquals(FileKind.ARCHIVE, FileKind.of("application/octet-stream", "backup.7z"))
        assertEquals(FileKind.PRESENTATION, FileKind.of("application/octet-stream", "talk.odp"))
        assertEquals(FileKind.OTHER, FileKind.of("application/octet-stream", "blob"))
    }

    @Test
    fun `badge uses short extensions only`() {
        assertEquals("DOCX", FileKind.badge("report.docx", FileKind.DOCUMENT))
        assertEquals("FILE", FileKind.badge("blob", FileKind.OTHER))
        assertEquals("ZIP", FileKind.badge("photos.verylongext", FileKind.ARCHIVE))
    }
}
