package com.doxigo.muchtoman.ui.commitments

import com.doxigo.muchtoman.ui.components.DetailRow
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.doxigo.muchtoman.Category
import com.doxigo.muchtoman.CategoryIcon
import com.doxigo.muchtoman.CategoryKind
import com.doxigo.muchtoman.GroupedNumber
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.InstallmentObligation
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Reservation
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.UiState
import com.doxigo.muchtoman.UrgencyLevel
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.formatShamsiDate
import com.doxigo.muchtoman.installmentDueBadge
import com.doxigo.muchtoman.parseAmount
import com.doxigo.muchtoman.ui.cards.CardEditorSheet
import com.doxigo.muchtoman.ui.categories.CategoriesScreen
import com.doxigo.muchtoman.ui.commitments.CreditPurchaseForm
import com.doxigo.muchtoman.ui.components.DetailRow
import com.doxigo.muchtoman.ui.commitments.DirectObligationSheet
import com.doxigo.muchtoman.ui.commitments.LinkInstallmentPicker
import com.doxigo.muchtoman.ui.commitments.ObligationDetailScreen
import com.doxigo.muchtoman.ui.components.HeroPanel
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun ObligationsCard(
    state: UiState,
    modifier: Modifier = Modifier,
    onOpenObligation: (InstallmentObligation) -> Unit = {},
    onAddObligation: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val obligations = state.obligations.filter { !it.archived }

    if (obligations.isEmpty()) {
        Card(
            shape = RoundedCornerShape(Radius.card),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "اقساط فعالی نیست",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddObligation) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("ثبت قسط", fontSize = 13.sp)
                }
            }
        }
        return
    }

    val totalRemaining = obligations.sumOf { ob ->
        val paid = state.obligationPayments[ob.id]?.count { it.paidDate != null } ?: 0
        val unpaidCount = ob.installmentCount - paid
        unpaidCount.toLong() * ob.installmentRial
    }

    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "اقساط فعال",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddObligation) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("افزودن", fontSize = 13.sp)
                }
                Spacer(Modifier.width(Space.xs))
                Text(
                    "${faNumber(obligations.size.toDouble())} مورد",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "باقیمانده: ${faNumber(totalRemaining / 10.0)} تومان",
                fontSize = 14.sp,
                fontFamily = ModamFigures,
                fontWeight = FontWeight.SemiBold,
            )

            if (expanded) {
                Spacer(Modifier.height(Space.m))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.m))
                obligations.forEach { ob ->
                    val payments = state.obligationPayments[ob.id] ?: emptyList()
                    val paid = payments.count { it.paidDate != null }
                    val remaining = (ob.installmentCount - paid).toLong() * ob.installmentRial
                    val nextUnpaid = payments.filter { it.paidDate == null }.minByOrNull { it.dueDate }
                    val badge = nextUnpaid?.let { installmentDueBadge(it.dueDate) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenObligation(ob) }
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                ob.provider.ifBlank { "اقساط" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${paid}/${ob.installmentCount} قسط پرداخت شده",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                if (badge != null) {
                                    Text(
                                        " • ",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                    val badgeColor = when (badge.level) {
                                        UrgencyLevel.OVERDUE -> MaterialTheme.colorScheme.error
                                        UrgencyLevel.WARNING -> MaterialTheme.colorScheme.secondary
                                        UrgencyLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        badge.text,
                                        fontSize = 11.sp,
                                        fontWeight = if (badge.level != UrgencyLevel.NORMAL) FontWeight.Bold else FontWeight.Normal,
                                        color = badgeColor,
                                    )
                                }
                            }
                        }
                        Text(
                            "${faNumber(remaining / 10.0)}",
                            fontSize = 14.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A card showing active fund reservations.
 * Always rendered on the home screen; shows an empty state when there are no reservations.
 */
@Composable
fun ReservationsCard(
    state: UiState,
    modifier: Modifier = Modifier,
    onRelease: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val reservations = state.reservations.filter { !it.released }

    if (reservations.isEmpty()) {
        Card(
            shape = RoundedCornerShape(Radius.card),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                "رزرو فعالی نیست",
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        return
    }

    val totalReserved = reservations.sumOf { it.amountRial }

    Card(
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "رزروها",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${faNumber(reservations.size.toDouble())} مورد",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "مجموع رزرو: ${faNumber(totalReserved / 10.0)} تومان",
                fontSize = 14.sp,
                fontFamily = ModamFigures,
                fontWeight = FontWeight.SemiBold,
            )

            reservations.forEach { reservation ->
                Spacer(Modifier.height(Space.s))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.s))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            reservation.purpose,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (reservation.categoryId != null) {
                            Text(
                                state.ledger.categories.firstOrNull { it.id == reservation.categoryId }?.nameFa ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Text(
                        "${faNumber(reservation.amountRial / 10.0)}",
                        fontSize = 14.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(Space.s))
                    TextButton(onClick = { onRelease(reservation.id) }) {
                        Text("آزاد", fontSize = 12.sp)
                    }
                    TextButton(onClick = { onDelete(reservation.id) }) {
                        Text("حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet form for adding a new fund reservation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReservationForm(
    categories: List<Category>,
    onSave: (purpose: String, amount: Long, categoryId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var purpose by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
        ) {
            Text(
                "افزودن رزرو",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.l),
            )

            OutlinedTextField(
                value = purpose,
                onValueChange = { purpose = it },
                label = { Text("هدف رزرو") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' } },
                label = { Text("مبلغ (تومان)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Text(
                "دسته‌بندی (اختیاری)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.s),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                modifier = Modifier.padding(bottom = Space.l),
            ) {
                categories.filter { it.kind == CategoryKind.EXPENSE }.forEach { category ->
                    FilterChip(
                        selected = category.id == selectedCategoryId,
                        onClick = {
                            selectedCategoryId = if (category.id == selectedCategoryId) null else category.id
                        },
                        label = { Text(category.nameFa, fontSize = 12.sp) },
                    )
                }
            }

            Button(
                onClick = {
                    val amount = amountText.replace(",", "").toLongOrNull() ?: return@Button
                    if (purpose.isBlank() || amount <= 0) return@Button
                    onSave(purpose, amount, selectedCategoryId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ذخیره")
            }
        }
    }
}

/**
 * What the new build changes, and the two things she can do about it.
 *
 * Nothing updates a sideloaded app on its own, so the button is the entire update path: it hands
 * the file to whatever she downloads with and she installs it herself. The app never fetches the
 * APK, never asks for the installer permission, and nothing touches the phone without her. The
 * link is the Worker's proxied copy, which answers with a Content-Disposition attachment, so the
 * download starts on the tap instead of landing her on a github.com page that, from Iran, most
 * often does not load at all.
 *
 * The two ways out are deliberately not the same. «بعداً» is an answer — the card goes away and
 * this version is not mentioned again. Swiping the sheet down or tapping the scrim is not an
 * answer, so the card is still there afterwards; nobody should lose the update by brushing it
 * away before reading it.
 */
