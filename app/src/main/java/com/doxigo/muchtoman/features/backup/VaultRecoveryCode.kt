package com.doxigo.muchtoman.features.backup

import java.security.SecureRandom

/**
 * The 12-word recovery code, and the vault key it stands for.
 *
 * ## Why 12 words and a 256-word list
 *
 * 12 words × 8 bits = 96 bits, of which the first 88 are entropy and the last byte is a
 * checksum. That is short enough to write on paper by hand and strong enough that guessing it
 * is not an attack anybody can run: 2^88. A BIP39 list (2048 words, 11 bits each) would put 128
 * bits in 12 words, but it also ships a 13 KB list this app does not otherwise need. The list
 * here is fixed, ordered, lowercase ASCII, and pinned by a test — it can never change without
 * invalidating every code already written down.
 *
 * ## Checksum
 *
 * The 12th word is `SHA-256(entropy)[0]`, so a single mistyped word is rejected with
 * probability 255/256 before any network request is made. The remaining 1/256 of typos produce
 * a well-formed but wrong key — which fails to find a blob, and reads as "no backup", not as
 * somebody else's data. This is a typo check, not authentication; [VaultKey.id] is the
 * unguessable identifier and the AES-GCM tag on the file is the actual authentication.
 *
 * ## What the code is and is not
 *
 * The code derives *the vault id and the access credential*. It is not the backup passphrase:
 * the worker stores an encrypted envelope, so opening it still needs the passphrase. Both are
 * required to restore, which is why the UI says so out loud.
 */
object VaultRecoveryCode {

    const val WORD_COUNT: Int = 12
    const val WORDLIST_SIZE: Int = 256
    const val ENTROPY_BYTES: Int = 11

    /**
     * Fixed for ever. Appending is not allowed either: indices are the wire format, so a new
     * word inserted in the middle silently changes the meaning of every existing code.
     * A v2 list would be a new object and a new vault id domain.
     */
    val WORDS: List<String> = listOf(
        "acorn", "actor", "adapt", "adult", "agent", "almond", "amber", "anchor", "animal", "ankle",
        "answer", "apple", "apron", "arch", "arctic", "arrow", "artist", "aspen", "atlas", "attic",
        "autumn", "awake", "axis", "azure", "badge", "bagel", "baker", "bamboo", "banjo", "barley",
        "basket", "beach", "beam", "bean", "beard", "beaver", "bell", "belt", "bench", "berry",
        "bike", "birch", "bird", "biscuit", "blade", "blanket", "blaze", "blend", "bloom", "board",
        "boat", "bolt", "bone", "bonus", "book", "boots", "border", "bottle", "boulder", "branch",
        "brass", "bread", "breeze", "brick", "bridge", "bright", "bronze", "brook", "brush", "bubble",
        "bucket", "buffalo", "bugle", "bunny", "butter", "button", "cabin", "cable", "cactus", "camel",
        "camera", "canal", "candle", "candy", "canoe", "canvas", "canyon", "carbon", "carpet", "carrot",
        "castle", "cedar", "cello", "chalk", "charm", "cherry", "chess", "chief", "child", "chime",
        "chimney", "chip", "cider", "cinema", "circle", "circus", "clay", "cliff", "cloak", "clock",
        "cloud", "clover", "coast", "cocoa", "coffee", "coin", "comet", "compass", "copper", "coral",
        "corn", "cotton", "couch", "country", "court", "cove", "crab", "crane", "crate", "crayon",
        "creek", "crest", "cricket", "crown", "crystal", "cube", "daisy", "dance", "dawn", "deer",
        "denim", "desert", "desk", "diamond", "diary", "dime", "dinner", "dish", "dock", "dolphin",
        "dome", "donkey", "door", "dove", "dragon", "dream", "dress", "drift", "drum", "duck",
        "dune", "dusk", "eagle", "earth", "easel", "east", "echo", "edge", "elder", "elf",
        "elk", "elm", "ember", "emerald", "engine", "entry", "envelope", "equal", "eraser", "essay",
        "estate", "evening", "exit", "fable", "fabric", "falcon", "farm", "feather", "fence", "fern",
        "ferry", "fiddle", "field", "fig", "filter", "finch", "fire", "fish", "flag", "flame",
        "flute", "foam", "fog", "forest", "fork", "fossil", "fountain", "fox", "frame", "frog",
        "frost", "fruit", "garden", "garlic", "gate", "gecko", "gem", "gift", "ginger", "giraffe",
        "glass", "globe", "glove", "goat", "gold", "goose", "gourd", "grain", "grape", "grass",
        "gravel", "green", "guitar", "gull", "hammer", "harbor", "harp", "hat", "hawk", "hay",
        "hazel", "heart", "hedge", "herb", "hero", "hill", "hive", "honey", "horse", "hotel",
        "house", "igloo", "ink", "iron", "island", "ivory",
    )

