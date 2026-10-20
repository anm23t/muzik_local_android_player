package org.muzzzlo.player.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FolderLabelTest {
    @Test
    fun `volume prefix and encoding are removed`() {
        assertEquals(
            "Documents/Backups",
            folderLabel("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FBackups"),
        )
    }

    @Test
    fun `root of a volume gives its name`() {
        assertEquals("primary", folderLabel("content://com.android.externalstorage.documents/tree/primary%3A"))
    }

    @Test
    fun `cyrillic names stay readable`() {
        assertEquals(
            "Музыка/Копии",
            folderLabel(
                "content://x/tree/primary%3A%D0%9C%D1%83%D0%B7%D1%8B%D0%BA%D0%B0%2F%D0%9A%D0%BE%D0%BF%D0%B8%D0%B8",
            ),
        )
    }

    @Test
    fun `something else is returned as it is`() {
        assertEquals("plain", folderLabel("plain"))
        assertEquals("%zz", folderLabel("a/%zz"))
    }
}
