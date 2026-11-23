package et.bingo.app

import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Navy = Color(0xFF123B70)
private val Blue = Color(0xFF1769AA)
private val Pale = Color(0xFFEAF3FC)
private val Green = Color(0xFF16834A)
private val AmberBg = Color(0xFFFFF8E1)
private val AmberBorder = Color(0xFFF59E0B)

private const val CARD_PRICE_BIRR = 10.0
private const val WINNER_SHARE_RATIO = 0.80
private const val SELECTION_INTERVAL_SEC = 120 // Every 2 minutes after a winner
private const val ALARM_COUNTDOWN_SEC = 10   // 10-second alarm before starting game
private const val CALL_INTERVAL_MS = 2000L   // 2-second interval between number calls

private enum class GamePhase {
    CARD_SELECTION,
    ALARM_COUNTDOWN,
    PLAYING,
    FINISHED
}

private enum class Mode(val en: String, val am: String) {
    ONE("One line", "አንድ መስመር"),
    THREE("Three lines", "ሶስት መስመሮች"),
    STAR("Four-star", "አራት ኮከቦች")
}

private data class BingoCard(
    val id: Int,
    val values: List<Int>,
    val marked: Set<Int> = emptySet()
) {
    fun value(row: Int, col: Int): Int = values[row * 5 + col]
}

private data class PlayerEntry(
    val name: String,
    val card: BingoCard,
    val isCurrentUser: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BingoTheme { BingoApp() } }
    }
}

@Composable
private fun BingoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Navy,
            background = Color(0xFFF7FAFE)
        ),
        content = content
    )
}

private fun playAlarmTone() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
    } catch (_: Exception) {
    }
}

