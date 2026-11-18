package com.cowatch.app.pairing

import java.security.SecureRandom

/**
 * One-time pairing codes like `K7P-Q2M`. The alphabet leaves out look-alikes
 * (0/O, 1/I/L) so codes are easy to read out loud over the phone.
 */
object PairingCode {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6
    const val EXPIRY_MS = 10 * 60 * 1000L
    private const val PEER_PREFIX = "cowatch-v1-"
    private val random = SecureRandom()

    fun generate(): String = buildString {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** Accepts what people type: lowercase, spaces, dashes. */
    fun normalize(input: String): String = input
        .uppercase()
        .filter { it.isLetterOrDigit() }
        .take(LENGTH)

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    fun format(code: String): String =
        if (code.length == LENGTH) code.substring(0, 3) + "-" + code.substring(3) else code

    /** The id the host registers on the signaling server. */
    fun hostPeerId(code: String): String = PEER_PREFIX + code.lowercase()

    fun guestPeerId(): String = PEER_PREFIX + "guest-" + buildString {
        repeat(12) { append(ALPHABET[random.nextInt(ALPHABET.length)].lowercaseChar()) }
    }

    fun deepLink(code: String): String = "cowatch://join/$code"

    fun fromDeepLink(uri: String): String? {
        val prefix = "cowatch://join/"
        if (!uri.startsWith(prefix, ignoreCase = true)) return null
        return normalize(uri.substring(prefix.length)).takeIf(::isValid)
    }
}
