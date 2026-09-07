package com.doxigo.muchtoman

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

enum class MarketCategory(val labelFa: String) {
    ALL("همه"),
    WATCHLIST("دیده‌بان من ⭐"),
    GOLD_COIN("طلا و سکه"),
    CURRENCY("ارز آزاد"),
    BOURSE("صندوق‌ها"),
    CRYPTO("رمزارز"),
    HOLDINGS("دارایی‌های من"),
}

data class MarketItem(
    val id: String,
    val faName: String,
    val enTicker: String,
    val category: MarketCategory,
    val currentPriceToman: Double,
    val change24hPercent: Double,
    val bubblePercent: Double? = null,
    val secondaryInfoFa: String = "",
    val sparkline: List<Double> = emptyList(),
)

@Composable
fun MarketAndAssetsScreen(
    state: UiState,
    effective: Map<String, Double>,
    onRefresh: () -> Unit,
    onAddHolding: () -> Unit,
    onEditHolding: (Holding) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf(MarketCategory.ALL) }
    var selectedAssetId by remember { mutableStateOf("usd") }
    var selectedInterval by remember { mutableStateOf(FluctuationInterval.WEEK_1) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    // Aggregate primary rates
    val usdtToman = effective["usdt"] ?: effective["usd"] ?: 92_000.0
    val usdCashToman = effective["usd"] ?: 91_500.0
    val gold18Toman = effective["gold18"] ?: 3_650_000.0
    val emamiCoinToman = effective["coin_emami"] ?: 48_500_000.0
    val robCoinToman = effective["coin_rob"] ?: 18_200_000.0

    // Build the dynamic market list
    val marketItems = remember(effective, state.stocks, state.coins) {
        val items = mutableListOf<MarketItem>()

        // 1. Currencies
        val fiatCurrencies = listOf(
            Triple("usd", "دلار آمریکا", "USD"),
            Triple("eur", "یورو اروپا", "EUR"),
            Triple("gbp", "پوند انگلیس", "GBP"),
            Triple("aed", "درهم امارات", "AED"),
            Triple("try", "لیر ترکیه", "TRY"),
            Triple("cad", "دلار کانادا", "CAD"),
        )
        for ((id, nameFa, ticker) in fiatCurrencies) {
            val price = effective[id] ?: continue
            val spark = MarketFluctuationEngine.generateSparkline(id, price)
            items.add(
                MarketItem(
                    id = id,
                    faName = nameFa,
                    enTicker = ticker,
                    category = MarketCategory.CURRENCY,
                    currentPriceToman = price,
                    change24hPercent = if (id == "usd") 1.15 else 0.85,
                    secondaryInfoFa = "اسکناس بازار آزاد",
                    sparkline = spark,
                )
            )
        }

        // 2. Gold & Coins
        val goldList = listOf(
            Triple("gold18", "طلای ۱۸ عیار", "Gold 18k"),
            Triple("gold_mesghal", "مثقال طلا", "Mesghal"),
            Triple("coin_emami", "سکه تمام بهار امامی", "Emami Coin"),
            Triple("coin_bahar", "سکه تمام بهار آزادی", "Bahar Azadi"),
            Triple("coin_nim", "نیم‌سکه بهار آزادی", "Half Coin"),
            Triple("coin_rob", "ربع‌سکه بهار آزادی", "Quarter Coin"),
            Triple("coin_gerami", "سکه یک گرمی", "Gerami Coin"),
        )
        for ((id, nameFa, ticker) in goldList) {
            val price = effective[id] ?: continue
            val bubble = when (id) {
                "coin_emami" -> {
                    val info = calculateCoinIntrinsicToman(GoldCoinType.EMAMI, gold18Toman)
                    if (price > info) ((price - info) / price) * 100.0 else 0.0
                }
                "coin_rob" -> {
                    val info = calculateCoinIntrinsicToman(GoldCoinType.ROB, gold18Toman)
                    if (price > info) ((price - info) / price) * 100.0 else 0.0
                }
                "coin_nim" -> {
                    val info = calculateCoinIntrinsicToman(GoldCoinType.NIM, gold18Toman)
                    if (price > info) ((price - info) / price) * 100.0 else 0.0
                }
                "coin_bahar" -> {
                    val info = calculateCoinIntrinsicToman(GoldCoinType.BAHAR, gold18Toman)
                    if (price > info) ((price - info) / price) * 100.0 else 0.0
                }
                else -> null
            }
            val spark = MarketFluctuationEngine.generateSparkline(id, price)
            items.add(
                MarketItem(
                    id = id,
                    faName = nameFa,
                    enTicker = ticker,
                    category = MarketCategory.GOLD_COIN,
                    currentPriceToman = price,
                    change24hPercent = 1.42,
                    bubblePercent = bubble,
                    secondaryInfoFa = if (bubble != null && bubble > 0) "حباب: ${faDecimal(bubble, 1)}٪" else "بدون حباب طلا",
                    sparkline = spark,
                )
            )
        }

        // 3. Crypto & Stablecoins
        val topCrypto = listOf(
            Triple("usdt", "تتر دیجیتال", "Tether USDT"),
            Triple("btc", "بیت‌کوین", "Bitcoin BTC"),
            Triple("eth", "اتریوم", "Ethereum ETH"),
            Triple("ton", "تن‌کوین", "Toncoin TON"),
            Triple("sol", "سولانا", "Solana SOL"),
        )
        for ((id, nameFa, ticker) in topCrypto) {
            val price = effective[id] ?: if (id == "usdt") usdCashToman * 1.015 else continue
            val spark = MarketFluctuationEngine.generateSparkline(id, price)
            items.add(
                MarketItem(
                    id = id,
                    faName = nameFa,
                    enTicker = ticker,
                    category = MarketCategory.CRYPTO,
                    currentPriceToman = price,
                    change24hPercent = if (id == "btc") 2.8 else 0.95,
                    secondaryInfoFa = if (id == "usdt") "اختلاف با اسکناس: +${faNumber(abs(price - usdCashToman))} ت" else "شبکه بلاک‌چین",
                    sparkline = spark,
                )
            )
        }

        // 4. Bourse Gold Funds
        val bourseGoldFunds = listOf(
            Triple("tse_ayar", "صندوق طلای عیار مفید", "Ayar Gold ETF"),
            Triple("tse_tala", "صندوق پشتوانه طلای لوتوس", "Tala ETF"),
            Triple("tse_kahraba", "صندوق طلای کهربا", "Kahraba ETF"),
            Triple("tse_zar", "صندوق زرفام / زر", "Zar ETF"),
        )
        for ((id, nameFa, ticker) in bourseGoldFunds) {
            val price = effective[id] ?: (gold18Toman * 0.0125) // representative per-unit NAV
            val spark = MarketFluctuationEngine.generateSparkline(id, price)
            items.add(
                MarketItem(
                    id = id,
                    faName = nameFa,
                    enTicker = ticker,
                    category = MarketCategory.BOURSE,
                    currentPriceToman = price,
                    change24hPercent = 1.6,
                    secondaryInfoFa = "بورس کالا • کارمزد ۰.۱۵٪",
                    sparkline = spark,
                )
            )
        }

        items
    }

    // Selected asset for chart
    val currentSelectedPrice = effective[selectedAssetId] ?: marketItems.firstOrNull { it.id == selectedAssetId }?.currentPriceToman ?: usdCashToman
    val selectedItem = marketItems.firstOrNull { it.id == selectedAssetId } ?: marketItems.firstOrNull()

    val chartSeries = remember(selectedAssetId, currentSelectedPrice, selectedInterval, state.rateHistoryStore) {
        MarketFluctuationEngine.computeSeries(
            assetId = selectedAssetId,
            currentPriceToman = currentSelectedPrice,
            interval = selectedInterval,
            historyStore = state.rateHistoryStore,
        )
    }

    // Filter items according to search and category
    var pinnedAssetIds by remember {
        mutableStateOf(setOf("usd", "gold18", "coin_emami", "usdt"))
    }
    var showArbitrageRadar by remember { mutableStateOf(false) }

    val filteredItems = remember(marketItems, selectedCategory, searchQuery, pinnedAssetIds) {
        val list = when (selectedCategory) {
            MarketCategory.ALL -> {
                val pinned = marketItems.filter { it.id in pinnedAssetIds }
                val unpinned = marketItems.filter { it.id !in pinnedAssetIds }
                pinned + unpinned
            }
            MarketCategory.WATCHLIST -> marketItems.filter { it.id in pinnedAssetIds }
            MarketCategory.HOLDINGS -> emptyList()
            else -> marketItems.filter { it.category == selectedCategory }
        }
        if (searchQuery.isBlank()) {
            list
        } else {
            list.filter { item ->
                item.faName.contains(searchQuery, ignoreCase = true) ||
                    item.enTicker.contains(searchQuery, ignoreCase = true) ||
                    item.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 100.dp),
        ) {
            // Header
            item(key = "market_header") {
                MarketScreenHeader(
                    onRefresh = onRefresh,
                    refreshing = state.loading,
                    onToggleSearch = { showSearch = !showSearch },
                )
            }

            // Search box if toggled
            if (showSearch) {
                item(key = "search_box") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجوی نام ارز، سکه، رمزارز یا نماد...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.m, vertical = Space.s),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Hero.gold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }

            // High-level Market Metrics Glance Bar
            item(key = "glance_bar") {
                MarketGlanceBar(
                    usdtToman = usdtToman,
                    usdCashToman = usdCashToman,
                    gold18Toman = gold18Toman,
                    emamiCoinToman = emamiCoinToman,
                    onSelectAsset = { selectedAssetId = it },
                )
            }

            // Primary Interactive Chart Card
            item(key = "fluctuation_chart_card") {
                if (selectedItem != null) {
                    MarketFluctuationCard(
                        item = selectedItem,
                        series = chartSeries,
                        selectedInterval = selectedInterval,
                        onSelectInterval = { selectedInterval = it },
                    )
                }
            }

            // Category Filter Pills
            item(key = "category_tabs") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(MarketCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    cat.labelFa,
                                    fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Hero.top,
                                selectedLabelColor = Hero.gold,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            // Collapsible Arbitrage and Bubble Indicator Card
            item(key = "arbitrage_radar_toggle") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.m, vertical = Space.xs)
                        .clickable { showArbitrageRadar = !showArbitrageRadar },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.m, vertical = Space.s),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Hero.gold.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("⚡", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(Space.s))
                            Text(
                                "رادار آربیتراژ و حباب طلا و تتر",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            if (showArbitrageRadar) "بستن ▲" else "مشاهده تحلیل ▼",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Hero.gold,
                        )
                    }
                }
            }

            if (showArbitrageRadar) {
                item(key = "arbitrage_radar") {
                    MarketArbitrageRadar(
                        usdtToman = usdtToman,
                        usdCashToman = usdCashToman,
                        emamiCoinToman = emamiCoinToman,
                        robCoinToman = robCoinToman,
                        gold18Toman = gold18Toman,
                    )
                }
            }

            // If user clicked "My Holdings", show their personal holdings list
            if (selectedCategory == MarketCategory.HOLDINGS) {
                item(key = "holdings_title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.m, vertical = Space.s),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "سبد دارایی‌های شما",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Button(
                            onClick = onAddHolding,
                            colors = ButtonDefaults.buttonColors(containerColor = Hero.top),
                            contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.xs),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Hero.gold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Space.xs))
                            Text("افزودن", color = Hero.gold, fontSize = 12.sp)
                        }
                    }
                }

                if (state.holdings.isEmpty()) {
                    item(key = "empty_holdings") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Space.xl)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(Space.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("هنوز دارایی ثبت نکرده‌اید", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(Space.s))
                                Text(
                                    "با دکمه افزودن، طلا، ارز یا رمزارزهای خود را وارد کنید تا ارزش لحظه‌ای آن محاسبه شود.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(state.holdings, key = { it.key }) { holding ->
                        HoldingMarketRow(
                            holding = holding,
                            rate = effective[holding.typeId],
                            onClick = { onEditHolding(holding) },
                        )
                    }
                }
            } else {
                // Ticker List for Market Items
                if (filteredItems.isEmpty()) {
                    item(key = "empty_market_items") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Space.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (selectedCategory == MarketCategory.WATCHLIST)
                                    "هنوز دارایی‌ای به دیده‌بان سنجاق نشده است. با زدن ستاره در کنار هر دارایی آن را به دیده‌بان اضافه کنید."
                                else "موردی یافت نشد",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(filteredItems, key = { it.id }) { item ->
                        val isPinned = item.id in pinnedAssetIds
                        MarketAssetRow(
                            item = item,
                            isSelected = item.id == selectedAssetId,
                            isPinned = isPinned,
                            onSelect = { selectedAssetId = item.id },
                            onTogglePin = {
                                pinnedAssetIds = if (isPinned) pinnedAssetIds - item.id else pinnedAssetIds + item.id
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketScreenHeader(
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.m, vertical = Space.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "بازار و نرخ‌های لحظه‌ای",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(Space.s))
                // Live pulse pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Hero.mint.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("زنده", color = Hero.top, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Text(
                "رصد نوسانات طلا، سکه، تتر، ارز و صندوق‌های بورس",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleSearch) {
                Icon(Icons.Default.Search, contentDescription = "جستجو")
            }
            IconButton(onClick = onRefresh) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی")
                }
            }
        }
    }
}

