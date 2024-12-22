package com.flow2.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserHashedTableAuth
import io.ktor.server.auth.form
import io.ktor.server.auth.session
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.util.getDigestFunction

const val ADMIN_LOGIN_CONFIG = "admin-login"
const val ADMIN_SESSION_CONFIG = "admin-session"

fun Application.configureAdminAuth() {
    val adminCookieName = environment.config.property("app.adminAuth.sessionCookie").getString()
    val adminUserName = environment.config.property("app.adminAuth.username").getString()
    val adminPassword = environment.config.property("app.adminAuth.password").getString()
    val salt = environment.config.property("app.adminAuth.digestSalt").getString()

    val adminUserAuthenticator = makeAdminUserAuth(adminUserName, adminPassword, salt)

    install(Sessions) {
        cookie<AdminUser>(adminCookieName, SessionStorageMemory()) {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 3600
        }
    }

    install(Authentication) {
        form(ADMIN_LOGIN_CONFIG) {
            validate { credentials -> adminUserAuthenticator.authenticate(credentials) }
        }
        session<AdminUser>(ADMIN_SESSION_CONFIG) {
            challenge { call.respondRedirect("/login") }
            validate { session: AdminUser -> session }
        }
    }
}

fun makeAdminUserAuth(adminUserName: String, adminPassword: String, salt: String): UserHashedTableAuth {
    val digestFunction = getDigestFunction("SHA-256") { salt + it.length }
    return UserHashedTableAuth(
        table = mapOf(
            adminUserName to digestFunction(adminPassword)
        ),
        digester = digestFunction
    )
}