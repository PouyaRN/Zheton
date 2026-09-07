package com.doxigo.muchtoman

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Theme palette for styling an Iranian bank card with physical metallic sheen. */
data class BankCardPalette(
    val backgroundGradient: List<Color>,
    val accentColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val chipGoldPrimary: Color = Color(0xFFFFD54F),
    val chipGoldSecondary: Color = Color(0xFFFFA000),
)

object BankCardThemes {
    val MELLI = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
        accentColor = Color(0xFF4FC3F7),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFFB0BEC5),
    )

    val MELLAT = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF2B0B0E), Color(0xFF5E131C), Color(0xFF8B1E29)),
        accentColor = Color(0xFFFF6B6B),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFFFFCDD2),
    )

    val SAMAN = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF0D2538), Color(0xFF134E5E), Color(0xFF237688)),
        accentColor = Color(0xFF4DD0E1),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFFB2EBF2),
    )

    val PASARGAD = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF1A1A1A), Color(0xFF2D2A24), Color(0xFF423B2D)),
        accentColor = Color(0xFFFFD54F),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFFFFECB3),
    )

    val BLU = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF0A192F), Color(0xFF0F3460), Color(0xFF16213E)),
        accentColor = Color(0xFF00E5FF),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFF80DEEA),
    )

    val TEJARAT = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF0B192C), Color(0xFF1E3E62), Color(0xFF000000)),
        accentColor = Color(0xFF64B5F6),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFFBBDEFB),
    )

    val RESALAT = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF062828), Color(0xFF0E4D4D), Color(0xFF136868)),
        accentColor = Color(0xFF26A69A),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFFB2DFDB),
    )

    val DEFAULT = BankCardPalette(
        backgroundGradient = listOf(Color(0xFF1A1C20), Color(0xFF24272C), Color(0xFF121417)),
        accentColor = Color(0xFF81D4FA),
        textColor = Color(0xFFFFFFFF),
        secondaryTextColor = Color(0xFF90A4AE),
    )

    fun resolve(bankName: String): BankCardPalette = when {
        bankName.contains("ملی") || bankName.equals("MELLI", ignoreCase = true) -> MELLI
        bankName.contains("ملت") || bankName.equals("MELLAT", ignoreCase = true) -> MELLAT
        bankName.contains("سامان") || bankName.equals("SAMAN", ignoreCase = true) -> SAMAN
        bankName.contains("پاسارگاد") || bankName.equals("PASARGAD", ignoreCase = true) -> PASARGAD
        bankName.contains("بلو") || bankName.equals("BLU", ignoreCase = true) -> BLU
        bankName.contains("تجارت") || bankName.equals("TEJARAT", ignoreCase = true) -> TEJARAT
        bankName.contains("رسالت") || bankName.equals("RESALAT", ignoreCase = true) -> RESALAT
        else -> DEFAULT
    }
}

/**
 * An interactive 3D physical Iranian bank card with real-time perspective tilt physics,
 * metallic holographic sheen, EMV smart chip, Shetab network insignia, and masked PAN.
 */
