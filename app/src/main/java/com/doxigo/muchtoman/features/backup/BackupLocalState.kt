package com.doxigo.muchtoman.features.backup

import android.content.Context
import com.doxigo.muchtoman.BuildConfig

/**
 * What the backup screen remembers between visits. Its own preferences file, so this feature
 * adds no keys to `Data.kt`'s store and cannot collide with one.
 *
 * **Nothing secret is ever written here.** Not the passphrase, not the recovery code, not the
 * vault id and not the vault key. `vaultEnabled` is a preference; `recoveryAcknowledged` records
 * that she confirmed writing the code down (so the app can stop nagging, not so it can show the
 * code again — it cannot, and that is the point). Re-entering the 12 words is required for every
 * upload or restore.
 */
class BackupLocalState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("muchtoman_backup", Context.MODE_PRIVATE)

    /** Where the vault lives. Empty means "the same Worker the household uses". */
    var vaultBaseUrl: String
        get() = prefs.getString(KEY_VAULT_BASE, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SYNC_URL
        set(value) = prefs.edit().putString(KEY_VAULT_BASE, value.trim()).apply()

    /** Whether she turned the vault on. Turning it off forgets nothing on the server. */
    var vaultEnabled: Boolean
        get() = prefs.getBoolean(KEY_VAULT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VAULT_ENABLED, value).apply()

    /** She ticked "I wrote the recovery code down" at least once. */
    var recoveryAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_RECOVERY_ACK, false)
        set(value) = prefs.edit().putBoolean(KEY_RECOVERY_ACK, value).apply()

    var lastExportAt: Long
        get() = prefs.getLong(KEY_LAST_EXPORT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_EXPORT, value).apply()

    var lastImportAt: Long
        get() = prefs.getLong(KEY_LAST_IMPORT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_IMPORT, value).apply()

    var lastUploadAt: Long
        get() = prefs.getLong(KEY_LAST_UPLOAD, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPLOAD, value).apply()

    var lastVaultRestoreAt: Long
        get() = prefs.getLong(KEY_LAST_RESTORE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RESTORE, value).apply()

    private companion object {
        const val KEY_VAULT_BASE = "vault_base"
        const val KEY_VAULT_ENABLED = "vault_enabled"
        const val KEY_RECOVERY_ACK = "recovery_acknowledged"
        const val KEY_LAST_EXPORT = "last_export_at"
        const val KEY_LAST_IMPORT = "last_import_at"
        const val KEY_LAST_UPLOAD = "last_upload_at"
        const val KEY_LAST_RESTORE = "last_vault_restore_at"
    }
}
