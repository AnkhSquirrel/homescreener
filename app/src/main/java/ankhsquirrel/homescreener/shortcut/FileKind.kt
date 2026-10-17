package ankhsquirrel.homescreener.shortcut

/** Coarse file category, used for fallback icons when no thumbnail can be rendered. */
enum class FileKind(val color: Int, val defaultBadge: String) {
    PDF(0xFFD93025.toInt(), "PDF"),
    IMAGE(0xFF188038.toInt(), "IMG"),
    VIDEO(0xFF8430CE.toInt(), "VID"),
    AUDIO(0xFFE8710A.toInt(), "AUD"),
    SPREADSHEET(0xFF0F9D58.toInt(), "XLS"),
    PRESENTATION(0xFFF29900.toInt(), "PPT"),
    DOCUMENT(0xFF1A73E8.toInt(), "DOC"),
    ARCHIVE(0xFF795548.toInt(), "ZIP"),
    TEXT(0xFF5F6368.toInt(), "TXT"),
    OTHER(0xFF455A64.toInt(), "FILE");

    companion object {
        private val ARCHIVE_EXT = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "xz", "bz2", "zst")
        private val SPREADSHEET_EXT = setOf("xls", "xlsx", "ods", "csv", "tsv")
        private val PRESENTATION_EXT = setOf("ppt", "pptx", "odp", "key")
        private val DOCUMENT_EXT = setOf("doc", "docx", "odt", "rtf", "epub")
        private val TEXT_EXT = setOf("txt", "md", "log")

        fun of(mimeType: String, fileName: String): FileKind {
            val mime = mimeType.lowercase()
            val ext = extension(fileName)
            return when {
                mime == "application/pdf" || ext == "pdf" -> PDF
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                ext in ARCHIVE_EXT || "zip" in mime || "compressed" in mime || "x-tar" in mime -> ARCHIVE
                ext in SPREADSHEET_EXT || "spreadsheet" in mime || "excel" in mime || mime == "text/csv" -> SPREADSHEET
                ext in PRESENTATION_EXT || "presentation" in mime || "powerpoint" in mime -> PRESENTATION
                ext in DOCUMENT_EXT || "wordprocessing" in mime || "msword" in mime ||
                    "opendocument.text" in mime || "epub" in mime -> DOCUMENT
                mime.startsWith("text/") || ext in TEXT_EXT -> TEXT
                else -> OTHER
            }
        }

        /** Short uppercase label for a glyph icon: the file extension if it is short enough. */
        fun badge(fileName: String, kind: FileKind): String =
            extension(fileName).takeIf { it.length in 1..4 && it.all(Char::isLetterOrDigit) }
                ?.uppercase() ?: kind.defaultBadge

        private fun extension(fileName: String): String =
            fileName.substringAfterLast('.', "").lowercase()
    }
}