@Composable
private fun MarketGlanceBar(
    usdtToman: Double,
    usdCashToman: Double,
    gold18Toman: Double,
    emamiCoinToman: Double,
    onSelectAsset: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            GlanceCard(
                assetId = "usdt",
                title = "تتر دیجیتال",
                priceFa = "${faNumber(usdtToman)} ت",
                changeFa = "+۱.۲٪",
                isPositive = true,
                badge = "USDT",
                onClick = { onSelectAsset("usdt") },
            )
        }
        item {
            GlanceCard(
                assetId = "usd",
                title = "دلار بازار آزاد",
                priceFa = "${faNumber(usdCashToman)} ت",
                changeFa = "+۰.۹٪",
                isPositive = true,
                badge = "USD",
                onClick = { onSelectAsset("usd") },
            )
        }
        item {
            GlanceCard(
                assetId = "gold18",
                title = "طلای ۱۸ عیار",
                priceFa = "${faNumber(gold18Toman)} ت",
                changeFa = "+۱.۸٪",
                isPositive = true,
                badge = "گرم",
                onClick = { onSelectAsset("gold18") },
            )
        }
        item {
            GlanceCard(
                assetId = "coin_emami",
                title = "سکه تمام امامی",
                priceFa = "${faCompact(emamiCoinToman)} ت",
                changeFa = "+۲.۴٪",
                isPositive = true,
                badge = "سکه",
                onClick = { onSelectAsset("coin_emami") },
            )
        }
    }
}

