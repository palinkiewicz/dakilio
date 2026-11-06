package pl.dakil.dakilio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagLimitTest {
    @Test
    fun keysRoundTrip() {
        (0..TagLimit.MAX).map(TagLimit::of).plus(TagLimit.All).forEach { limit ->
            assertEquals(limit, TagLimit.parse(limit.key))
        }
    }

    @Test
    fun oldChoiceNamesStillRead() {
        assertEquals(TagLimit.of(3), TagLimit.parse("Three"))
        assertEquals(TagLimit.of(6), TagLimit.parse("Six"))
        assertEquals(TagLimit.of(TagLimit.MAX), TagLimit.parse("Twelve"))
        assertEquals(TagLimit.All, TagLimit.parse("All"))
    }

    @Test
    fun outOfRangeIsClampedAndJunkIgnored() {
        assertEquals(TagLimit.of(TagLimit.MAX), TagLimit.parse("40"))
        assertNull(TagLimit.parse("-2"))
        assertNull(TagLimit.parse("many"))
    }
}
