package com.flow2.service

import com.flow2.repository.PostRepositoryInterface

class PostService(
    private val postRepository: PostRepositoryInterface,
    private val mdService: MarkdowwnService
) {

}