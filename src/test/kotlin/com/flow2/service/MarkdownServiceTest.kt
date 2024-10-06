package com.flow2.service

import kotlin.test.Test

class MarkdownServiceTest {

    private val markdownService = MarkdownService()

    private val mediaDir = "/media/post-id-123"

    private val mdContent = """
        # Hello World
        This is a test markdown content.
        ~~This is a strikethrough text.~~
    """.trimIndent()

    @Test
    fun parseHtmlContent() {
        val html = markdownService.parseHtmlContent(mdContent, mediaDir)
        println(html)

    }


}