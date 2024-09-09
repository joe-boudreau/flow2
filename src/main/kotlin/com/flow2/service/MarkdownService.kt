package com.flow2.service

import com.vladsch.flexmark.formatter.Formatter.*
import com.vladsch.flexmark.formatter.internal.MergeLinkResolver
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

class MarkdownService {

    fun parseHtmlContent(mdContent: String, imgSrcPrefix: String): String {
        val options = MutableDataSet()
            .set(DOC_ROOT_URL, imgSrcPrefix)
            .set(DEFAULT_LINK_RESOLVER, true)

        val parser = Parser
            .builder(options)
            .build()

        val renderer = HtmlRenderer
            .builder(options)
            .linkResolverFactory(MergeLinkResolver.Factory())
            .build()


        val document = parser.parse(mdContent)
        return renderer.render(document)
    }
}
