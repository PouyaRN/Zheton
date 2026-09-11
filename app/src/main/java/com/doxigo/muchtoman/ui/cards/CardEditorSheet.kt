package com.doxigo.muchtoman.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.BankAccount
import com.doxigo.muchtoman.BankCard3D
import com.doxigo.muchtoman.BankCardThemes
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.SheetFlingGuard
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics

/**
 * Scoped Bank Card Editor bottom sheet extracted from monolithic Ui.kt.
 * Enables modifying bank card decoration fields (nickname, full PAN, expiry, theme, SHEBA)
 * with real-time interactive 3D metallic preview and FintechHaptics feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorSheet(
    account: BankAccount,
    onSave: (nickname: String, pan: String, expiry: String, theme: String, sheba: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var nicknameDraft by remember(account.key) { mutableStateOf(account.nickname) }
    var panDraft by remember(account.key) { mutableStateOf(account.fullPan) }
    var expiryDraft by remember(account.key) { mutableStateOf(account.expiry) }
    var themeDraft by remember(account.key) { mutableStateOf(account.theme) }
    var shebaDraft by remember(account.key) { mutableStateOf(account.sheba) }
    var revealPan by remember(account.key) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .nestedScroll(SheetFlingGuard)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Space.xl),
        ) {
            Text(
                "ویرایش کارت ${account.bankFa}",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "این مشخصات فقط روی کارت همین حساب می‌نشیند؛ موجودی و تراکنش‌ها از پیامک بانک می‌آیند.",
                fontSize = 12.sp,
                color = muted,
                modifier = Modifier.padding(top = Space.xs),
            )

            val fintechHaptics = LocalFintechHaptics.current

            // Live 3D Bank Card Studio Interactive Preview
            val previewAccount = remember(account, nicknameDraft, panDraft, expiryDraft, themeDraft, shebaDraft) {
                account.copy(
                    nickname = nicknameDraft,
                    fullPan = panDraft,
                    expiry = expiryDraft,
                    theme = themeDraft,
                    sheba = shebaDraft,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.m),
                contentAlignment = Alignment.Center,
            ) {
                BankCard3D(
                    bankAccount = previewAccount,
                    cardHolderName = previewAccount.nickname.ifBlank { "کاربر چندتومان" },
                    privacyMode = !revealPan,
                    enable3DTiltDrag = true,
                )
            }

            OutlinedTextField(
                value = nicknameDraft,
                onValueChange = { nicknameDraft = it },
                label = { Text("نام مستعار (دلخواه)") },
                placeholder = { Text("مثلاً: کارت اصلی، حقوق، اسنپ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.s))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(
                    value = panDraft,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(16)
                        panDraft = digits
                    },
                    label = { Text("شماره ۱۶ رقمی کارت") },
                    placeholder = { Text("xxxx xxxx xxxx xxxx") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = expiryDraft,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() || it == '/' }.take(5)
                        expiryDraft = clean
                    },
                    label = { Text("انقضا") },
                    placeholder = { Text("MM/YY") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                )
            }
            Spacer(Modifier.height(Space.s))

            OutlinedTextField(
                value = shebaDraft,
                onValueChange = { input ->
                    val clean = input.filter { it.isLetterOrDigit() }.uppercase().take(26)
                    shebaDraft = clean
                },
                label = { Text("شماره شبا") },
                placeholder = { Text("IR000000000000000000000000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.m))
            Text(
                "طراحی کارت و استایل ظاهری",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Space.s))

            // Bank card theme preset picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Bank Default theme pill
                val bankSelected = themeDraft.isBlank()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF263238))
                        .border(
                            if (bankSelected) 3.dp else 1.dp,
                            if (bankSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                        .clickable {
                            fintechHaptics.click()
                            themeDraft = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("بانک", fontSize = 9.sp, color = Color.White)
                }
                BankCardThemes.PRESETS.forEach { (id, preset) ->
                    val selected = themeDraft == id
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(preset.backgroundGradient))
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .clickable {
                                fintechHaptics.click()
                                themeDraft = id
                            },
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("انصراف", fontSize = 13.sp)
                }
                Spacer(Modifier.width(Space.s))
                Button(
                    onClick = {
                        fintechHaptics.heavyClick()
                        onSave(nicknameDraft, panDraft, expiryDraft, themeDraft, shebaDraft)
                    },
                    shape = RoundedCornerShape(Radius.pill),
                ) {
                    Text("ذخیره", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}
