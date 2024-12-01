package com.flow2.auth

import io.ktor.server.auth.Principal
import kotlinx.serialization.Serializable

@Serializable
data class AdminUser(val name: String) : Principal