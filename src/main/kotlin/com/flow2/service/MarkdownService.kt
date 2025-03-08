package com.flow2.service

import com.flow2.repository.assets.SiteAssetRepositoryInterface
import com.flow2.repository.media.MediaRepositoryInterface
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.attributes.AttributesExtension
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension
import com.vladsch.flexmark.formatter.Formatter
import com.vladsch.flexmark.formatter.internal.MergeLinkResolver
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

class MarkdownService(
    private val siteAssetRepository: SiteAssetRepositoryInterface,
    private val mediaRepository: MediaRepositoryInterface,
) {

    fun parseHtmlContent(mdContent: String): String {
        val imgUrlPrefix = siteAssetRepository.getPublicSiteAssetUrl("images/")
        return parseToHtml(mdContent, imgUrlPrefix)
    }

    fun parseHtmlContent(mdContent: String, postId: String): String {
        val imgUrlPrefix = mediaRepository.getPublicPostMediaUrl("$postId/")
        return parseToHtml(mdContent, imgUrlPrefix)
    }

    private fun parseToHtml(mdContent: String, imgUrlPrefix: String): String {

        val options = MutableDataSet()
        options
            .set(Formatter.DOC_RELATIVE_URL, imgUrlPrefix)
            .set(Formatter.DOC_ROOT_URL, imgUrlPrefix)

        options
            .set(WikiLinkExtension.IMAGE_LINKS, true)
            .set(WikiLinkExtension.IMAGE_PREFIX, imgUrlPrefix)
            .set(WikiLinkExtension.IMAGE_PREFIX_ABSOLUTE, imgUrlPrefix)

        options
            .set(
                Parser.EXTENSIONS, listOf(
                    WikiLinkExtension.create(),
                    StrikethroughExtension.create(),
                    AttributesExtension.create(),
                    YamlFrontMatterExtension.create()
                )
            );

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

    fun parseFrontMatter(mdContent: String): Map<String, List<String>> {
        val options = MutableDataSet()
        options.set(Parser.EXTENSIONS, listOf(YamlFrontMatterExtension.create()))
        val parser = Parser.builder(options).build()
        val document = parser.parse(mdContent)
        val visitor = AbstractYamlFrontMatterVisitor()
        visitor.visit(document);
        return visitor.data
    }

    fun addFrontMatter(mdContent: String, newFrontMatter: Map<String, List<String>>): String {
        val existingFrontMatter = parseFrontMatter(mdContent).toMutableMap()
        val noExistingFrontMatter = existingFrontMatter.isEmpty()
        for ((key, value) in newFrontMatter) {
            existingFrontMatter[key] = value
        }

        val frontMatterEntries = existingFrontMatter
            .map { (key, value) ->
                if (value.size == 1) {
                    "$key: ${value.first()}"
                } else {
                    "$key:\n${value.joinToString("\n") { "    - $it" }}"
                }
            }
            .joinToString("\n")

        val frontMatterString = "--\n$frontMatterEntries\n---"

        return if (noExistingFrontMatter) {
            "-$frontMatterString\n$mdContent"
        } else {
            // use regex to find existing front matter and replace it
            mdContent.replace(Regex("""^---\n.*\n---""", RegexOption.DOT_MATCHES_ALL), "-$frontMatterString")
        }
    }
}