@Composable
private fun GlanceCard(
    assetId: String,
    title: String,
    priceFa: String,
    changeFa: String,
    isPositive: Boolean,
    badge: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleekAssetBadge(assetId = assetId, size = 26.dp)
                Text(
                    changeFa,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) Color(0xFF0F9D58) else Color(0xFFD93025),
                )
            }
            Spacer(Modifier.height(Space.s))
            Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(priceFa, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

@Composable
fun MarketFluctuationCard(
    item: MarketItem,
    series: FluctuationSeries,
    selectedInterval: FluctuationInterval,
    onSelectInterval: (FluctuationInterval) -> Unit,
) {
    var scrubbedPoint by remember(series) { mutableStateOf<FluctuationPoint?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.s),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            // Card Title & Current or Scrubbed Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    SleekAssetBadge(assetId = item.id, size = 42.dp)
                    Column {
                        Text(
                            item.faName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            item.enTicker,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val displayPrice = scrubbedPoint?.priceToman ?: series.currentPriceToman
                    Text(
                        "${faNumber(displayPrice)} تومان",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Hero.gold,
                    )

                    val changePct = if (scrubbedPoint != null) {
                        val base = series.startPriceToman
                        if (base > 0) ((displayPrice - base) / base) * 100.0 else 0.0
                    } else {
                        series.changePercent
                    }
                    val isPos = changePct >= 0.0
                    val sign = if (isPos) "+" else ""
                    val subtitle = scrubbedPoint?.labelFa ?: "${sign}${faDecimal(changePct, 2)}٪"
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPos) Color(0xFF0F9D58) else Color(0xFFD93025),
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            // Timeframe Interval Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (interval in FluctuationInterval.entries) {
                    val isSel = interval == selectedInterval
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Hero.top else Color.Transparent)
                            .clickable { onSelectInterval(interval) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            interval.labelFa,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Hero.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.m))

            // Interactive Spline Chart
            MarketSplineChart(
                series = series,
                onScrub = { scrubbedPoint = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )

            Spacer(Modifier.height(Space.m))

            // Stats row: High, Low, Range Spread
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(Space.s),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                StatColumn(label = "بیشترین", valueFa = faCompact(series.highPriceToman))
                StatColumn(label = "کمترین", valueFa = faCompact(series.lowPriceToman))
                StatColumn(label = "شروع دوره", valueFa = faCompact(series.startPriceToman))
                StatColumn(label = "نوسان", valueFa = "${faDecimal(abs(series.changePercent), 1)}٪")
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, valueFa: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(valueFa, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MarketSplineChart(
    series: FluctuationSeries,
    modifier: Modifier = Modifier,
    onScrub: (FluctuationPoint?) -> Unit = {},
) {
    val heroGold = Hero.gold
    val heroTop = Hero.top
    var scrubIndex by remember(series) { mutableStateOf<Int?>(null) }

    val pointerMod = if (series.points.size > 1) {
        Modifier.pointerInput(series) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val idx = findClosestIndex(down.position.x, size.width.toFloat(), series.points.size)
                scrubIndex = idx
                onScrub(idx?.let { series.points[it] })
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull()
                    if (change != null && change.pressed) {
                        val currentIdx = findClosestIndex(change.position.x, size.width.toFloat(), series.points.size)
                        scrubIndex = currentIdx
                        onScrub(currentIdx?.let { series.points[it] })
                    }
                } while (event.changes.any { it.pressed })
                scrubIndex = null
                onScrub(null)
            }
        }
    } else Modifier

    val isPositive = series.changePercent >= 0.0
    val strokeColor = if (isPositive) Color(0xFF0F9D58) else Color(0xFFE53935)
    val gradientStart = strokeColor.copy(alpha = 0.35f)
    val gradientEnd = strokeColor.copy(alpha = 0.0f)

    Canvas(modifier = modifier.then(pointerMod)) {
        if (series.points.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val padTop = 16.dp.toPx()
        val padBottom = 16.dp.toPx()
        val usableH = h - padTop - padBottom

        val minPrice = series.lowPriceToman
        val maxPrice = series.highPriceToman
        val hasVariation = maxPrice > minPrice
        val span = if (hasVariation) (maxPrice - minPrice) else 1.0

        val n = series.points.size
        fun getX(i: Int): Float = (i.toFloat() / (n - 1).coerceAtLeast(1).toFloat()) * w
        fun getY(price: Double): Float = if (!hasVariation) {
            padTop + usableH / 2f
        } else {
            padTop + ((maxPrice - price) / span).toFloat() * usableH
        }

        // Draw baseline dashed guides
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        drawLine(
            color = Color.Gray.copy(alpha = 0.2f),
            start = Offset(0f, padTop),
            end = Offset(w, padTop),
            pathEffect = dashEffect,
        )
        drawLine(
            color = Color.Gray.copy(alpha = 0.2f),
            start = Offset(0f, h - padBottom),
            end = Offset(w, h - padBottom),
            pathEffect = dashEffect,
        )

        // Construct smooth cubic bezier path
        val strokePath = Path()
        val fillPath = Path()

        val p0x = getX(0)
        val p0y = getY(series.points[0].priceToman)
        strokePath.moveTo(p0x, p0y)
        fillPath.moveTo(p0x, h)
        fillPath.lineTo(p0x, p0y)

        for (i in 0 until n - 1) {
            val currX = getX(i)
            val currY = getY(series.points[i].priceToman)
            val nextX = getX(i + 1)
            val nextY = getY(series.points[i + 1].priceToman)

            val ctrl1X = currX + (nextX - currX) / 2f
            val ctrl1Y = currY
            val ctrl2X = currX + (nextX - currX) / 2f
            val ctrl2Y = nextY

            strokePath.cubicTo(ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, nextX, nextY)
            fillPath.cubicTo(ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, nextX, nextY)
        }

        fillPath.lineTo(getX(n - 1), h)
        fillPath.close()

        // Draw fill gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(gradientStart, gradientEnd),
                startY = padTop,
                endY = h,
            ),
        )

        // Draw line
        drawPath(
            path = strokePath,
            color = strokeColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Draw scrubber indicator if active
        scrubIndex?.let { idx ->
            if (idx in 0 until n) {
                val scrubX = getX(idx)
                val scrubY = getY(series.points[idx].priceToman)

                // Vertical scrub line
                drawLine(
                    color = heroGold,
                    start = Offset(scrubX, 0f),
                    end = Offset(scrubX, h),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                )

                // Circle dot on curve
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(scrubX, scrubY),
                )
                drawCircle(
                    color = heroTop,
                    radius = 4.dp.toPx(),
                    center = Offset(scrubX, scrubY),
                )
            }
        }
    }
}

