package com.flow2

import com.flow2.plugin.*
import com.flow2.repository.MediaRepository
import com.flow2.repository.MediaRepositoryInterface
import com.flow2.repository.MongoPostRepository
import com.flow2.repository.PostRepositoryInterface
import com.flow2.service.MarkdownService
import com.flow2.service.PostService
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.thymeleaf.*
import org.koin.dsl.module
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templateresolver.FileTemplateResolver
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import io.ktor.server.resources.Resources

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(Resources)
    configureRouting()
    install(Thymeleaf) {
        setTemplateResolver(getTemplateResolver())
    }
    install(Koin) {
        slf4jLogger()
        modules(module)
    }
    install(ContentNegotiation) {
        json()
    }
}

private val module =  module {

    // The MongoClient instance actually represents a pool of connections to the database;
    // you will only need one instance of class MongoClient even with multiple threads.
    single<MongoClient> {
        //TODO: replace with secrets
        val uri = "mongodb://localhost:27017"
        MongoClient.create(uri)
    }

    factory<MongoDatabase> { get<MongoClient>().getDatabase("flow2") }
    factory<PostRepositoryInterface> { MongoPostRepository(get()) }

    single<MediaRepositoryInterface> { MediaRepository() }

    single<MarkdownService>{ MarkdownService() }
    single<PostService>{ PostService(get(), get(), get()) }
}

private fun getTemplateResolver(): AbstractConfigurableTemplateResolver {
    // TODO: use Koin DI
    val resolver = if (true) {
        FileTemplateResolver().apply {
            isCacheable = false
            prefix = "src/main/resources/templates/"
        }
    } else {
        ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
        }
    }

    resolver.apply {
        suffix = ".html"
        characterEncoding = "utf-8"
    }
    return resolver
}