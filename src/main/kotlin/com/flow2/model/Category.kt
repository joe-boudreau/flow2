package com.flow2.model

import kotlinx.serialization.Serializable

@Serializable
enum class Category(
    val displayName: String,
    val urlName: String,
) {
    BOOK_REVIEW("Book Reviews", "book-reviews"),
    TECH("Tech", "tech"),
    TRAVEL("Travel", "travel"),
    PERSONAL("Personal", "personal"),
    SIDI("SIDI", "sidi");

    companion object {
        fun getByUrlName(urlName: String): Category? {
            return entries.find { it.urlName == urlName }
        }

        fun getByDisplayName(displayName: String): Category? {
            return entries.find { it.displayName == displayName }
        }
    }
}