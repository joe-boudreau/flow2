package com.flow2.request.web

import io.ktor.server.application.Application
import org.thymeleaf.context.IExpressionContext
import org.thymeleaf.dialect.AbstractDialect
import org.thymeleaf.dialect.IExpressionObjectDialect
import org.thymeleaf.expression.IExpressionObjectFactory

class RequestUrlBuilderDialect(private val app: Application) : AbstractDialect("urlBuilderDialect"), IExpressionObjectDialect {

    override fun getExpressionObjectFactory(): IExpressionObjectFactory {
        return object : IExpressionObjectFactory {

            override fun getAllExpressionObjectNames(): Set<String> {
                return setOf("urls")
            }

            override fun buildObject(
                context: IExpressionContext?,
                expressionObjectName: String?
            ): Any? {
                return RequestUrlBuilder(app)
            }

            override fun isCacheable(expressionObjectName: String): Boolean {
                return true
            }
        }
    }
}