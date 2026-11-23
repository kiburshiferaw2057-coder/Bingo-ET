package et.bingo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val Navy = Color(0xFF123B70)
private val Blue = Color(0xFF1769AA)
private val Pale = Color(0xFFEAF3FC)
private val Green = Color(0xFF16834A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BingoTheme { BingoApp() } }
    }
}

@Composable
private fun BingoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, secondary = Navy, background = Color(0xFFF7FAFE)),
        content = content
    )
}

private enum class Mode(val en: String, val am: String) {
    ONE("One line", "አንድ መስመር"),
    THREE("Three lines", "ሶስት መስመሮች"),
    STAR("Four-star", "አራት ኮከቦች")
}

private data class BingoCard(val id: Int, val values: List<Int>, val marked: Set<Int> = emptySet()) {
    fun value(row: Int, col: Int) = values[row * 5 + col]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BingoApp() {
    var am by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.ONE) }
    var cards by remember { mutableStateOf(listOf(makeCard(1))) }
    var called by remember { mutableStateOf(emptyList<Int>()) }
    var status by remember { mutableStateOf("Tap Draw number to start a free practice game.") }
    var points by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }

    fun newGame() {
        called = emptyList()
        cards = listOf(makeCard(1))
        status = if (am) "አዲስ ጨዋታ ተጀምሯል።" else "New free practice game started."
        gameOver = false
    }

    fun addCard() {
        if (cards.size >= 10) {
            status = if (am) "በዚህ ሙከራ እስከ 10 ካርዶች ብቻ ነው።" else "This prototype allows up to 10 cards per game."
        } else {
            cards = cards + makeCard((cards.maxOfOrNull { it.id } ?: 0) + 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BINGO ET", color = Navy, fontWeight = FontWeight.ExtraBold) },
                actions = {
                    TextButton(onClick = { am = !am }) {
                        Text(if (am) "English" else "አማርኛ")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Pale), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(if (am) "እንኳን ወደ BINGO ET በደህና መጡ" else "Welcome to Bingo ET", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text(if (am) "ነፃ የቢንጎ ልምምድ • ነጥቦች ብቻ • እውነተኛ ገንዘብ የለም" else "Free Bingo practice • Points only • No real-money play", color = Navy)
                        Spacer(Modifier.height(8.dp))
                        Text((if (am) "ነጥቦች: " else "Practice points: ") + points, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text(if (am) "የጨዋታ አይነት" else "Game mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Mode.values().forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { mode = m }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(if (am) m.am else m.en)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!gameOver && called.size < 75) {
                            val remaining = (1..75).filterNot { it in called }
                            called = called + remaining.random()
                            status = if (am) "የተጠራ ቁጥር: ${called.last()}" else "Last called: ${called.last()}"
                        } else if (called.size == 75) {
                            status = if (am) "ሁሉም ቁጥሮች ተጠርተዋል።" else "All 75 numbers have been called."
                        }
                    }, modifier = Modifier.weight(1f), enabled = !gameOver && called.size < 75) {
                        Text(if (am) "ቁጥር ጥራ" else "Draw number")
                    }
                    OutlinedButton(onClick = { newGame() }, modifier = Modifier.weight(1f)) {
                        Text(if (am) "አዲስ ጨዋታ" else "New game")
                    }
                }
                Text(status, color = Blue, fontWeight = FontWeight.SemiBold)
                if (called.isNotEmpty()) {
                    Text((if (am) "የመጨረሻ ቁጥር: " else "Last number: ") + called.last(), fontSize = 25.sp, color = Green, fontWeight = FontWeight.ExtraBold)
                }
            }
            item {
                Text(if (am) "የተጠሩ ቁጥሮች (${called.size}/75)" else "Called numbers (${called.size}/75)", fontWeight = FontWeight.Bold)
                Text(called.sorted().joinToString("  ").ifBlank { if (am) "ገና ቁጥር አልተጠራም" else "No numbers called yet" })
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (am) "የእርስዎ ካርዶች" else "Your cards", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { addCard() }) { Text(if (am) "ካርድ ጨምር" else "Add card") }
                }
            }
            itemsIndexed(cards, key = { _, card -> card.id }) { _, card ->
                BingoCardView(card = card, called = called, onToggle = { n ->
                    cards = cards.map { c -> if (c.id == card.id) c.copy(marked = if (n in c.marked) c.marked - n else c.marked + n) else c }
                }, am = am)
                val active = cards.first { it.id == card.id }
                val win = hasWon(active, called, mode)
                if (win) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F6EC))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(if (am) "BINGO! ካርድ #${card.id} አሸንፏል!" else "BINGO! Card #${card.id} wins!", color = Green, fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                if (!gameOver) {
                                    points += when (mode) { Mode.ONE -> 10; Mode.THREE -> 25; Mode.STAR -> 15 }
                                    gameOver = true
                                    status = if (am) "አሸናፊው ተረጋግጧል። ነጥቦች ተጨምረዋል።" else "Winner verified. Practice points added."
                                }
                            }, enabled = !gameOver) { Text(if (am) "አሸናፊነትን አረጋግጥ" else "Verify Bingo") }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Pale)) {
                    Text(
                        if (am) "ማሳሰቢያ: ይህ የሙከራ መተግበሪያ ነው። ነጥቦች ገንዘብ አይደሉም፤ ክፍያ፣ ውርርድ ወይም የገንዘብ ሽልማት የለም።" else "Note: This is a prototype. Points have no cash value; there are no payments, wagers or cash prizes.",
                        Modifier.padding(14.dp), fontSize = 12.sp, color = Navy
                    )
                }
            }
        }
    }
}

