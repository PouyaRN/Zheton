package com.doxigo.muchtoman.features.pricealerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.features.reminders.NotificationPermission
import com.doxigo.muchtoman.features.reminders.rememberNotificationPermissionRequest
import com.doxigo.muchtoman.parseAmount

/**
 * Create and delete price alerts for one asset. Self-contained: it reads and writes through
 * [PriceAlerts], so the caller (the Asset screen's «هشدار قیمت» bell) only has to say which asset.
 *
 * Adding an alert is the moment the notification permission is asked for, through the same
 * request the reminders use; an alert added without the permission is still saved and fires
 * once she allows notifications.
 *
 * @param currentRate today's Toman rate for one unit, used to pick the default direction and as
 *   the placeholder; null when there is none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertSheet(
    assetId: String,
    assetName: String,
    currentRate: Double?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val version by PriceAlerts.changes.collectAsState()
    val alerts = remember(version, assetId) { PriceAlerts.forAsset(context, assetId) }

    var input by remember { mutableStateOf("") }
    val target = parseAmount(input)?.takeIf { it > 0.0 }
    var direction by remember { mutableStateOf<AlertDirection?>(null) }
    val effectiveDirection = direction
        ?: PriceAlertRules.defaultDirection(target ?: 0.0, currentRate)
    var permitted by remember { mutableStateOf(NotificationPermission.isGranted(context)) }
    val askPermission = rememberNotificationPermissionRequest { permitted = it }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("هشدار قیمت · $assetName", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (currentRate != null && currentRate > 0.0) {
                Text(
                    "الان ${faNumber(currentRate)} تومان",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("قیمت هدف (تومان)") },
                singleLine = true,
                isError = input.isNotBlank() && target == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertDirection.entries.forEach { d ->
                    FilterChip(
                        selected = effectiveDirection == d,
                        onClick = { direction = d },
                        label = { Text(if (d == AlertDirection.ABOVE) "وقتی بالا رفت" else "وقتی پایین آمد") },
                    )
                }
            }
            Button(
                onClick = {
                    val t = target ?: return@Button
                    PriceAlerts.add(context, assetId, assetName, t, effectiveDirection)
                    input = ""
                    direction = null
                    if (!permitted) askPermission()
                },
                enabled = target != null,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("بذار هشدار بده") }

            if (!permitted && alerts.any { it.armed }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "اعلان‌ها خاموشه؛ هشدار تا روشنش نکنی نمی‌رسه.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { NotificationPermission.openNotificationSettings(context) }) {
                        Text("تنظیمات")
                    }
                }
            }

            alerts.sortedByDescending { it.createdAt }.forEach { alert ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${alert.direction.fa} ${faNumber(alert.target)} تومان",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (alert.armed) "فعال" else "رسید",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { PriceAlerts.remove(context, alert.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف هشدار")
                    }
                }
            }
        }
    }
}
