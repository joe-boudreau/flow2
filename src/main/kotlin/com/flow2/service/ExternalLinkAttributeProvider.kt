package com.flow2.service

import com.vladsch.flexmark.ast.AutoLink
import com.vladsch.flexmark.ast.Link
import com.vladsch.flexmark.ast.LinkRef
import com.vladsch.flexmark.html.AttributeProvider
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory
import com.vladsch.flexmark.html.renderer.AttributablePart
import com.vladsch.flexmark.html.renderer.LinkResolverContext
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.html.MutableAttributes
import java.net.URI

class ExternalLinkAttributeProvider() : AttributeProvider {

    override fun setAttributes(node: Node, part: AttributablePart, attributes: MutableAttributes) {
        // images are rendered with AttributablePart.LINK too, so check the node type as well
        if (part != AttributablePart.LINK) return
        if (node !is Link && node !is AutoLink && node !is LinkRef) return

        val href = attributes.getValue("href") ?: return
        if (isExternal(href)) {
            attributes.replaceValue("target", "_blank")
        }
    }

    private fun isExternal(href: String): Boolean {
        val uri = runCatching { URI(href) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        // leave mailto:, tel: etc. alone; a null scheme also covers protocol relative //host/path
        if (scheme != null && scheme != "http" && scheme != "https") return false
        // anchors (#foo) and site relative paths (/post/x, ./x) have no host
        return !uri.host.isNullOrEmpty()
    }

    class Factory() : IndependentAttributeProviderFactory() {
        override fun apply(context: LinkResolverContext): AttributeProvider =
            ExternalLinkAttributeProvider()
    }
}
