package de.remotemc.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandCompletionTest {
    private val players = listOf("Steve", "alex", "Sam")
    private val worlds = listOf("world", "world_nether")

    private fun suggest(buffer: String) = CommandCompletion.suggest(buffer, players, worlds)

    @Test
    fun nothingWhileTypingTheCommandName() {
        assertEquals(emptyList<String>(), suggest("gamem"))
    }

    @Test
    fun knownArgumentsByPosition() {
        assertEquals(listOf("creative"), suggest("gamemode cr"))
        assertEquals(listOf("adventure", "creative", "spectator", "survival"), suggest("/gamemode "))
        assertEquals(listOf("Sam", "Steve"), suggest("gamemode creative S"))
        assertEquals(listOf("hard"), suggest("difficulty h"))
        assertEquals(listOf("night", "noon"), suggest("time set n"))
        assertEquals(listOf("alex"), suggest("minecraft:kick a"))
        assertEquals(listOf("world_nether"), suggest("mv world_"))
    }

    @Test
    fun unknownCommandsGetPlayerNamesOnceSomethingIsTyped() {
        assertEquals(emptyList<String>(), suggest("lp user "))
        assertEquals(listOf("Steve"), suggest("lp user st"))
    }

    @Test
    fun theWordItselfIsNotSuggested() {
        assertEquals(emptyList<String>(), suggest("gamemode creative Steve"))
    }

    @Test
    fun mergeKeepsServerFirstWithoutDuplicates() {
        assertEquals(listOf("creative", "Custom", "survival"), CommandCompletion.merge(listOf("creative", "Custom"), listOf("Creative", "survival")))
    }

    @Test
    fun cacheServesLongerInputsOfTheSameArgument() {
        val cache = CompletionCache()
        assertNull(cache.lookup("gamemode cr"))
        cache.put("gamemode ", listOf("adventure", "creative", "spectator", "survival"))
        assertEquals(listOf("creative"), cache.lookup("gamemode cr"))
        assertEquals(listOf("adventure", "creative", "spectator", "survival"), cache.lookup("gamemode "))
        // The next argument is something else: no answer yet.
        assertNull(cache.lookup("gamemode creative "))
    }

    @Test
    fun cacheForgetsTheOldest() {
        val cache = CompletionCache(maxEntries = 2)
        cache.put("a ", listOf("1"))
        cache.put("b ", listOf("2"))
        cache.put("c ", listOf("3"))
        assertNull(cache.lookup("a "))
        assertEquals(listOf("3"), cache.lookup("c "))
    }
}
