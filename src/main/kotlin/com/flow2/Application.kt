package com.flow2

import com.flow2.auth.configureAdminAuth
import com.flow2.repository.media.FSMediaRepository
import com.flow2.repository.media.MediaRepositoryInterface
import com.flow2.repository.posts.MongoPostRepository
import com.flow2.repository.posts.PostRepositoryInterface
import com.flow2.repository.assets.FSSiteAssetRepository
import com.flow2.repository.assets.SiteAssetRepositoryInterface
import com.flow2.request.web.RequestUrlBuilder
import com.flow2.request.web.RequestUrlBuilderDialect
import com.flow2.routing.configureAdminRoutes
import com.flow2.routing.configurePublicRoutes
import com.flow2.service.MarkdownService
import com.flow2.service.PostService
import com.flow2.service.RssService
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.request.path
import io.ktor.server.thymeleaf.*
import org.koin.dsl.module
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templateresolver.FileTemplateResolver
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import io.ktor.server.resources.Resources
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(configureKoinModule())
    }

    install(Resources)
    configureAdminAuth()
    configurePublicRoutes()
    configureAdminRoutes()
    install(Thymeleaf) {
        setTemplateResolver(getTemplateResolver())
        addDialect(RequestUrlBuilderDialect(get<RequestUrlBuilder>()))
    }
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }
    install(CORS) {
        anyHost()
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(DefaultHeaders) {
        header(HttpHeaders.Server, "flow2")
        header("thats-a", "spicy meat-a-ball")
    }
    install(CachingHeaders) {
        // TODO
//        options { call, content ->
//            when (content.contentType?.withoutParameters()) {
//                ContentType.Text.Plain -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600))
//                ContentType.Text.Html -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60))
//                else -> null
//            }
//        }
    }
    install(ForwardedHeaders)
    install(CallLogging)

    log.info("Application started and configured")
}

private fun Application.configureKoinModule() = module {
    val dbConnectionString = environment.config.property("app.db.connectionString").getString()
    val dbName = environment.config.property("app.db.dbName").getString()
    val mediaDirectoryPath = environment.config.property("app.media.directoryPath").getString()
    val assetDirectoryPath = environment.config.property("app.assets.directoryPath").getString()
    val schemeDomainPortConfig = environment.config.property("app.schemeDomainPort").getString()

    val schemeDomainPort = schemeDomainPortConfig.removeSuffix("/")

    // The MongoClient instance actually represents a pool of connections to the database;
    // you will only need one instance of class MongoClient even with multiple threads.
    single<MongoClient> { MongoClient.create(dbConnectionString) }
    factory<MongoDatabase> { get<MongoClient>().getDatabase(dbName) }

    factory<PostRepositoryInterface> { MongoPostRepository(get()) }
    single<MediaRepositoryInterface> { FSMediaRepository(mediaDirectoryPath) }
    single<SiteAssetRepositoryInterface> { FSSiteAssetRepository(assetDirectoryPath) }
    single<MarkdownService>{ MarkdownService(get(), get()) }
    single<PostService>{ PostService(get(), get(), get()) }
    single<RequestUrlBuilder>{ RequestUrlBuilder(this@configureKoinModule, schemeDomainPort) }
    single<RssService>{ RssService(get(), get()) }
}

private fun Application.getTemplateResolver() =
    if (this.developmentMode) {
        FileTemplateResolver().apply {
            isCacheable = false
            prefix = "src/main/resources/templates/"
            suffix = ".html"
            characterEncoding = "utf-8"
        }
    } else {
        ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "utf-8"
        }
    }