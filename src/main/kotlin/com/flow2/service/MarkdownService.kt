package com.flow2.service

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension
import com.vladsch.flexmark.formatter.Formatter
import com.vladsch.flexmark.formatter.internal.MergeLinkResolver
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

class MarkdownService {

    fun parseHtmlContent(mdContent: String, postMediaDir: String): String {
        val imgUrlPrefix = "$postMediaDir/"

        val options = MutableDataSet()
        options
            .set(Formatter.DOC_RELATIVE_URL, imgUrlPrefix)
            .set(Formatter.DOC_ROOT_URL, imgUrlPrefix)

        options
            .set(WikiLinkExtension.IMAGE_LINKS, true)
            .set(WikiLinkExtension.IMAGE_PREFIX, imgUrlPrefix)
            .set(WikiLinkExtension.IMAGE_PREFIX_ABSOLUTE, imgUrlPrefix)

        options
            .set(Parser.EXTENSIONS, listOf(
                WikiLinkExtension.create(),
                StrikethroughExtension.create()
            ));

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