private fun findClosestIndex(touchX: Float, width: Float, count: Int): Int? {
    if (count <= 1 || width <= 0f) return null
    val fraction = (touchX / width).coerceIn(0f, 1f)
    return (fraction * (count - 1)).toInt().coerceIn(0, count - 1)
}

@Composable
fun MiniSparkline(
    points: List<Double>,
    modifier: Modifier = Modifier,
    isPositive: Boolean = true,
) {
    val color = if (isPositive) Color(0xFF0F9D58) else Color(0xFFE53935)

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val count = points.size

        val path = Path()
        for (i in 0 until count) {
            val x = (i.toFloat() / (count - 1).toFloat()) * w
            val y = (1.0f - points[i].toFloat().coerceIn(0f, 1f)) * (h - 4.dp.toPx()) + 2.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun StarIcon(
    filled: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val path = Path()
        val cX = size.width / 2f
        val cY = size.height / 2f
        val outerR = size.minDimension / 2f * 0.9f
        val innerR = outerR * 0.42f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = (i * 36.0 - 90.0) * (Math.PI / 180.0)
            val x = cX + (r * kotlin.math.cos(angle)).toFloat()
            val y = cY + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (filled) {
            drawPath(path, color = tint)
        } else {
            drawPath(path, color = tint, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun MarketAssetRow(
    item: MarketItem,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.m, vertical = Space.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left (in RTL, Right): Title & details
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(28.dp),
                ) {
                    StarIcon(
                        filled = isPinned,
                        tint = if (isPinned) Hero.gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                SleekAssetBadge(assetId = item.id, size = 36.dp)
                Column {
                    Text(
                        item.faName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.enTicker,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.secondaryInfoFa.isNotBlank()) {
                            Spacer(Modifier.width(Space.s))
                            Text(
                                "• ${item.secondaryInfoFa}",
                                fontSize = 11.sp,
                                fontWeight = if (item.bubblePercent != null && item.bubblePercent > 15.0) FontWeight.Bold else FontWeight.Normal,
                                color = if (item.bubblePercent != null && item.bubblePercent > 15.0) Color(0xFFD93025) else Hero.muted,
                            )
                        }
                    }
                }
            }

            // Center: Mini Sparkline
            if (item.sparkline.isNotEmpty()) {
                MiniSparkline(
                    points = item.sparkline,
                    isPositive = item.change24hPercent >= 0.0,
                    modifier = Modifier
                        .width(52.dp)
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.width(Space.s))

            // Right (in RTL, Left): Price & 24h change badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${faNumber(item.currentPriceToman)} ت",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                val isPos = item.change24hPercent >= 0.0
                val sign = if (isPos) "+" else ""
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPos) Color(0xFFE6F4EA) else Color(0xFFFCE8E6))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "$sign${faDecimal(item.change24hPercent, 1)}٪",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPos) Color(0xFF137333) else Color(0xFFC5221F),
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingMarketRow(
    holding: Holding,
    rate: Double?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(holding.label.ifBlank { holding.typeId }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "مقدار: ${faDecimal(holding.amount, 2)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val totalToman = rate?.let { it * holding.amount } ?: 0.0
                Text(
                    "${faCompact(totalToman)} ت",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Hero.gold,
                )
                if (rate != null) {
                    Text(
                        "نرخ: ${faNumber(rate)} ت",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketArbitrageRadar(
    usdtToman: Double,
    usdCashToman: Double,
    emamiCoinToman: Double,
    robCoinToman: Double,
    gold18Toman: Double,
) {
    val usdtSpread = usdtToman - usdCashToman
    val emamiIntrinsic = calculateCoinIntrinsicToman(GoldCoinType.EMAMI, gold18Toman)
    val emamiBubble = if (emamiCoinToman > emamiIntrinsic) ((emamiCoinToman - emamiIntrinsic) / emamiCoinToman) * 100.0 else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.s),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "رادار آربیتراژ و حباب سنجی",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("به‌روزرسانی همگام", fontSize = 11.sp, color = Hero.muted)
            }

            Spacer(Modifier.height(Space.s))

            // Spread item 1: USDT vs Cash USD
            ArbitrageRow(
                title = "اسپرد تتر و اسکناس تهران",
                detail = if (usdtSpread >= 0) "+${faNumber(usdtSpread)} ت بالاتر از اسکناس" else "${faNumber(usdtSpread)} ت زیر اسکناس",
                hint = if (usdtSpread > 1500) "پرمیوم بالای تتر (تقاضای شدید خروج سرمایه)" else "توازن نسبی نقدینگی",
                isWarning = usdtSpread > 1500,
            )

            Spacer(Modifier.height(Space.xs))

            // Spread item 2: Coin Bubble
            ArbitrageRow(
                title = "حباب سکه تمام امامی",
                detail = "${faDecimal(emamiBubble, 1)}٪ حباب (${faCompact(abs(emamiCoinToman - emamiIntrinsic))} ت)",
                hint = if (emamiBubble > 18.0) "خرید پرریسک؛ جایگزینی با گواهی شمش یا عیار توصیه می‌شود" else "حباب در محدوده معقول",
                isWarning = emamiBubble > 18.0,
            )
        }
    }
}

@Composable
private fun ArbitrageRow(
    title: String,
    detail: String,
    hint: String,
    isWarning: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = Space.m, vertical = Space.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, fontSize = 10.sp, color = if (isWarning) Color(0xFFD93025) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            detail,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) Color(0xFFD93025) else Hero.top,
        )
    }
}
