package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.AssetType
import com.doxigo.muchtoman.Holding
import com.doxigo.muchtoman.faHeld
import com.doxigo.muchtoman.resolveType

/**
 * The assets snapshot, computed from what the app already has in memory.
 *
 * This is pure on purpose: the report activity hands it the stored holdings, the catalogue it
 * resolved from cached coins and shares, and the effective rate map. No network call and no
 * database read lives here, so a report is generated offline, instantly, and from exactly the
 * prices the home screen is showing — never from a fresher number the reader cannot see.
 *
 * [ratesToman] is Toman per one unit, the same map `effectiveRates` builds (cached rates +
 * bourse + hand-typed overrides). A holding the map cannot price gets `toman = null`; the
 * builder prints «بدون قیمت» and keeps it out of the sum rather than valuing it at zero.
 */
fun assetLines(
    holdings: List<Holding>,
    types: List<AssetType>,
    ratesToman: Map<String, Double>,
): List<AssetLine> {
    val byId = types.associateBy { it.id }
    return holdings
        // Archived rows are filed away, not owned; the home list hides them for the same reason.
        .filterNot { it.archived }
        .map { holding ->
            // An asset the catalogue no longer knows still renders, with its id as a name, so a
            // holding can never silently disappear from the report along with its value.
            val type = byId[holding.typeId] ?: resolveType(holding.typeId, emptyList(), emptyList())
            val price = ratesToman[holding.typeId]
            AssetLine(
                nameFa = holding.nameOr(type.fa),
                amountFa = "${faHeld(holding.amount, type.dec)} ${type.unitFa}",
                toman = if (price != null && price > 0.0 && holding.amount > 0.0) {
                    price * holding.amount
                } else {
                    null
                },
                inTotal = !holding.excluded,
            )
        }
        // Largest first: the snapshot is read to answer «پول من کجاست؟», not to preserve the
        // manage-list order she dragged by hand.
        .sortedWith(compareByDescending<AssetLine> { it.toman ?: -1.0 }.thenBy { it.nameFa })
}
