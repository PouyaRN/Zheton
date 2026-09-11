package com.doxigo.muchtoman.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.Category
import com.doxigo.muchtoman.CategoryIcon
import com.doxigo.muchtoman.CategoryKind
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.faNumber

/**
 * Scoped Categories management screen extracted from monolithic Ui.kt.
 * Manages category list, creation dialogs, and delete/reassign flows.
 */
@Composable
fun CategoriesScreen(
    categories: List<Category>,
    hiddenCategories: Set<String> = emptySet(),
    categoryCounts: Map<String, Int> = emptyMap(),
    onAdd: (String, String) -> Unit,
    onEdit: (String, String) -> Unit,
    /** (category id, target id) — target null deletes outright (only when empty is safe). */
    onDelete: (String, String?) -> Unit,
    onToggleHidden: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var newName by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf(CategoryKind.EXPENSE) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xl, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                }
                Text(
                    "دسته‌بندی‌ها",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { adding = true; newName = ""; selectedKind = CategoryKind.EXPENSE }) {
                    Icon(Icons.Rounded.Add, contentDescription = "افزودن")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.xxl),
            ) {
                items(categories, key = { it.id }) { category ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.xl, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIcon(
                            category.nameFa,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 20.dp,
                            stroke = 1.6.dp,
                        )
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(category.nameFa, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val count = categoryCounts[category.id] ?: 0
                            val countText = if (count > 0) "${faNumber(count.toDouble())} تراکنش" else "بدون تراکنش"
                            Text(
                                countText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Built-in categories cannot be deleted or renamed, but can be hidden.
                        if (!category.builtin) {
                            TextButton(onClick = {
                                editingCategory = category
                                newName = category.nameFa
                            }) {
                                Text("ویرایش", fontSize = 12.sp)
                            }
                            TextButton(onClick = { deletingCategory = category }) {
                                Text("حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            val hidden = category.id in hiddenCategories
                            TextButton(onClick = { onToggleHidden(category.id, !hidden) }) {
                                Text(
                                    if (hidden) "نمایش" else "پنهان",
                                    fontSize = 12.sp,
                                    color = if (hidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add category dialog
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("دسته‌بندی جدید") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("نام دسته") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        FilterChip(
                            selected = selectedKind == CategoryKind.EXPENSE,
                            onClick = { selectedKind = CategoryKind.EXPENSE },
                            label = { Text("هزینه") },
                        )
                        FilterChip(
                            selected = selectedKind == CategoryKind.INCOME,
                            onClick = { selectedKind = CategoryKind.INCOME },
                            label = { Text("درآمد") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onAdd(newName.trim(), selectedKind)
                        adding = false
                    },
                ) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text("لغو") }
            },
        )
    }

    // Edit category dialog
    editingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("ویرایش دسته‌بندی") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("نام جدید") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank() && newName.trim() != cat.nameFa,
                    onClick = {
                        onEdit(cat.id, newName.trim())
                        editingCategory = null
                    },
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("لغو") }
            },
        )
    }

    // Delete confirmation dialog. Empty categories delete outright; a category with
    // transactions must say where they go before it may die, so nothing is orphaned.
    deletingCategory?.let { cat ->
        val count = categoryCounts[cat.id] ?: 0
        if (count == 0) {
            AlertDialog(
                onDismissRequest = { deletingCategory = null },
                title = { Text("حذف دسته‌بندی") },
                text = { Text("«${cat.nameFa}» خالی است و حذف می‌شود.") },
                confirmButton = {
                    TextButton(
                        onClick = { onDelete(cat.id, null); deletingCategory = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("حذف") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingCategory = null }) { Text("لغو") }
                },
            )
        } else {
            var targetId by remember(cat.id) { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { deletingCategory = null },
                title = { Text("انتقال تراکنش‌های «${cat.nameFa}»") },
                text = {
                    Column {
                        Text(
                            "${faNumber(count.toDouble())} تراکنش در این دسته هست. قبل از حذف باید به دسته دیگری بروند.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(Space.m))
                        LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(categories.filter { it.id != cat.id }, key = { it.id }) { target ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Radius.field))
                                        .background(
                                            if (target.id == targetId) MaterialTheme.colorScheme.primaryContainer
                                             else Color.Transparent
                                        )
                                        .clickable { targetId = target.id }
                                        .padding(Space.m),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CategoryIcon(
                                        target.nameFa,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        size = 18.dp,
                                        stroke = 1.4.dp,
                                    )
                                    Spacer(Modifier.width(Space.m))
                                    Text(target.nameFa, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = targetId != null,
                        onClick = {
                            targetId?.let { onDelete(cat.id, it) }
                            deletingCategory = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("انتقال و حذف") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingCategory = null }) { Text("لغو") }
                },
            )
        }
    }
}
