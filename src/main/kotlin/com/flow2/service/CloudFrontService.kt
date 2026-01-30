package com.flow2.service

import aws.sdk.kotlin.services.cloudfront.CloudFrontClient
import aws.sdk.kotlin.services.cloudfront.model.CreateInvalidationRequest
import aws.sdk.kotlin.services.cloudfront.model.InvalidationBatch
import aws.sdk.kotlin.services.cloudfront.model.Paths
import com.flow2.model.Post
import io.ktor.util.logging.KtorSimpleLogger

class CloudFrontService(
    private val distributionId: String?,
    private val enabled: Boolean = true
) {

    private val log = KtorSimpleLogger(CloudFrontService::class.simpleName!!)

    /**
     * Invalidates cache for a specific post by its slug
     */
    suspend fun invalidatePost(post: Post) {
        val paths = listOf(
            "/${post.slug}",
            "/${post.slug}/",
            "/post/${post.slug}",
            "/post/${post.slug}/"
        )
        invalidatePaths(paths)
    }

    /**
     * Invalidates cache for the home page and listing pages that might show the post
     */
    suspend fun invalidateListingPages() {
        val paths = listOf(
            "/",
            "/feed.xml",
            "/rss",
            "/rss/",
            "/post",
            "/post/"
        )
        invalidatePaths(paths)
    }

    /**
     * Invalidates cache for a post and all listing pages
     * Use this when a post is created, updated, or deleted
     */
    suspend fun invalidateForPostChange(post: Post) {
        val postPaths = listOf(
            "/${post.slug}",
            "/${post.slug}/",
            "/post/${post.slug}",
            "/post/${post.slug}/"
        )
        val listingPaths = listOf(
            "/",
            "/feed.xml",
            "/rss",
            "/rss/",
            "/post",
            "/post/"
        )
        // Also invalidate category and tag pages
        val categoryPaths = listOf(
            "/category/${post.category.name.lowercase()}",
            "/category/${post.category.name.lowercase()}/"
        )
        val tagPaths = post.tags.flatMap { tag ->
            listOf(
                "/tag/${tag.trim().lowercase()}",
                "/tag/${tag.trim().lowercase()}/"
            )
        }

        invalidatePaths(postPaths + listingPaths + categoryPaths + tagPaths)
    }

    /**
     * Invalidates all cached content.
     * Use sparingly - while this counts as only 1 invalidation request, it forces CloudFront
     * to re-fetch ALL content from the origin, increasing origin load and causing temporary
     * cache misses at edge locations.
     */
    suspend fun invalidateAll() {
        invalidatePaths(listOf("/*"))
    }

    /**
     * Invalidates specific paths in CloudFront
     */
    suspend fun invalidatePaths(paths: List<String>) {
        if (!enabled) {
            log.info("CloudFront invalidation is disabled, skipping invalidation for paths: $paths")
            return
        }

        if (distributionId.isNullOrBlank()) {
            log.warn("CloudFront distribution ID is not configured, skipping invalidation for paths: $paths")
            return
        }

        if (paths.isEmpty()) {
            log.debug("No paths to invalidate")
            return
        }

        try {
            val client = CloudFrontClient { region = "us-east-1" }
            try {
                val callerReference = "flow2-${System.currentTimeMillis()}"

                val invalidationBatch = InvalidationBatch {
                    this.callerReference = callerReference
                    this.paths = Paths {
                        this.quantity = paths.size
                        this.items = paths
                    }
                }

                val request = CreateInvalidationRequest {
                    this.distributionId = this@CloudFrontService.distributionId
                    this.invalidationBatch = invalidationBatch
                }

                val response = client.createInvalidation(request)
                log.info("CloudFront invalidation created: ${response.invalidation?.id} for paths: $paths")
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            log.error("Failed to create CloudFront invalidation for paths $paths: ${e.message}", e)
        }
    }
}
