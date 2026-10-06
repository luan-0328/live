package com.geocommunity.controller;

import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.result.Result;
import com.geocommunity.dto.RankVO;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rank")
public class RankController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    /** 时间衰减热榜（从 Redis ZSET 读取，按分数降序） */
    @GetMapping("/hot")
    public Result<List<RankVO>> hot(@RequestParam(defaultValue = "20") int n) {
        n = Math.max(n, 1);
        n = Math.min(n, 100);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(RedisKeys.POST_HOT_BOARD, 0, n - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(List.of());
        }

        List<Long> postIds = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null) {
                postIds.add(Long.valueOf(tuple.getValue()));
            }
        }

        List<Post> posts = postMapper.selectBatchIds(postIds);
        var postMap = posts.stream().collect(Collectors.toMap(Post::getId, p -> p));

        // 批量查询作者昵称
        List<Long> authorIds = posts.stream()
                .map(Post::getAuthorId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> authorNameMap = authorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));

        List<RankVO> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            Double score = tuple.getScore();
            if (value == null || score == null) continue;

            Long postId = Long.valueOf(value);
            Post post = postMap.get(postId);
            if (post == null) continue; // 帖子已删，跳过

            RankVO vo = new RankVO();
            vo.setRank(rank++);
            vo.setPostId(postId);
            vo.setTitle(post.getTitle());
            vo.setScore(score);
            vo.setAuthorNickname(authorNameMap.get(post.getAuthorId()));
            list.add(vo);
        }
        return Result.ok(list);
    }
}
