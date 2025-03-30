package com.flow2.service

import com.flow2.model.Post
import com.flow2.request.web.RequestUrlBuilder
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.io.SyndFeedOutput
import java.util.Date

class RssService(
    private val requestUrlBuilder: RequestUrlBuilder,
    private val markdownService: MarkdownService,
) {

    fun createRSSFeed(posts: List<Post>): String {
        val feed = SyndFeedImpl()
        feed.feedType = "rss_2.0"
        feed.title = "flow2"
        feed.link = requestUrlBuilder.getRootUrl()
        feed.description = "A blog about software engineering, books, and other things"
        feed.language = "en"
        feed.publishedDate = Date()
        feed.entries = posts.map { post ->
            val htmlContent = markdownService.parseHtmlContent(post.mdContent ?: "")
            SyndEntryImpl().apply {
                title = post.title
                author = "Joe Boudreau"
                link = requestUrlBuilder.getPostAbsoluteUrl(post)
                description = SyndContentImpl().apply {
                    type = "text/plain"
                    value = htmlContent.take(200)
                }
                publishedDate = Date(post.publishedAt)
                updatedDate = Date(post.updatedAt)
                contents = listOf(SyndContentImpl().apply {
                    type = "text/html"
                    value = htmlContent
                })
            }
        }
        return SyndFeedOutput().outputString(feed)
    }
}