@Composable
private fun BingoCardView(card: BingoCard, called: List<Int>, onToggle: (Int) -> Unit, am: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text((if (am) "ካርድ #" else "Card #") + card.id, fontWeight = FontWeight.Bold, color = Navy)
            Row(Modifier.fillMaxWidth()) {
                listOf("B", "I", "N", "G", "O").forEach { letter ->
                    Box(Modifier.weight(1f).padding(2.dp).background(Blue, RoundedCornerShape(6.dp)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(letter, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            for (r in 0 until 5) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 5) {
                        val n = card.value(r, c)
                        val free = r == 2 && c == 2
                        val marked = free || n in card.marked || n in called
                        Box(
                            Modifier.weight(1f).padding(2.dp).aspectRatio(1f)
                                .background(if (marked) Color(0xFFCDEDD8) else Pale, RoundedCornerShape(7.dp))
                                .border(1.dp, if (marked) Green else Color(0xFFD3E0EF), RoundedCornerShape(7.dp))
                                .clickable(enabled = !free) { onToggle(n) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (free) "★" else n.toString(), fontWeight = FontWeight.Bold, color = if (marked) Green else Navy, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun makeCard(id: Int): BingoCard {
    val ranges = listOf(1..15, 16..30, 31..45, 46..60, 61..75)
    val columns = ranges.map { range -> range.shuffled().take(5) }
    val values = (0 until 5).flatMap { row -> (0 until 5).map { col -> columns[col][row] } }.toMutableList()
    return BingoCard(id = id, values = values)
}

private fun hasWon(card: BingoCard, called: List<Int>, mode: Mode): Boolean {
    // Only server/game-called numbers count toward a win; manual marks cannot validate a claim.
    val active = called.toSet() + 0
    val lineCount = (0 until 5).count { row -> (0 until 5).all { col -> (row == 2 && col == 2) || card.value(row, col) in active } } +
        (0 until 5).count { col -> (0 until 5).all { row -> (row == 2 && col == 2) || card.value(row, col) in active } } +
        (if ((0 until 5).all { i -> i == 2 || card.value(i, i) in active }) 1 else 0) +
        (if ((0 until 5).all { i -> i == 2 || card.value(i, 4 - i) in active }) 1 else 0)
    val stars = listOf(card.value(0,0), card.value(0,4), card.value(4,0), card.value(4,4))
    return when (mode) {
        Mode.ONE -> lineCount >= 1
        Mode.THREE -> lineCount >= 3
        Mode.STAR -> stars.all { it in active }
    }
}