@Composable
fun BankCard3D(
    bankAccount: BankAccount?,
    modifier: Modifier = Modifier,
    cardHolderName: String = "کارت اصلی شتاب",
    onClick: () -> Unit = {},
    enable3DTiltDrag: Boolean = true,
) {
    val bankName = bankAccount?.bankFa ?: "کارت شتاب"
    val palette = remember(bankName) { BankCardThemes.resolve(bankName) }
    val balanceToman = bankAccount?.balance ?: 0.0

    // Raw drag offsets in pixels
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    // Smooth spring physics for rotation angles
    val targetRotY = (dragX / 25f).coerceIn(-18f, 18f)
    val targetRotX = (-dragY / 25f).coerceIn(-14f, 14f)

    val animatedRotX by animateFloatAsState(
        targetValue = targetRotX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "cardTiltX",
    )
    val animatedRotY by animateFloatAsState(
        targetValue = targetRotY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "cardTiltY",
    )

    // Dynamic specular sheen highlight offset
    val sheenProgress = ((animatedRotY / 18f) + 1f) / 2f

    val density = LocalDensity.current

    val dragModifier = if (enable3DTiltDrag) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {},
                onDragEnd = {
                    dragX = 0f
                    dragY = 0f
                },
                onDragCancel = {
                    dragX = 0f
                    dragY = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount.x
                    dragY += dragAmount.y
                },
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(dragModifier),
    ) {
        // Shadow layer giving physical elevation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .graphicsLayer {
                    rotationX = animatedRotX * 0.5f
                    rotationY = animatedRotY * 0.5f
                    translationY = 16.dp.toPx()
                    alpha = 0.35f
                }
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = palette.accentColor,
                    ambientColor = Color.Black,
                ),
        )

        // The 3D Card body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .graphicsLayer {
                    this.cameraDistance = 14f * density.density
                    this.rotationX = animatedRotX
                    this.rotationY = animatedRotY
                    this.shape = RoundedCornerShape(20.dp)
                    this.clip = true
                }
                .background(Brush.linearGradient(palette.backgroundGradient))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            palette.accentColor.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                    ),
                    shape = RoundedCornerShape(20.dp),
                ),
        ) {
            // Background geometric pattern
            CardGeometricPattern(accentColor = palette.accentColor)

            // Dynamic Holographic / Specular Sheen overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sheenX = size.width * sheenProgress
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f + abs(animatedRotX + animatedRotY) / 300f),
                            palette.accentColor.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.18f),
                            palette.accentColor.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        start = Offset(sheenX - 160f, 0f),
                        end = Offset(sheenX + 240f, size.height),
                    ),
                    size = size,
                )
            }

            // Card Content layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Row: Bank branding & Shetab emblem
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(palette.accentColor.copy(alpha = 0.2f))
                                .border(1.dp, palette.accentColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = bankName.take(1),
                                color = palette.accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Text(
                            text = bankName,
                            color = palette.textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    // Shetab Badge
                    ShetabInsignia(accentColor = palette.accentColor)
                }

                // Middle: EMV Smart Chip and Contactless glyphs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EmvSmartChip(
                        goldPrimary = palette.chipGoldPrimary,
                        goldSecondary = palette.chipGoldSecondary,
                    )
                    ContactlessWaves(color = palette.secondaryTextColor.copy(alpha = 0.7f))
                }

                // Card Number PAN & Copy Action
                val displayPan = formatCardPan(bankAccount?.mask ?: "۶۰۳۷ •••• •••• ۱۲۳۴")
                val cardContext = LocalContext.current
                val cardClipboard = LocalClipboardManager.current
                val cardDigits = bankAccount?.mask?.filter { it.isDigit() }?.ifBlank { bankAccount.mask } ?: ""

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (cardDigits.isNotBlank()) {
                                cardClipboard.setText(AnnotatedString(cardDigits))
                                android.widget.Toast.makeText(cardContext, "شماره کپی شد: $cardDigits", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = displayPan,
                        color = palette.textColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        letterSpacing = 2.sp,
                    )
                    if (cardDigits.isNotBlank()) {
                        CardCopyIcon(
                            tint = palette.accentColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // Bottom Row: Cardholder / Title & Live Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            text = "صاحب کارت",
                            color = palette.secondaryTextColor.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = cardHolderName,
                            color = palette.textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "موجودی حساب",
                            color = palette.secondaryTextColor.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = faNumber(balanceToman),
                                color = palette.accentColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = ModamFigures,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "تومان",
                                color = palette.secondaryTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Draws the metallic EMV chip with realistic contact etching lines. */
@Composable
private fun EmvSmartChip(
    goldPrimary: Color,
    goldSecondary: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(width = 44.dp, height = 34.dp)
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp)),
    ) {
        // Base gold brushed surface
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(goldPrimary, goldSecondary, goldPrimary),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )

        // Micro-etched contact circuit lines
        val strokeWidth = 1.dp.toPx()
        val linePaint = Color(0xFF5D4037).copy(alpha = 0.7f)

        // Horizontal contact partition lines
        drawLine(
            color = linePaint,
            start = Offset(0f, size.height * 0.33f),
            end = Offset(size.width, size.height * 0.33f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = linePaint,
            start = Offset(0f, size.height * 0.66f),
            end = Offset(size.width, size.height * 0.66f),
            strokeWidth = strokeWidth,
        )

        // Center contact square
        drawRoundRect(
            color = linePaint,
            topLeft = Offset(size.width * 0.32f, size.height * 0.22f),
            size = Size(size.width * 0.36f, size.height * 0.56f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )

        // Diagonal branch lines
        drawLine(
            color = linePaint,
            start = Offset(size.width * 0.32f, size.height * 0.22f),
            end = Offset(size.width * 0.15f, 0f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = linePaint,
            start = Offset(size.width * 0.68f, size.height * 0.22f),
            end = Offset(size.width * 0.85f, 0f),
            strokeWidth = strokeWidth,
        )
    }
}

/** Draws contactless radio waves glyphs. */
@Composable
private fun ContactlessWaves(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width * 0.15f, size.height * 0.5f)
        val stroke = 1.5.dp.toPx()

        for (i in 1..3) {
            val radius = i * 6.5.dp.toPx()
            drawArc(
                color = color,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** Subtle background guilloche / geometric curve watermark. */
@Composable
private fun CardGeometricPattern(accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.4f)
            cubicTo(
                size.width * 0.35f,
                size.height * 0.1f,
                size.width * 0.65f,
                size.height * 0.9f,
                size.width,
                size.height * 0.5f,
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.05f),
                    accentColor.copy(alpha = 0.01f),
                ),
            ),
        )
    }
}

