package com.flow2.service

import kotlin.test.Test

class MarkdownServiceTest {

    private val markdownService = MarkdownService()

    private val mediaDir = "/media/post-id-123"

    private val mdContent = """
fdffsdfsdfsd

> When awake, we see only a narrow set of all possible memory interrelationships. The opposite is true, however, when we enter the dream state and start looking through the other end of the memory-surveying telescope. Using that wide-angle dream lens, we can apprehend the full constellation of stored information and their diverse combinatorial possibilities
> 
> — Matthew Walker, pg. 203

dfdsfsdfsd
    """.trimIndent()

    @Test
    fun parseHtmlContent() {
        val html = markdownService.parseHtmlContent(mdContent, mediaDir)
        println(html)

    }


}