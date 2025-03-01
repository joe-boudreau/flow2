package com.flow2.service

import com.flow2.repository.assets.FSSiteAssetRepository
import com.flow2.repository.media.FSMediaRepository
import kotlin.test.Test

class MarkdownServiceTest {

    private val markdownService = MarkdownService(
        siteAssetRepository = FSSiteAssetRepository(),
        mediaRepository = FSMediaRepository(FSSiteAssetRepository(), "/media"),
    )

    private val mediaDir = "/media/post-id-123"

    private val mdContent = """
---
title: The Master & Margarita
publishedAt: 
tags: fiction, Russia, Soviet Union, magical realism, Satan, love
category: BOOK_REVIEW
---

> Follow me, reader! Who told you that there is no true, eternal, and faithful love in the world! May the liar have his foul tongue cut out! Follow me, my reader, and only me, and I will show you such a love!
> 
> — Mikhail Bulgakov, *The Master and Margarita* pg. 180

One thing I need to start doing before reading a foreign language novel is decide what translation to read. If it's been translated more than once, there will certainly be differences in the versions—in prose, grammar, and closeness to the original text. 
    """.trimIndent()

    @Test
    fun parseHtmlContent() {
        val html = markdownService.parseHtmlContent(mdContent, mediaDir)
        println(html)

    }

    @Test
    fun parseFrontMatter() {
        val frontMatter = markdownService.parseFrontMatter(mdContent)
        println(frontMatter["title"]?.firstOrNull())
        println(frontMatter["publishedAt"]?.firstOrNull())
        println(frontMatter["tags"])
        println(frontMatter["category"]?.firstOrNull())
    }


}