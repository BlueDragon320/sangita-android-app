package com.sangita.app.data.local.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StorageFileNamesTest {
    @Test fun `distinct paths that collided under the old naming scheme stay distinct`() {
        assertNotEquals(trackStorageKey("A/B_C.mp3"), trackStorageKey("A_B/C.mp3"))
    }

    @Test fun `storage key is stable and handles non-ASCII paths`() {
        assertEquals(trackStorageKey("ಕನ್ನಡ/ಹಾಡು.mp3"), trackStorageKey("ಕನ್ನಡ/ಹಾಡು.mp3"))
        assertEquals(64, trackStorageKey("ಕನ್ನಡ/ಹಾಡು.mp3").length)
    }
}
