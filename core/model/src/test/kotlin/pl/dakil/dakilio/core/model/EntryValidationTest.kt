package pl.dakil.dakilio.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryValidationTest {
    private fun canSave(
        hasMood: Boolean = false,
        tags: Int = 0,
        title: String = "",
        note: String = "",
        photos: Int = 0,
        diaries: Int = 1,
    ) = EntryValidation.canSave(hasMood, tags, title, note, photos, diaries)

    @Test fun `mood alone is enough`() = assertTrue(canSave(hasMood = true))

    @Test fun `tags alone are enough`() = assertTrue(canSave(tags = 2))

    @Test fun `skipping mood and tags requires body content`() {
        assertFalse(canSave())
        assertFalse(canSave(title = "   ", note = "\n"))
        assertTrue(canSave(title = "Hike"))
        assertTrue(canSave(note = "Felt good"))
        assertTrue(canSave(photos = 1))
    }

    @Test fun `an entry must belong to a diary`() = assertFalse(canSave(hasMood = true, diaries = 0))
}
