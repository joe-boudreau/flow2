package com.flow2.auth

import com.flow2.config.*
import io.ktor.server.auth.UserHashedTableAuth
import io.ktor.util.getDigestFunction

val digestFunction = getDigestFunction("SHA-256") { DIGEST_SALT + it.length }

val hashedUserTable = UserHashedTableAuth(
    table = mapOf(
        ADMIN_USERNAME to digestFunction(ADMIN_PASSWORD)
    ),
    digester = digestFunction
)
