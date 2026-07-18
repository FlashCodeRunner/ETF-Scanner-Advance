package com.swapnil.etfscanner

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ---- Palette -------------------------------------------------------------
private val BgTop = Color(0xFF0A0E1A)
private val BgMid = Color(0xFF121A30)
private val TextHi = Color(0xFFF1F5F9)
private val TextLo = Color(0xFF94A3B8)
private val Pos = Color(0xFF34D399)
private val Neg = Color(0xFFFB7185)
private val GlassFill = Color(0x0FFFFFFF)   // ~6% white
private val GlassBorder = Color(0x1FFFFFFF)  // ~12% white
private val Accent = Brush.horizontalGradient(listOf(Color(0xFF22D3EE), Color(0xFF6366F1)))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ScannerScreen()
            }
        }
    }
}

@Composable
fun ScannerScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<ScanResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var thresholds by remember { mutableStateOf(SettingsStore.load(ctx)) }
    var selectedTab by remember { mutableStateOf(0) }
    var filtersOpen by remember { mutableStateOf(false) }
    var updatedAt by remember { mutableStateOf<String?>(null) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun runScan() {
        loading = true
        scope.launch {
            val r = MarketRepository.fetchAndScan()
            state = r
            if (r is ScanResult.Success)
                updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runScan()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgMid, BgTop)))
    ) {
        val success = state as? ScanResult.Success
        val stable = success?.stable(thresholds).orEmpty()
        val falling = success?.falling(thresholds).orEmpty()
        val blocked = success?.all
            ?.filter { !it.qualifies(thresholds) && it.oneDayChange <= thresholds.maxOneDay }
            ?.sortedBy { it.oneDayChange }.orEmpty()
        val current = when (selectedTab) { 0 -> stable; 1 -> falling; else -> blocked }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp)
        ) {
            item { Header(updatedAt) }
            item { Spacer(Modifier.height(14.dp)) }
            item {
                FiltersCard(filtersOpen, thresholds,
                    onToggle = { filtersOpen = !filtersOpen },
                    onApply = { thresholds = it; SettingsStore.save(ctx, it) })
            }
            item { Spacer(Modifier.height(14.dp)) }
            item {
                Segmented(
                    selectedTab,
                    listOf("Stable  ${stable.size}", "Falling  ${falling.size}", "Blocked  ${blocked.size}")
                ) { selectedTab = it }
            }
            item { Spacer(Modifier.height(14.dp)) }

            when {
                state is ScanResult.Error ->
                    item { InfoCard("Couldn't load", (state as ScanResult.Error).message) }
                success == null -> {} // overlay handles first load
                current.isEmpty() ->
                    item { InfoCard("Nothing here", "No ETF in this list at the current filters.") }
                else -> items(current) { EtfCard(it, blockedView = selectedTab == 2, t = thresholds) }
            }
        }

        // Floating refresh button
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(60.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(Accent)
                .clickable(enabled = !loading) { runScan() },
            contentAlignment = Alignment.Center
        ) {
            if (loading) CircularProgressIndicator(
                Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp
            ) else Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White)
        }

        // First-load overlay
        if (loading && success == null && state !is ScanResult.Error) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassBox {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF22D3EE), strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("Fetching live quotes…", color = TextHi, fontSize = 14.sp)
                        Text("pulling ${EtfUniverse.list.size} ETFs from Yahoo", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(updatedAt: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("ETF Tracker Advance", color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                if (updatedAt != null) "Updated $updatedAt · Yahoo (≈15 min delay)"
                else "Live from Yahoo Finance",
                color = TextLo, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GlassBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .shadow(10.dp, RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
    ) { content() }
}

@Composable
private fun Segmented(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (sel) Modifier.background(Accent) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (sel) Color.White else TextLo,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FiltersCard(
    open: Boolean,
    current: Thresholds,
    onToggle: () -> Unit,
    onApply: (Thresholds) -> Unit
) {
    GlassBox {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Tune, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Filters", color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "1d≤${fmtPct(current.maxOneDay)} · split ${fmtPct(current.thirtyDaySplit)}",
                    color = TextLo, fontSize = 11.sp
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null, tint = TextLo
                )
            }

            if (open) {
                var oneDay by remember { mutableStateOf(current.maxOneDay.toString()) }
                var split by remember { mutableStateOf(current.thirtyDaySplit.toString()) }
                var vol by remember { mutableStateOf(current.minVolume.toLong().toString()) }
                var value by remember { mutableStateOf(current.minValue.toLong().toString()) }
                var error by remember { mutableStateOf<String?>(null) }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField("1-day % ≤", oneDay, Modifier.weight(1f)) { oneDay = it }
                    GlassField("30-day split", split, Modifier.weight(1f)) { split = it }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField("Volume >", vol, Modifier.weight(1f)) { vol = it }
                    GlassField("Value ₹ >", value, Modifier.weight(1f)) { value = it }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp)); Text(it, color = Neg, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent)
                            .clickable {
                                val t = parseThresholds(oneDay, split, vol, value)
                                if (t == null) error = "Enter valid numbers"
                                else { error = null; onApply(t); onToggle() }
                            }
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                    ) { Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }

                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Reset",
                        color = TextLo, fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val d = Thresholds.DEFAULT
                                oneDay = d.maxOneDay.toString(); split = d.thirtyDaySplit.toString()
                                vol = d.minVolume.toLong().toString(); value = d.minValue.toLong().toString()
                                error = null; onApply(d)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi,
            focusedBorderColor = Color(0xFF22D3EE),
            unfocusedBorderColor = GlassBorder,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedLabelColor = Color(0xFF22D3EE),
            unfocusedLabelColor = TextLo,
            cursorColor = Color(0xFF22D3EE)
        ),
        modifier = modifier
    )
}

private fun parseThresholds(a: String, b: String, c: String, d: String): Thresholds? {
    val ma = a.trim().toDoubleOrNull() ?: return null
    val sp = b.trim().toDoubleOrNull() ?: return null
    val vo = c.trim().replace(",", "").toDoubleOrNull() ?: return null
    val va = d.trim().replace(",", "").toDoubleOrNull() ?: return null
    return Thresholds(ma, sp, vo, va)
}

@Composable
private fun EtfCard(etf: Etf, blockedView: Boolean, t: Thresholds) {
    Box(Modifier.padding(vertical = 5.dp)) {
        GlassBox {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(etf.name.removePrefix("NSE:"), color = TextHi,
                            fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(2.dp))
                        CategoryChip(etf.category)
                    }
                    PctPill(etf.oneDayChange)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "₹${etf.price}    30d ${fmtPct(etf.thirtyDayChange)}",
                    color = TextHi, fontSize = 13.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "vol ${etf.volume.toLong()}    val ${crore(etf.tradedValue)}",
                    color = TextLo, fontSize = 12.sp
                )
                if (blockedView) {
                    val reasons = etf.failReasons(t)
                    if (reasons.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("✗ " + reasons.joinToString("; "), color = Neg, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PctPill(v: Double) {
    val c = if (v < 0) Neg else Pos
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(fmtPct(v), color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun CategoryChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) { Text(text, color = TextLo, fontSize = 11.sp) }
}

@Composable
private fun InfoCard(title: String, msg: String) {
    GlassBox {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(msg, color = TextLo, fontSize = 13.sp)
        }
    }
}
