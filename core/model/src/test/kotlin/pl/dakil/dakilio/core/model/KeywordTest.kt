package pl.dakil.dakilio.core.model

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordTest {
    private fun kw(word: String, approximate: Boolean = false, tagId: Long = 1) = Keyword(id = 0, tagId = tagId, word = word, approximate = approximate)
    private fun hit(text: String, keyword: Keyword) = Keywords.matches(Keywords.tokenize(text), keyword)

    @Test fun `exact keyword matches the whole word only`() {
        assertTrue(hit("Went for a bike ride", kw("bike")))
        assertFalse(hit("Saw a biker", kw("bike")))
    }

    @Test fun `run does not match runway unless approximate`() {
        assertFalse(hit("on the runway", kw("run")))
        assertTrue(hit("on the runway", kw("run", approximate = true)))
    }

    @Test fun `approximate keyword matches inflected forms`() {
        val rower = kw("rower", approximate = true)
        assertTrue(hit("Jechałam rowerem", rower))
        assertTrue(hit("na rowerze", rower))
        assertFalse(hit("narower", rower))
    }

    @Test fun `matching ignores case and accents`() {
        assertTrue(hit("ŁÓDŹ była piękna", kw("lodz")))
        assertTrue(hit("lodz", kw("Łódź")))
    }

    @Test fun `punctuation separates words`() = assertTrue(hit("bike!", kw("bike")))

    @Test fun `phrases need consecutive words`() {
        assertTrue(hit("we had ice cream today", kw("ice cream")))
        assertFalse(hit("ice was cold, cream was warm", kw("ice cream")))
        assertTrue(hit("ice creamy", kw("ice cream", approximate = true)))
        assertFalse(hit("icy creamy", kw("ice cream", approximate = true)))
    }

    @Test fun `match returns tag ids`() {
        val keywords = listOf(kw("bike", tagId = 1), kw("cycling", tagId = 1), kw("friends", tagId = 2), kw("rain", tagId = 3))
        assertEquals(setOf(1L, 2L), Keywords.match("Cycling with friends", keywords))
    }

    @Test fun `a blank keyword never matches`() = assertFalse(hit("anything", kw("  !! ")))

    @Test fun `the word being typed is ignored until a separator follows`() {
        assertEquals("went ", Keywords.settledText("went bik", 8).trimEnd() + " ")
        assertFalse(Keywords.match(Keywords.settledText("went bike", 9), listOf(kw("bike"))).isNotEmpty())
        assertTrue(Keywords.match(Keywords.settledText("went bike ", 10), listOf(kw("bike"))).isNotEmpty())
    }

    @Test fun `editing a word in the middle hides only that word`() {
        val text = "bike and rain"
        // Cursor after "bik" inside "bike".
        val settled = Keywords.settledText(text, 3)
        assertEquals(setOf(2L), Keywords.match(settled, listOf(kw("bike", tagId = 1), kw("rain", tagId = 2))))
    }

    @Test fun `cursor after a separator keeps the whole text`() = assertEquals("bike, ", Keywords.settledText("bike, ", 6))

    @Test fun `key folds for duplicate checks`() = assertEquals(Keywords.key("Ice  Créam"), Keywords.key("ice cream"))

    @Test fun `past matches skip tagged posts and inactive tags`() {
        val cycling = Tag(id = 1, groupId = 1, label = "Cycling", iconKey = "bike", position = 0)
        fun post(id: Long, note: String, tags: List<Tag> = emptyList()) =
            Post(id = id, dateTime = LocalDateTime.of(2026, 1, 1, 12, 0), mood = null, title = null, note = note, tags = tags, diaryId = 1, photos = emptyList())
        val posts = listOf(
            post(1, "bike ride"),
            post(2, "bike again", tags = listOf(cycling)),
            post(3, "nothing here"),
            post(4, "rain and bike"),
        )
        val keywords = listOf(kw("bike", tagId = 1), kw("rain", tagId = 9))
        assertEquals(mapOf(1L to listOf(1L, 4L)), Keywords.pastMatches(posts, keywords, activeTagIds = setOf(1L)))
    }

    private fun highlighted(text: String, vararg keywords: Keyword): List<String> =
        Keywords.matchRanges(text, keywords.toList()).map { text.substring(it) }

    @Test fun `ranges cover exact words only`() =
        assertEquals(listOf("bike"), highlighted("A bike, not a biker.", kw("bike")))

    @Test fun `approximate ranges cover the whole inflected word`() =
        assertEquals(listOf("rowerem", "Rower"), highlighted("Jechałam rowerem. Rower był super", kw("rower", approximate = true)))

    @Test fun `ranges point into the original accented text`() =
        assertEquals(listOf("Zażółć"), highlighted("Dziś: Zażółć gęślą", kw("zazolc")))

    @Test fun `a phrase range spans its words`() =
        assertEquals(listOf("ice  cream"), highlighted("We had ice  cream and ice", kw("ice cream")))

    @Test fun `overlapping ranges merge`() =
        assertEquals(listOf("ice cream"), highlighted("ice cream", kw("ice cream", tagId = 1), kw("cream", tagId = 1)))

    @Test fun `ranges stay right after a character that folds into several`() =
        assertEquals(listOf("bike"), highlighted("ǅ bike", kw("bike")))

    @Test fun `spanned tokens fold like tokenize`() {
        val text = "Zażółć, GĘŚLĄ jaźń! ǅ ﬁne 42"
        assertEquals(Keywords.tokenize(text), Keywords.spannedTokens(text).map { it.word })
    }
}
