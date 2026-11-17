package com.doxigo.muchtoman

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The bank's own mark, on a white plate.
 *
 * The plate is not decoration: these are the banks' real logos, drawn for a white page, and a
 * dozen of them are near-black — on the dark theme they would be a black shape on a dark card.
 * The `when` is exhaustive on purpose, so a bank added to the enum cannot ship without one;
 * every logo the list has is already in `res/drawable` as `ic_bank_<name>`.
 */
@Composable
internal fun BankLogo(bank: String, size: Dp = 32.dp) {
    val enumLogo = when (runCatching { Bank.valueOf(bank) }.getOrDefault(Bank.OTHER)) {
        Bank.BLU -> R.drawable.ic_bank_blu
        Bank.SAMAN -> R.drawable.ic_bank_saman
        Bank.REFAH -> R.drawable.ic_bank_refah
        Bank.PASARGAD -> R.drawable.ic_bank_pasargad
        Bank.EGHTESAD_NOVIN -> R.drawable.ic_bank_eghtesad_novin
        Bank.KHAVARMIANEH -> R.drawable.ic_bank_khavar_mianeh
        Bank.SADERAT -> R.drawable.ic_bank_saderat
        Bank.RESALAT -> R.drawable.ic_bank_resalat
        Bank.PARSIAN -> R.drawable.ic_bank_parsian
        Bank.MELLAT -> R.drawable.ic_bank_mellat
        Bank.MELLI -> R.drawable.ic_bank_melli
        Bank.MASKAN -> R.drawable.ic_bank_maskan
        Bank.SEPAH -> R.drawable.ic_bank_sepah
        Bank.TEJARAT -> R.drawable.ic_bank_tejarat
        Bank.OTHER -> null
    }

    val extendedLogo = when {
        bank.contains("KESHAVARZI", ignoreCase = true) || bank.contains("کشاورزی") -> R.drawable.ic_bank_keshavarzi
        bank.contains("SHAHR", ignoreCase = true) || bank.contains("شهر") -> R.drawable.ic_bank_shahr
        bank.contains("AYANDEH", ignoreCase = true) || bank.contains("آینده") -> R.drawable.ic_bank_ayandeh
        bank.contains("SINA", ignoreCase = true) || bank.contains("سینا") -> R.drawable.ic_bank_sina
        bank.contains("POST", ignoreCase = true) || bank.contains("پست") -> R.drawable.ic_bank_post
        bank.contains("MEHR_IRAN", ignoreCase = true) || bank.contains("مهر") -> R.drawable.ic_bank_mehr_iran
        bank.contains("GARDESHGARI", ignoreCase = true) || bank.contains("گردشگری") -> R.drawable.ic_bank_gardeshgari
        bank.contains("SARMAYEH", ignoreCase = true) || bank.contains("سرمایه") -> R.drawable.ic_bank_sarmayeh
        bank.contains("IRAN_ZAMIN", ignoreCase = true) || bank.contains("ایران زمین") -> R.drawable.ic_bank_iran_zamin
        bank.contains("DEY", ignoreCase = true) || bank.contains("دی") -> R.drawable.ic_bank_dey
        bank.contains("SANAT_MADAN", ignoreCase = true) || bank.contains("صنعت و معدن") -> R.drawable.ic_bank_sanat_madan
        bank.contains("TOSEE_TAAVON", ignoreCase = true) || bank.contains("توسعه تعاون") -> R.drawable.ic_bank_tosee_taavon
        bank.contains("GHAVAMIN", ignoreCase = true) || bank.contains("قوامین") -> R.drawable.ic_bank_ghavamin
        bank.contains("KOSAR", ignoreCase = true) || bank.contains("کوثر") -> R.drawable.ic_bank_kosar
        bank.contains("KARAFARIN", ignoreCase = true) || bank.contains("کارآفرین") -> R.drawable.ic_bank_karafarin
        else -> null
    }

    val logo = enumLogo ?: extendedLogo

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (logo != null) Color.White else MaterialTheme.colorScheme.surfaceVariant,
            )
            // The name is right beside it in text; a second reading of it is noise.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                // The inset scales with the plate: a fixed 4dp that reads as breathing room at
                // 32dp is a hairline at 44dp, and the mark then touches the circle's edge.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size / 8),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_asset_bank),
                contentDescription = null,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}

@Composable
internal fun WalletNetworkLogo(network: String, size: Dp = 28.dp) {
    val logo = when (network.lowercase()) {
        "bitcoin" -> R.drawable.ic_network_bitcoin
        "ethereum" -> R.drawable.ic_network_ethereum
        "solana" -> R.drawable.ic_network_solana
        "tron" -> R.drawable.ic_network_tron
        "bsc" -> R.drawable.ic_network_bsc
        "arbitrum" -> R.drawable.ic_network_arbitrum
        "polygon" -> R.drawable.ic_network_polygon
        "optimism" -> R.drawable.ic_network_optimism
        "avalanche" -> R.drawable.ic_network_avalanche
        else -> null
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    network.take(2).uppercase(),
                    // Scaled, not fixed: at badge size two letters at 10sp are wider than the
                    // disc they sit in, and the fallback clipped instead of naming the chain.
                    fontSize = (size.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
