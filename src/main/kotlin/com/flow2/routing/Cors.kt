package com.flow2.routing

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.net.URI

fun Application.configureCors() {
    val siteUrl = URI(environment.config.property("app.schemeDomainPort").getString())

    routing {
        install(CORS) {
            anyHost()
            anyMethod()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
        }

        route("/api/posts/{postId}/comments") {
            restrictToSiteOrigin(siteUrl)
        }

        route("/admin/comments") {
            restrictToSiteOrigin(siteUrl)
        }
    }
}

private fun Route.restrictToSiteOrigin(siteUrl: URI) {
    install(CORS) {
        allowSameOrigin = false
        allowHost(siteUrl.rawAuthority, schemes = listOf(siteUrl.scheme))
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Comment-Request")
    }

    // Route-scoped CORS needs matching routes for preflight requests.
    options {
        call.respond(HttpStatusCode.OK)
    }
    options("/{path...}") {
        call.respond(HttpStatusCode.OK)
    }
}
