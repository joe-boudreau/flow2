package com.flow2.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommentAvatarTest {
    private val avatars = CommentAvatar("test-secret")

    @Test
    fun `email takes precedence and ignores surrounding spaces and case`() {
        assertEquals(avatars.seed("Alice", " User@Example.com "), avatars.seed("Other name", "user@example.com"))
    }

    @Test
    fun `missing email falls back to normalized name`() {
        assertEquals(avatars.seed(" Alice ", null), avatars.seed("alice", "  "))
        assertNotEquals(avatars.seed("Alice", null), avatars.seed("Bob", null))
    }

    @Test
    fun `seed is keyed hexadecimal and separates names from emails`() {
        val seed = avatars.seed("Alice", "user@example.com")
        assertTrue(seed.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(seed, CommentAvatar("other-secret").seed("Alice", "user@example.com"))
        assertNotEquals(seed, avatars.seed("user@example.com", null))
    }
}
