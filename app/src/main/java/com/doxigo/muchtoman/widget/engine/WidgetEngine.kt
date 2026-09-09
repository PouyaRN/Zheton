package com.doxigo.muchtoman.widget.engine

import com.doxigo.muchtoman.widget.primitives.WidgetDensity
import kotlinx.serialization.Serializable

@Serializable
enum class WidgetId(val titleFa: String, val badge: String) {
    NET_WORTH("ارزش خالص دارایی", "NET WORTH"),
    CURRENCY("ارز و طلا", "FX / GOLD"),
    FINANCIAL_HEALTH("شاخص سلامت مالی", "HEALTH"),
    ACCOUNTS("ترکیب حساب‌ها", "ACCOUNTS"),
    DAILY_BURN("نرخ سوخت روزانه", "DAILY BURN"),
    SPENDING("گردش مالی ماه", "FLOW"),
    INSTALLMENTS("اقساط فعال", "INSTALLMENTS"),
    TRANSACTIONS("تراکنش‌های اخیر", "STREAM"),
    MARKET_WATCH("دیده‌بان بازار", "MARKETS"),
    BUDGET("بودجه ۵۰/۳۰/۲۰", "BUDGET"),
    PORTFOLIO("سبد دارایی‌ها", "PORTFOLIO"),
    GOALS("اهداف پس‌انداز", "GOALS"),
    BILLS("قبوض و چک‌ها", "BILLS"),
}

@Serializable
data class WidgetSlot(
    val id: WidgetId,
    val density: WidgetDensity = WidgetDensity.STANDARD,
    val isPinned: Boolean = false,
    val isVisible: Boolean = true,
    val order: Int = 0,
)

object WidgetEngineDefaults {
    val defaultSlots: List<WidgetSlot> = listOf(
        WidgetSlot(id = WidgetId.NET_WORTH, density = WidgetDensity.EXPANDED, isPinned = true, isVisible = true, order = 0),
        WidgetSlot(id = WidgetId.CURRENCY, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 1),
        WidgetSlot(id = WidgetId.FINANCIAL_HEALTH, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 2),
        WidgetSlot(id = WidgetId.ACCOUNTS, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 3),
        WidgetSlot(id = WidgetId.DAILY_BURN, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 4),
        WidgetSlot(id = WidgetId.SPENDING, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 5),
        WidgetSlot(id = WidgetId.INSTALLMENTS, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 6),
        WidgetSlot(id = WidgetId.TRANSACTIONS, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 7),
        WidgetSlot(id = WidgetId.MARKET_WATCH, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 8),
        WidgetSlot(id = WidgetId.BUDGET, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 9),
        WidgetSlot(id = WidgetId.PORTFOLIO, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 10),
        WidgetSlot(id = WidgetId.GOALS, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 11),
        WidgetSlot(id = WidgetId.BILLS, density = WidgetDensity.STANDARD, isPinned = false, isVisible = true, order = 12),
    )

    fun mergeWithDefaults(saved: List<WidgetSlot>): List<WidgetSlot> {
        if (saved.isEmpty()) return defaultSlots
        val savedMap = saved.associateBy { it.id }
        val merged = mutableListOf<WidgetSlot>()

        saved.sortedBy { it.order }.forEach { slot ->
            merged.add(slot)
        }

        // Add any missing new widgets
        defaultSlots.forEach { defaultSlot ->
            if (!savedMap.containsKey(defaultSlot.id)) {
                merged.add(defaultSlot.copy(order = merged.size))
            }
        }
        return merged
    }
}
