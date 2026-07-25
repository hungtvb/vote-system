ALTER TABLE posts
    ADD COLUMN up_votes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN down_votes BIGINT NOT NULL DEFAULT 0;

UPDATE posts post
SET up_votes = aggregate.up_votes,
    down_votes = aggregate.down_votes,
    vote_score = aggregate.up_votes - aggregate.down_votes
FROM (
    SELECT post_id,
           COUNT(*) FILTER (WHERE vote_type = 'UP') AS up_votes,
           COUNT(*) FILTER (WHERE vote_type = 'DOWN') AS down_votes
    FROM votes
    GROUP BY post_id
) aggregate
WHERE post.id = aggregate.post_id;

ALTER TABLE posts
    ADD CONSTRAINT ck_posts_up_votes_non_negative CHECK (up_votes >= 0),
    ADD CONSTRAINT ck_posts_down_votes_non_negative CHECK (down_votes >= 0),
    ADD CONSTRAINT ck_posts_vote_score_consistent CHECK (vote_score = up_votes - down_votes);
