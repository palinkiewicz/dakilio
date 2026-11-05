package pl.dakil.dakilio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSizingTest {
    @Test fun `landscape scales by its width`() =
        assertEquals(PixelSize(1280, 960), PhotoSizing.scaledSize(4000, 3000, 1280))

    @Test fun `portrait scales by its height`() =
        assertEquals(PixelSize(960, 1280), PhotoSizing.scaledSize(3000, 4000, 1280))

    @Test fun `a photo that already fits is left alone`() {
        assertNull(PhotoSizing.scaledSize(1280, 720, 1280))
        assertNull(PhotoSizing.scaledSize(800, 600, 1280))
    }

    @Test fun `sides are rounded and never drop below one pixel`() {
        assertEquals(PixelSize(640, 427), PhotoSizing.scaledSize(1080, 721, 640))
        assertEquals(PixelSize(640, 1), PhotoSizing.scaledSize(10_000, 2, 640))
    }

    @Test fun `original keeps photos up to the pixel ceiling`() {
        assertNull(PhotoSizing.scaledSize(4000, 3000, null))
        assertNull(PhotoSizing.scaledSize(4624, 3468, null))
    }

    @Test fun `original scales a huge photo down to the pixel ceiling`() {
        val size = PhotoSizing.scaledSize(16_320, 12_240, null)!!
        assertTrue(size.width.toLong() * size.height <= PhotoSizing.MAX_ORIGINAL_PIXELS + size.width + size.height)
        assertEquals(16_320.0 / 12_240, size.width.toDouble() / size.height, 0.01)
    }

    @Test fun `invalid sizes are left alone`() = assertNull(PhotoSizing.scaledSize(0, 100, 640))

    @Test fun `exceeds follows the chosen size`() {
        assertTrue(PhotoSizing.exceeds(PixelSize(1080, 721), PhotoSize.Px960))
        assertFalse(PhotoSizing.exceeds(PixelSize(1080, 721), PhotoSize.Px1280))
        assertFalse(PhotoSizing.exceeds(PixelSize(4000, 3000), PhotoSize.Original))
    }
}