/** Iranian Shetab (شبکه شتاب) insignia badge. */
@Composable
private fun ShetabInsignia(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Text(
                text = "شتاب",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * Sanitizes any Persian or Arabic numerals to standard ASCII digits 0-9
 * for seamless compatibility when pasting into banking apps or web portals.
 */
fun toAsciiDigits(str: String): String = str.map { char ->
    when (char) {
        '۰', '٠' -> '0'
        '۱', '١' -> '1'
        '۲', '٢' -> '2'
        '۳', '٣' -> '3'
        '۴', '٤' -> '4'
        '۵', '٥' -> '5'
        '۶', '٦' -> '6'
        '۷', '٧' -> '7'
        '۸', '٨' -> '8'
        '۹', '٩' -> '9'
        else -> char
    }
}.joinToString("")

/** Formats a PAN or mask into 4 digit blocks with Persian numerals. */
fun formatCardPan(raw: String): String {
    val clean = raw.trim()
    fun convertPersian(str: String): String = str.map { char ->
        when (char) {
            '0' -> '۰'
            '1' -> '۱'
            '2' -> '۲'
            '3' -> '۳'
            '4' -> '۴'
            '5' -> '۵'
            '6' -> '۶'
            '7' -> '۷'
            '8' -> '۸'
            '9' -> '۹'
            else -> char
        }
    }.joinToString("")

    if (clean.contains("•") || clean.contains("*")) {
        return convertPersian(clean)
    }

    val digits = clean.filter { it.isDigit() }
    if (digits.length >= 16) {
        val p1 = digits.substring(0, 4)
        val p2 = digits.substring(4, 8)
        val p3 = digits.substring(8, 12)
        val p4 = digits.substring(12, 16)
        return convertPersian("$p1  $p2  $p3  $p4")
    }

    return convertPersian(clean)
}

/**
 * Minimalist crisp dual-page copy icon.
 */
@Composable
private fun CardCopyIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.3.dp.toPx()
        val corner = 2.dp.toPx()
        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(size.width * 0.28f, 0f),
            size = Size(size.width * 0.72f, size.height * 0.72f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, size.height * 0.28f),
            size = Size(size.width * 0.72f, size.height * 0.72f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * Interactive fluid carousel/deck of 3D Iranian bank cards with native snapping,
 * 3D perspective scroll tilt, and dynamic bank-accented indicator pills.
 */
@Composable
fun BankCardDeck(
    accounts: List<BankAccount>,
    modifier: Modifier = Modifier,
    cardHolderName: String = "کاربر مهربان",
    onCardClick: (BankAccount) -> Unit = {},
) {
    if (accounts.isEmpty()) return

    if (accounts.size == 1) {
        val singleAccount = accounts.first()
        val context = LocalContext.current
        val clipboardManager = LocalClipboardManager.current
        val rawDigits = toAsciiDigits(singleAccount.mask).filter { it.isDigit() }
        val copyText = if (rawDigits.length >= 16) rawDigits.take(16) else rawDigits.ifBlank { singleAccount.mask }

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BankCard3D(
                bankAccount = singleAccount,
                cardHolderName = cardHolderName,
                modifier = Modifier.padding(horizontal = 20.dp),
                onClick = { onCardClick(singleAccount) },
                enable3DTiltDrag = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = Space.m, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (copyText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(copyText))
                                android.widget.Toast.makeText(context, "شماره کپی شد: $copyText", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = Space.s, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardCopyIcon(
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "کپی شماره",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Box(
                        Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable { onCardClick(singleAccount) }
                        .padding(horizontal = Space.s, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "گردش حساب ${singleAccount.bankFa}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { accounts.size })
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val account = accounts[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val clampedOffset = pageOffset.coerceIn(-1f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Dynamic 3D depth and tilt during page transition
                        cameraDistance = 14f * density.density
                        rotationY = clampedOffset * -16f
                        scaleX = 1f - (abs(clampedOffset) * 0.08f)
                        scaleY = 1f - (abs(clampedOffset) * 0.08f)
                        alpha = 1f - (abs(clampedOffset) * 0.3f)
                    },
            ) {
                BankCard3D(
                    bankAccount = account,
                    cardHolderName = cardHolderName,
                    onClick = { onCardClick(account) },
                    enable3DTiltDrag = false, // HorizontalPager handles swipe gesture cleanly
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Dynamic bank indicator pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            accounts.forEachIndexed { index, acc ->
                val isSelected = index == pagerState.currentPage
                val palette = remember(acc.bankFa) { BankCardThemes.resolve(acc.bankFa) }
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (isSelected) 24.dp else 8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isSelected) palette.accentColor
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                )
            }
        }

        // Active card quick copy and details bar
        val activeAccount = accounts.getOrNull(pagerState.currentPage) ?: accounts.firstOrNull()
        if (activeAccount != null) {
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current
            val rawDigits = toAsciiDigits(activeAccount.mask).filter { it.isDigit() }
            val copyText = if (rawDigits.length >= 16) rawDigits.take(16) else rawDigits.ifBlank { activeAccount.mask }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = Space.m, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (copyText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(copyText))
                                android.widget.Toast.makeText(context, "شماره کپی شد: $copyText", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = Space.s, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardCopyIcon(
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "کپی شماره",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Box(
                        Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable { onCardClick(activeAccount) }
                        .padding(horizontal = Space.s, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "گردش حساب ${activeAccount.bankFa}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
