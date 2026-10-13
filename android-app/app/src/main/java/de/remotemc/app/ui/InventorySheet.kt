package de.remotemc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.remotemc.app.R
import de.remotemc.app.data.InventoryItem
import de.remotemc.app.data.PlayerInventory
import de.remotemc.app.ui.util.errorText
import kotlinx.coroutines.CancellationException

/** Read-only look into a player's inventory and ender chest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventorySheet(player: String, load: suspend (String) -> PlayerInventory, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        InventoryContent(player, load)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryContent(player: String, load: suspend (String) -> PlayerInventory, initial: PlayerInventory? = null) {
    val context = LocalContext.current
    var inventory by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<InventoryItem?>(null) }
    LaunchedEffect(reload) {
        if (initial != null && reload == 0) return@LaunchedEffect
        try {
            inventory = load(player)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = context.errorText(e)
        }
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(player, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { selected = null; reload++ }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.action_refresh)) }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0; selected = null }, text = { Text(stringResource(R.string.players_inventory)) })
            Tab(selected = tab == 1, onClick = { tab = 1; selected = null }, text = { Text(stringResource(R.string.players_ender_chest)) })
        }
        val inv = inventory
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
            inv == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val pick: (InventoryItem?) -> Unit = { selected = it }
                if (tab == 0) {
                    Label(stringResource(R.string.inventory_armor))
                    // Helmet, chestplate, leggings, boots, then the off hand.
                    val armor = (3 downTo 0).map { slot -> inv.armor.firstOrNull { it.slot == slot } } + listOf(inv.offhand)
                    SlotRow(armor + List(4) { null }, selected, pick, placeholders = 5)
                    Label(stringResource(R.string.players_inventory))
                    (1..3).forEach { row -> SlotRow(slots(inv.items, row * 9, 9), selected, pick) }
                    Label(stringResource(R.string.inventory_hotbar))
                    SlotRow(slots(inv.items, 0, 9), selected, pick, highlight = inv.selectedSlot)
                } else {
                    (0..2).forEach { row -> SlotRow(slots(inv.enderChest, row * 9, 9), selected, pick) }
                }
                Spacer(Modifier.height(8.dp))
                ItemDetails(selected)
            }
        }
    }
}

private fun slots(items: List<InventoryItem>, from: Int, count: Int) =
    (from until from + count).map { slot -> items.firstOrNull { it.slot == slot } }

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun SlotRow(
    items: List<InventoryItem?>,
    selected: InventoryItem?,
    onPick: (InventoryItem?) -> Unit,
    highlight: Int = -1,
    /** Only the first n slots are drawn (armor row). */
    placeholders: Int = items.size,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, item ->
            Box(Modifier.weight(1f)) {
                if (i < placeholders) Slot(item, item != null && item == selected, i == highlight) { onPick(item) }
            }
        }
    }
}

@Composable
private fun Slot(item: InventoryItem?, selected: Boolean, held: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        held -> Color(0xFFFFFFFF)
        item != null && item.enchants.isNotEmpty() -> Color(0xFFB57BFF)
        else -> Color(0x33FFFFFF)
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
            .background(if (item == null) Color(0xFF2A2F2C) else itemColor(item.id))
            .border(if (selected || held) 2.dp else 1.dp, border, shape)
            .clickable(enabled = item != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            // Letters top left, amount bottom right (like the game), so both stay readable.
            Text(
                abbreviation(item), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(if (item.amount > 1) Alignment.TopStart else Alignment.Center)
                    .padding(start = if (item.amount > 1) 4.dp else 0.dp, top = if (item.amount > 1) 2.dp else 0.dp),
            )
            if (item.amount > 1) {
                Text(
                    item.amount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
                )
            }
            val max = item.maxDurability
            val left = item.durability
            if (max != null && left != null && max > 0 && left < max) {
                val fraction = left.toFloat() / max
                Box(Modifier.align(Alignment.BottomStart).padding(start = 3.dp, end = 3.dp, bottom = 3.dp).fillMaxWidth().height(2.dp).background(Color(0x66000000))) {
                    Box(
                        Modifier.fillMaxWidth(fraction).height(2.dp)
                            .background(if (fraction > 0.5f) Color(0xFF6FE38A) else if (fraction > 0.2f) Color(0xFFFFC857) else Color(0xFFFF6B6B)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDetails(item: InventoryItem?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (item == null) {
                Text(stringResource(R.string.inventory_tap_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(itemColor(item.id)))
                Spacer(Modifier.width(8.dp))
                Text(item.displayName + if (item.amount > 1) " ×${item.amount}" else "", style = MaterialTheme.typography.titleMedium)
            }
            Text(item.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.enchants.isNotEmpty()) {
                Text(
                    item.enchants.joinToString(", ") { InventoryItem.prettyId(it.substringBefore(' ')) + " " + it.substringAfter(' ') },
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB57BFF),
                )
            }
            if (item.durability != null && item.maxDurability != null) {
                Text(stringResource(R.string.inventory_durability, item.durability, item.maxDurability), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Two letters for the slot: "Diamond Sword" → "DS", "Oak Log" → "OL", "Stone" → "ST". */
internal fun abbreviation(item: InventoryItem): String {
    val words = InventoryItem.prettyId(item.id).split(' ').filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0][0]}${words.last()[0]}"
        words.isNotEmpty() -> words[0].take(2)
        else -> "?"
    }.uppercase()
}

/** A colour that hints at the material (diamond, gold, iron, wood, …). */
internal fun itemColor(id: String): Color {
    val name = id.substringAfter(':')
    return when {
        "netherite" in name -> Color(0xFF4A3F45)
        "diamond" in name -> Color(0xFF2E9C9A)
        "emerald" in name -> Color(0xFF1E8A4C)
        "gold" in name -> Color(0xFFB8901E)
        "iron" in name || "chainmail" in name -> Color(0xFF7D8590)
        "copper" in name -> Color(0xFFB0643E)
        "redstone" in name -> Color(0xFFA12A2A)
        "lapis" in name -> Color(0xFF2F4FA8)
        listOf("oak", "spruce", "birch", "jungle", "acacia", "cherry", "mangrove", "bamboo", "wood", "log", "plank", "stick").any { it in name } -> Color(0xFF8A6239)
        listOf("stone", "cobble", "deepslate", "andesite", "diorite", "granite").any { it in name } -> Color(0xFF6B6F6E)
        listOf("dirt", "grass", "mud").any { it in name } -> Color(0xFF6B4A2F)
        listOf("sand", "glass").any { it in name } -> Color(0xFF9E9467)
        listOf("bread", "apple", "beef", "pork", "chicken", "carrot", "potato", "steak", "cake", "cookie").any { it in name } -> Color(0xFFB0703A)
        "leather" in name -> Color(0xFF8A5235)
        else -> Color.hsv((name.hashCode().toLong() and 0xFFFF).toFloat() % 360f, 0.35f, 0.5f)
    }
}
