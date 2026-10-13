package de.remotemc.app.data

import de.remotemc.app.ui.abbreviation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerDetailsTest {
    /** What the plugin answers (taken from a real Paper 1.21.4 server). */
    private val json = """
        {"player":"Steve","selectedSlot":1,
         "items":[{"id":"minecraft:diamond_sword","amount":1,"enchants":["sharpness 3"],"durability":1461,"maxDurability":1561,"slot":0},
                  {"id":"minecraft:oak_log","amount":32,"slot":1},
                  {"id":"minecraft:name_tag","amount":1,"name":"Bello","slot":20}],
         "armor":[{"id":"minecraft:iron_helmet","amount":1,"durability":165,"maxDurability":165,"slot":3}],
         "offhand":[],
         "enderChest":[{"id":"minecraft:emerald","amount":5,"slot":0}]}
    """.trimIndent()

    @Test
    fun inventoryFromJson() {
        val inv = PlayerInventory.fromJson(JSONObject(json))
        assertEquals(3, inv.items.size)
        val sword = inv.items[0]
        assertEquals("Diamond Sword", sword.displayName)
        assertEquals(listOf("sharpness 3"), sword.enchants)
        assertEquals(1461, sword.durability)
        assertEquals("Bello", inv.items[2].displayName)
        assertNull(inv.items[1].durability)
        assertEquals(3, inv.armor.single().slot)
        assertNull(inv.offhand)
        assertEquals(5, inv.enderChest.single().amount)
        assertEquals(1, inv.selectedSlot)
    }

    @Test
    fun abbreviations() {
        assertEquals("DS", abbreviation(InventoryItem(0, "minecraft:diamond_sword", 1)))
        assertEquals("ST", abbreviation(InventoryItem(0, "minecraft:stone", 1)))
        assertEquals("NT", abbreviation(InventoryItem(0, "minecraft:netherite_upgrade_smithing_template", 1)))
    }

    @Test
    fun joinTimesSurviveTheSnapshot() {
        val p = PlayerInfo("Steve", "u", "world", 0, 64, 0, 20.0, 20.0, 20, 1, 30, "SURVIVAL", false, firstPlayed = 1_700_000_000_000, onlineSince = 1_760_000_000_000)
        val back = PlayerInfo.listFromJson(org.json.JSONArray().put(p.toJson())).single()
        assertEquals(p, back)
    }

    @Test
    fun notesPerServerAndPlayer() {
        val notes = PlayerNotes(RuntimeEnvironment.getApplication().getSharedPreferences("test-notes", 0))
        notes.set("s1", "u1", "Griefed the spawn once")
        notes.set("s2", "u1", "other server")
        assertEquals("Griefed the spawn once", notes.get("s1", "u1"))
        assertEquals("", notes.get("s1", "u2"))
        notes.deleteServer("s1")
        assertEquals("", notes.get("s1", "u1"))
        assertEquals("other server", notes.get("s2", "u1"))
        notes.set("s2", "u1", "  ")
        assertEquals("", notes.get("s2", "u1"))
        notes.set("s2", "u1", "x".repeat(5000))
        assertEquals(PlayerNotes.MAX_LENGTH, notes.get("s2", "u1").length)
    }
}