    private val INDEX: Map<String, Int> by lazy { WORDS.withIndex().associate { it.value to it.index } }

    private const val KEY_DOMAIN = "muchtoman-vault-key-v1"
    private const val ID_DOMAIN = "muchtoman-vault-id-v1"

    /**
     * A recovery key. Deliberately not a data class: the bytes are secret, so equality is
     * content equality and `toString` prints the id only, never the key.
     */
    class VaultKey(val key: ByteArray, val id: String) {
        val shortId: String get() = id.take(8)

        override fun equals(other: Any?): Boolean =
            other is VaultKey && id == other.id && key.contentEquals(other.key)

        override fun hashCode(): Int = 31 * id.hashCode() + key.contentHashCode()

        override fun toString(): String = "VaultKey($shortId…)"
    }

    /** A fresh 88-bit entropy plus its checksum word. */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val entropy = ByteArray(ENTROPY_BYTES).also { random.nextBytes(it) }
        return encode(entropy)
    }

    fun encode(entropy: ByteArray): String {
        require(entropy.size == ENTROPY_BYTES) { "entropy must be $ENTROPY_BYTES bytes" }
        val checksum = BackupCrypto.sha256(entropy)[0].toInt() and 0xff
        val out = ArrayList<String>(WORD_COUNT)
        for (byte in entropy) out += WORDS[byte.toInt() and 0xff]
        out += WORDS[checksum]
        return out.joinToString(" ")
    }

    /** Null for any code that is not exactly 12 known words with a matching checksum. */
    fun parse(raw: String): VaultKey? {
        val words = normalize(raw).split(' ').filter { it.isNotEmpty() }
        if (words.size != WORD_COUNT) return null
        val indices = IntArray(WORD_COUNT)
        for (i in words.indices) {
            indices[i] = INDEX[words[i]] ?: return null
        }
        val entropy = ByteArray(ENTROPY_BYTES) { indices[it].toByte() }
        val checksum = indices[ENTROPY_BYTES]
        if ((BackupCrypto.sha256(entropy)[0].toInt() and 0xff) != checksum) return null
        return keyFor(entropy)
    }

    /** Same code, whitespace and case cleaned up. Persian/Arabic spacing is normalised too. */
    fun normalize(raw: String): String {
        val builder = StringBuilder(raw.length)
        var pendingSpace = false
        for (ch in raw.trim()) {
            if (ch.isWhitespace() || ch == '\u200c' || ch == '\u00a0') {
                pendingSpace = builder.isNotEmpty()
                continue
            }
            if (pendingSpace) {
                builder.append(' ')
                pendingSpace = false
            }
            builder.append(ch.lowercaseChar())
        }
        return builder.toString()
    }

    fun keyFor(entropy: ByteArray): VaultKey {
        require(entropy.size == ENTROPY_BYTES) { "entropy must be $ENTROPY_BYTES bytes" }
        val key = BackupCrypto.sha256(KEY_DOMAIN.toByteArray(Charsets.UTF_8) + entropy)
        val id = BackupCrypto.toHex(BackupCrypto.sha256(ID_DOMAIN.toByteArray(Charsets.UTF_8) + key))
        return VaultKey(key, id)
    }

    /** The id is a hash of the key: unguessable without the code, and stable for the same code. */
    fun idFor(key: ByteArray): String =
        BackupCrypto.toHex(BackupCrypto.sha256(ID_DOMAIN.toByteArray(Charsets.UTF_8) + key))
}
