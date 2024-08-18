package com.flow2.model

import kotlinx.serialization.Serializable

@Serializable
enum class Category {
    BOOK_REVIEW,
    TECH,
    TRAVEL,
    PERSONAL,
    SIDI,
}