package com.flow2.request.web

import org.thymeleaf.context.IExpressionContext
import org.thymeleaf.dialect.AbstractDialect
import org.thymeleaf.dialect.IExpressionObjectDialect
import org.thymeleaf.expression.IExpressionObjectFactory

class RequestUrlBuilderDialect(private val builder: RequestUrlBuilder) : AbstractDialect("urlBuilderDialect"), IExpressionObjectDialect {

    override fun getExpressionObjectFactory(): IExpressionObjectFactory {
        return object : IExpressionObjectFactory {

            override fun getAllExpressionObjectNames(): Set<String> {
                return setOf("urls")
            }

            override fun buildObject(
                context: IExpressionContext?,
                expressionObjectName: String?
            ): Any? {
                return builder
            }

            override fun isCacheable(expressionObjectName: String): Boolean {
                return true
            }
        }
    }
}