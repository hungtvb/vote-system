package com.hungtvb.votesystem.post;

import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.post.dto.CreatePostRequest;
import com.hungtvb.votesystem.post.dto.PostResponse;
import com.hungtvb.votesystem.vote.Vote;
import com.hungtvb.votesystem.vote.VoteRepository;
import com.hungtvb.votesystem.vote.VoteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final VoteRepository voteRepository;

    public PostService(PostRepository postRepository, VoteRepository voteRepository) {
        this.postRepository = postRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public PostResponse create(UUID authorId, CreatePostRequest request) {
        Post post = Post.create(authorId, request.title().trim(), request.content().trim());
        return PostResponse.from(postRepository.save(post));
    }

    @Transactional(readOnly = true)
    public PostResponse get(UUID postId, UUID userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        VoteType myVote = userId == null ? null : voteRepository.findByUserIdAndPostId(userId, postId)
                .map(Vote::getType)
                .orElse(null);
        return PostResponse.from(post, myVote);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> list(Pageable pageable, UUID userId) {
        Page<Post> posts = postRepository.findAllByOrderByCreatedAtDesc(pageable);
        if (userId == null || posts.isEmpty()) {
            return posts.map(PostResponse::from);
        }

        Map<UUID, VoteType> votesByPostId = voteRepository.findByUserIdAndPostIdIn(
                        userId,
                        posts.getContent().stream().map(Post::getId).toList())
                .stream()
                .collect(Collectors.toMap(Vote::getPostId, Vote::getType, (first, ignored) -> first));

        return posts.map(post -> PostResponse.from(post, votesByPostId.get(post.getId())));
    }
}