private fun playCallTone() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BingoApp() {
    var am by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.THREE) }
    var phase by remember { mutableStateOf(GamePhase.CARD_SELECTION) }
    var nextCardId by remember { mutableIntStateOf(2) }
    var entries by remember {
        mutableStateOf(
            listOf(
                PlayerEntry(name = "You", card = makeCard(1), isCurrentUser = true)
            )
        )
    }
    var called by remember { mutableStateOf(emptyList<Int>()) }
    var statusKey by remember { mutableStateOf("select_cards") }
    var points by remember { mutableIntStateOf(0) }
    var totalPotBirr by remember { mutableDoubleStateOf(10.0) }
    var winnerPayoutEachBirr by remember { mutableDoubleStateOf(0.0) }
    var winningCardIds by remember { mutableStateOf(emptyList<Int>()) }

    var selectionSecondsLeft by remember { mutableIntStateOf(SELECTION_INTERVAL_SEC) }
    var alarmSecondsLeft by remember { mutableIntStateOf(ALARM_COUNTDOWN_SEC) }
    var autoCallEnabled by remember { mutableStateOf(true) }

    val playerCards = remember(entries) { entries.filter { it.isCurrentUser }.map { it.card } }

    fun recalculatePot(currentEntries: List<PlayerEntry>) {
        totalPotBirr = currentEntries.size * CARD_PRICE_BIRR
    }

    fun startAlarmCountdown() {
        phase = GamePhase.ALARM_COUNTDOWN
        alarmSecondsLeft = ALARM_COUNTDOWN_SEC
        statusKey = "alarm_started"
        playAlarmTone()
    }

    fun startPlayingRound() {
        called = emptyList()
        winningCardIds = emptyList()
        winnerPayoutEachBirr = 0.0
        phase = GamePhase.PLAYING
        autoCallEnabled = true
        statusKey = "playing"
    }

    fun prepareNextCardSelectionCycle() {
        // After winner: switch from Three lines to One line if in THREE mode
        if (mode == Mode.THREE) {
            mode = Mode.ONE
        }
        val nextIdStart = nextCardId
        val freshEntries = listOf(
            PlayerEntry(name = "You", card = makeCard(nextIdStart), isCurrentUser = true)
        )
        nextCardId = nextIdStart + 1
        entries = freshEntries
        recalculatePot(freshEntries)
        called = emptyList()
        winningCardIds = emptyList()
        winnerPayoutEachBirr = 0.0
        selectionSecondsLeft = SELECTION_INTERVAL_SEC
        phase = GamePhase.CARD_SELECTION
        statusKey = "new_cycle_selection"
    }

    fun addPlayerCard() {
        if (phase != GamePhase.CARD_SELECTION) return
        if (playerCards.size >= 10) {
            statusKey = "max_cards"
            return
        }
        val card = makeCard(nextCardId)
        nextCardId += 1
        val updated = entries + PlayerEntry(name = "You", card = card, isCurrentUser = true)
        entries = updated
        recalculatePot(updated)
        statusKey = "card_added"
    }

    fun addPracticeBotCard() {
        if (phase != GamePhase.CARD_SELECTION) return
        if (entries.size >= 20) {
            statusKey = "max_total_cards"
            return
        }
        val botIndex = entries.count { !it.isCurrentUser } + 1
        val card = makeCard(nextCardId)
        nextCardId += 1
        val updated = entries + PlayerEntry(name = "Player $botIndex", card = card, isCurrentUser = false)
        entries = updated
        recalculatePot(updated)
        statusKey = "bot_added"
    }

    fun evaluateWinners() {
        if (phase != GamePhase.PLAYING) return
        val winners = entries.filter { hasWon(it.card, called, mode) }
        if (winners.isEmpty()) {
            statusKey = "no_winner_yet"
            return
        }
        val winIds = winners.map { it.card.id }
        val totalWinnerPool = totalPotBirr * WINNER_SHARE_RATIO
        val perWinnerShare = totalWinnerPool / winners.size

        winningCardIds = winIds
        winnerPayoutEachBirr = perWinnerShare
        val userWinsCount = winners.count { it.isCurrentUser }
        if (userWinsCount > 0) {
            points += (perWinnerShare * userWinsCount).toInt()
        }
        phase = GamePhase.FINISHED
        autoCallEnabled = false
        selectionSecondsLeft = SELECTION_INTERVAL_SEC
        statusKey = "winners_declared"
        playAlarmTone()
    }

    // 2-minute (120s) timer after a game finishes to select cards for the next round
    LaunchedEffect(phase) {
        if (phase == GamePhase.FINISHED) {
            selectionSecondsLeft = SELECTION_INTERVAL_SEC
            while (selectionSecondsLeft > 0) {
                delay(1000L)
                selectionSecondsLeft -= 1
            }
            prepareNextCardSelectionCycle()
        }
    }

    // 10-second alarm countdown before starting the game
    LaunchedEffect(phase) {
        if (phase == GamePhase.ALARM_COUNTDOWN) {
            alarmSecondsLeft = ALARM_COUNTDOWN_SEC
            while (alarmSecondsLeft > 0) {
                delay(1000L)
                alarmSecondsLeft -= 1
                playAlarmTone()
            }
            startPlayingRound()
        }
    }

    // Automatic number calling every 2 seconds while playing
    LaunchedEffect(phase, autoCallEnabled) {
        while (phase == GamePhase.PLAYING && autoCallEnabled && called.size < 75) {
            delay(CALL_INTERVAL_MS)
            if (phase == GamePhase.PLAYING && autoCallEnabled && called.size < 75) {
                val remaining = (1..75).filterNot { it in called }
                called = called + remaining.random()
                playCallTone()
                statusKey = "drawn"

                val autoWinners = entries.filter { hasWon(it.card, called, mode) }
                if (autoWinners.isNotEmpty()) {
                    evaluateWinners()
                    break
                }
            }
        }
        if (phase == GamePhase.PLAYING && called.size == 75 && winningCardIds.isEmpty()) {
            statusKey = "all_drawn"
        }
    }

    val statusText = when (statusKey) {
        "select_cards" -> if (am) "ካርድ ይምረጡ (1 ካርድ = 10 ብር) እና የ10 ሰከንድ ማንቂያ ቆጠራ ይጀምሩ።"
        else "Select cards (1 card = 10 Birr) and start the 10s alarm countdown."
        "card_added" -> if (am) "አዲስ ካርድ ተጨምሯል (+10 ብር)።" else "New player card added (+10 Birr)."
        "bot_added" -> if (am) "ተጫዋች ካርድ ተጨምሯል (+10 ብር)።" else "Opponent card added (+10 Birr)."
        "max_cards" -> if (am) "በአንድ ጨዋታ እስከ 10 የራስዎ ካርዶች ይቻላል።" else "Up to 10 personal cards per game."
        "max_total_cards" -> if (am) "በጠቅላላ እስከ 20 ካርዶች ይቻላል።" else "Up to 20 total cards in pot."
        "alarm_started" -> if (am) "ማንቂያ ተጀምሯል! ጨዋታው በ${alarmSecondsLeft} ሰከንድ ይጀምራል።"
        else "Alarm active! Game starts in ${alarmSecondsLeft}s."
        "playing" -> if (am) "ጨዋታው ጀምሯል! በየ 2 ሰከንዱ ቁጥር ይጠራል።"
        else "Game live! Numbers draw every 2 seconds."
        "drawn" -> if (am) "የተጠራ ቁጥር: ${called.lastOrNull() ?: ""}"
        else "Last called: ${called.lastOrNull() ?: ""}"
        "all_drawn" -> if (am) "ሁሉም 75 ቁጥሮች ተጠርተዋል።" else "All 75 numbers have been called."
        "no_winner_yet" -> if (am) "እስካሁን አሸናፊ ካርድ የለም።" else "No winning card yet."
        "winners_declared" -> if (am) "አሸናፊ ተረጋግጧል! 80% ለአሸናፊዎች ተከፋፍሏል። በ2 ደቂቃ ቀጣይ ካርድ ምርጫ ይጀምራል።"
        else "Winner declared! 80% split equally. Next card selection in ${selectionSecondsLeft}s."
        "new_cycle_selection" -> if (am) "አዲስ የካርድ ምርጫ ጊዜ (2 ደቂቃ)። ሶስት መስመር ወደ አንድ መስመር ተቀይሯል።"
        else "New 2-minute card selection window. Mode switched to One line if Three lines was active."
        "mode_changed" -> if (am) "የጨዋታ አይነት ተቀይሯል።" else "Game mode updated."
        else -> ""
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Pale),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (am) "BINGO ET • የልምምድ እና የነጥብ ጨዋታ"
                            else "BINGO ET • Practice & Points Mode",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Text(
                            if (am) "1 ካርድ = 10 ብር ነጥብ • የአሸናፊ ድርሻ 80% • ብዙ አሸናፊዎች እኩል ይካፈላሉ"
                            else "1 card = 10 Birr pts • Winner pool 80% • Multiple winners split equally",
                            color = Navy,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (if (am) "ጠቅላላ ድምር: " else "Total pot: ") +
                                "${totalPotBirr.toInt()} Birr  |  " +
                                (if (am) "የአሸናፊ 80%: " else "80% pool: ") +
                                "${(totalPotBirr * WINNER_SHARE_RATIO).toInt()} Birr",
                            fontWeight = FontWeight.Bold,
                            color = Blue
                        )
                        Text(
                            (if (am) "የተጠራቀመ ነጥብ: " else "Earned points: ") + points,
                            fontWeight = FontWeight.Bold,
                            color = Green
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        when (phase) {
                            GamePhase.CARD_SELECTION -> {
                                Text(
                                    if (am) "ደረጃ 1: ካርድ ይምረጡ (10 ሰከንድ ማንቂያ ለመጀመር ይጫኑ)"
                                    else "Step 1: Select cards & trigger 10s alarm countdown",
                                    fontWeight = FontWeight.Bold,
                                    color = Navy
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { startAlarmCountdown() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = entries.isNotEmpty()
                                ) {
                                    Text(
                                        if (am) "የ10 ሰከንድ ማንቂያ ጀምር"
                                        else "Start 10s Alarm & Game"
                                    )
                                }
                            }
                            GamePhase.ALARM_COUNTDOWN -> {
                                Text(
                                    if (am) "ማንቂያ እየጮኸ ነው: በ $alarmSecondsLeft ሰከንድ ይጀምራል!"
                                    else "ALARM! Game starts in ${alarmSecondsLeft}s",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFB45309)
                                )
                            }
                            GamePhase.PLAYING -> {
                                Text(
                                    if (am) "ጨዋታ በሂደት ላይ (በየ 2 ሰከንዱ ጥሪ)"
                                    else "Game in progress (2s call interval)",
                                    fontWeight = FontWeight.Bold,
                                    color = Green
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (called.size < 75) {
                                                val remaining = (1..75).filterNot { it in called }
                                                called = called + remaining.random()
                                                playCallTone()
                                                statusKey = "drawn"
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = called.size < 75
                                    ) {
                                        Text(if (am) "አሁን ጥራ" else "Call now")
                                    }
                                    OutlinedButton(
                                        onClick = { evaluateWinners() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (am) "BINGO አረጋግጥ" else "Claim Bingo")
                                    }
                                }
                            }
                            GamePhase.FINISHED -> {
                                val mins = selectionSecondsLeft / 60
                                val secs = selectionSecondsLeft % 60
                                Text(
                                    if (am) "ጨዋታው ተጠናቋል! ቀጣይ ካርድ ምርጫ በ ${mins}:${secs.toString().padStart(2, '0')}"
                                    else "Round finished! Next card selection in ${mins}:${secs.toString().padStart(2, '0')}",
                                    fontWeight = FontWeight.Bold,
                                    color = Navy
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (am) "እያንዳንዱ አሸናፊ ካርድ ድርሻ: ${String.format("%.2f", winnerPayoutEachBirr)} Birr"
                                    else "Payout per winning card: ${String.format("%.2f", winnerPayoutEachBirr)} Birr",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Green
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { prepareNextCardSelectionCycle() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (am) "አሁን ካርድ ምረጥ (ወደ አንድ መስመር)"
                                        else "Select cards now (switches to One line)"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    if (am) "የጨዋታ አይነት" else "Game mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Mode.entries.forEach { m ->
                    val modeEditable = phase == GamePhase.CARD_SELECTION
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = modeEditable) {
                                mode = m
                                statusKey = "mode_changed"
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == m,
                            enabled = modeEditable,
                            onClick = {
                                mode = m
                                statusKey = "mode_changed"
                            }
                        )
                        Text(
                            if (am) m.am else m.en,
                            color = if (modeEditable) Color.Unspecified else Color.Gray
                        )
                    }
                }
            }

            item {
                Text(statusText, color = Blue, fontWeight = FontWeight.SemiBold)
                if (called.isNotEmpty()) {
                    Text(
                        (if (am) "የመጨረሻ ቁጥር: " else "Last number: ") + called.last(),
                        fontSize = 25.sp,
                        color = Green,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (am) "የተጠሩ ቁጥሮች (${called.size}/75)" else "Called numbers (${called.size}/75)",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    called.sorted().joinToString("  ").ifBlank {
                        if (am) "ገና ቁጥር አልተጠራም" else "No numbers called yet"
                    }
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (am) "የተጫዋች ካርዶች (${playerCards.size})" else "Your cards (${playerCards.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { addPlayerCard() },
                        enabled = phase == GamePhase.CARD_SELECTION && playerCards.size < 10
                    ) {
                        Text(if (am) "+ ካርድ (10 ብር)" else "+ Card (10 Birr)")
                    }
                    OutlinedButton(
                        onClick = { addPracticeBotCard() },
                        enabled = phase == GamePhase.CARD_SELECTION && entries.size < 20
                    ) {
                        Text(if (am) "+ ተጫዋች" else "+ Opponent")
                    }
                }
            }

            itemsIndexed(entries, key = { _, entry -> entry.card.id }) { _, entry ->
                val card = entry.card
                val isWinnerCard = entry.card.id in winningCardIds
                BingoCardView(
                    entry = entry,
                    called = called,
                    isWinnerCard = isWinnerCard,
                    onToggle = { n ->
                        // Player can mark any cell manually or rely on called numbers;
                        // both manual marks and called numbers count toward Bingo.
                        entries = entries.map { item ->
                            if (item.card.id == card.id) {
                                val nextMarked = if (n in item.card.marked) {
                                    item.card.marked - n
                                } else {
                                    item.card.marked + n
                                }
                                item.copy(card = item.card.copy(marked = nextMarked))
                            } else {
                                item
                            }
                        }
                    },
                    am = am
                )
                if (isWinnerCard) {
                    Spacer(Modifier.height(6.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F6EC))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                if (am) "BINGO! ${entry.name} • ካርድ #${card.id} አሸንፏል! (${String.format("%.2f", winnerPayoutEachBirr)} Birr)"
                                else "BINGO! ${entry.name} • Card #${card.id} wins! (${String.format("%.2f", winnerPayoutEachBirr)} Birr)",
                                color = Green,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = Pale)) {
                    Text(
                        if (am) "ማሳሰቢያ: ይህ የልምምድ እና የነጥብ ሙከራ መተግበሪያ ነው። ብር/ነጥቦች የልምምድ መለኪያ ናቸው፤ እውነተኛ ክፍያ፣ ውርርድ ወይም የገንዘብ ሽልማት የለም።"
                        else "Note: Practice & points simulation only. Birr/points are simulated values with no real-money payments, wagers, or cash payouts.",
                        Modifier.padding(14.dp),
                        fontSize = 12.sp,
                        color = Navy
                    )
                }
            }
        }
    }
}

