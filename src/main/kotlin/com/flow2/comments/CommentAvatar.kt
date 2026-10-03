package com.flow2.comments

import java.util.HexFormat
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class CommentAvatar(secret: String) {
    private val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    fun seed(name: String, email: String?): String {
        val normalizedEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
        val identity = if (normalizedEmail != null) {
            "email:$normalizedEmail"
        } else {
            "name:${name.trim().lowercase(Locale.ROOT)}"
        }

        // Domain separation keeps avatar hashes distinct from other uses of the key.
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return HexFormat.of().formatHex(mac.doFinal("comment-avatar:$identity".toByteArray(Charsets.UTF_8)))
    }
}
