package com.flow2.repository

import com.flow2.model.Post

class ArrayPostRepository:  PostRepositoryInterface {

    private val posts = mutableListOf<Post>()

    override suspend fun getPostBySlug(slug: String): Post? {
        return Post(
            "id123",
            "my cool's Post",
            "this is the content of my cool post",
            "/assets/images/banner_image_ex.jpg"
        )
    }

    override fun createPost(title: String, content: String, bannerImage: String): Post {

        return
    }
}