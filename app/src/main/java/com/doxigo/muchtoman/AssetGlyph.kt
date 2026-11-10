package com.doxigo.muchtoman

import androidx.annotation.DrawableRes

/**
 * Deterministic vector glyph resolver for every static and dynamic [AssetType].
 * Replaces OEM-dependent Unicode emoji font glyphs with bundled VectorDrawables.
 */
@DrawableRes
fun assetGlyph(type: AssetType): Int = assetGlyphById(type.id, type.kind)

@DrawableRes
fun assetGlyphById(id: String, kind: Kind = Kind.CRYPTO): Int = when (id.lowercase()) {
    TOMAN_ID -> R.drawable.ic_asset_cash
    BANK_ID -> R.drawable.ic_asset_bank
    "usd" -> R.drawable.ic_asset_usd
    "eur" -> R.drawable.ic_asset_eur
    "gbp" -> R.drawable.ic_asset_gbp
    "nok" -> R.drawable.flag_no
    "try" -> R.drawable.flag_tr
    "aed" -> R.drawable.flag_ae
    "cad" -> R.drawable.flag_ca
    "gold18", "gold_mesghal" -> R.drawable.ic_asset_gold
    "silver_999", "silver_925" -> R.drawable.ic_asset_silver
    "coin_emami", "coin_bahar", "coin_nim", "coin_rob", "coin_gerami", "parsian" -> R.drawable.ic_asset_coin
    else -> when {
        id.startsWith("parsian_") -> R.drawable.ic_asset_coin
        else -> when (kind) {
            Kind.CASH -> R.drawable.ic_asset_cash
            Kind.FIAT -> R.drawable.ic_asset_usd
            Kind.GOLD -> R.drawable.ic_asset_gold
            Kind.SILVER -> R.drawable.ic_asset_silver
            Kind.COIN -> R.drawable.ic_asset_coin
            Kind.STOCK -> R.drawable.ic_asset_stock
            Kind.CRYPTO -> R.drawable.ic_asset_crypto
        }
    }
}