@Composable
private fun BingoCardView(
    entry: PlayerEntry,
    called: List<Int>,
    isWinnerCard: Boolean,
    onToggle: (Int) -> Unit,
    am: Boolean
) {
    val card = entry.card
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isWinnerCard) Modifier.border(2.dp, Green, RoundedCornerShape(16.dp))
                else Modifier
            )
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    (if (am) "ካርድ #" else "Card #") + card.id + " (${entry.name})",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    if (entry.isCurrentUser) {
                        if (am) "የእርስዎ" else "Player"
                    } else {
                        if (am) "ተጫዋች" else "Opponent"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = if (entry.isCurrentUser) Blue else Color.Gray,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("B", "I", "N", "G", "O").forEach { letter ->
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .background(Blue, RoundedCornerShape(6.dp))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(letter, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            for (r in 0 until 5) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 5) {
                        val n = card.value(r, c)
                        val free = r == 2 && c == 2
                        val isCalled = n in called
                        val isManual = n in card.marked
                        val isMarked = free || isCalled || isManual

                        val bgColor = when {
                            free || isCalled -> Color(0xFFCDEDD8)
                            isManual -> AmberBg
                            else -> Pale
                        }
                        val borderColor = when {
                            free || isCalled -> Green
                            isManual -> AmberBorder
                            else -> Color(0xFFD3E0EF)
                        }
                        val textColor = when {
                            free || isCalled -> Green
                            isManual -> Color(0xFFB45309)
                            else -> Navy
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .background(bgColor, RoundedCornerShape(7.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(7.dp))
                                .clickable(enabled = !free) { onToggle(n) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (free) "★" else n.toString(),
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 15.sp
                            )
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
    val values = (0 until 5).flatMap { row ->
        (0 until 5).map { col ->
            if (row == 2 && col == 2) 0 else columns[col][row]
        }
    }
    return BingoCard(id = id, values = values)
}

private fun hasWon(card: BingoCard, called: List<Int>, mode: Mode): Boolean {
    // Either manually marked OR called numbers count toward winning a line/star
    val active = called.toSet() + card.marked + 0
    val lineCount = (0 until 5).count { row ->
        (0 until 5).all { col -> (row == 2 && col == 2) || card.value(row, col) in active }
    } + (0 until 5).count { col ->
        (0 until 5).all { row -> (row == 2 && col == 2) || card.value(row, col) in active }
    } + (if ((0 until 5).all { i -> i == 2 || card.value(i, i) in active }) 1 else 0) +
        (if ((0 until 5).all { i -> i == 2 || card.value(i, 4 - i) in active }) 1 else 0)

    val stars = listOf(
        card.value(0, 0),
        card.value(0, 4),
        card.value(4, 0),
        card.value(4, 4)
    )
    return when (mode) {
        Mode.ONE -> lineCount >= 1
        Mode.THREE -> lineCount >= 3
        Mode.STAR -> stars.all { it in active }
    }
}
