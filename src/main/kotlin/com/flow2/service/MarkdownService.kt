package com.flow2.service

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

class MarkdownService {
    private val parser = Parser
        .builder(MutableDataSet().set(
            Parser.EXTENSIONS,
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create()
            )
        ))
        .build();

    private val htmlRenderer = HtmlRenderer
        .builder(MutableDataSet().set(
        Parser.EXTENSIONS,
        listOf(
            TablesExtension.create(),
            StrikethroughExtension.create()
        )
    )).build();

    fun parseToHtml(mdContent: String): String {
        val document = parser.parse(mdContent)
        return htmlRenderer.render(document)
    }
}
