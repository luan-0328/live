package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.utils.AfterCommit;
import com.geocommunity.common.utils.CacheVersion;
import com.geocommunity.mq.OutboxService;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.dto.AuthorVO;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.CommentMapper;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.UserMapper;
import com.geocommunity.mq.MqEvent;
import com.geocommunity.service.CommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 发表评论：写评论表 → 帖子评论数 +1 → 热榜 +3 → 通知作者 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Comment addComment(Long postId, Comment comment, Long userId) {
        Post post = postMapper.selectActiveForUpdate(postId);
        if (post == null || post.getStatus() != 1) {
            throw new BusinessException(1003, "帖子不存在");
        }

        if (comment.getContent()==null || comment.getContent().isBlank() || comment.getContent().length()>500)
            throw new BusinessException(1001,"评论内容须为1～500字符");
        Long targetId=comment.getReplyToCommentId()!=null?comment.getReplyToCommentId():comment.getParentId();
        Long recipient=post.getAuthorId();
        comment.setReplyToUserId(null);
        if(targetId!=null) {
            Comment target=commentMapper.selectById(targetId);
            if(target==null || !Integer.valueOf(1).equals(target.getStatus()) || !postId.equals(target.getPostId()))
                throw new BusinessException(1003,"被回复评论不存在或不属于当前帖子");
            Long rootId=target.getParentId()==null?target.getId():target.getParentId();
            // 根评论删除后保留已有楼层，但不能继续新增回复。
            if(target.getParentId()!=null) {
                Comment root=commentMapper.selectById(rootId);
                if(root==null || !Integer.valueOf(1).equals(root.getStatus()) || !postId.equals(root.getPostId()) || root.getParentId()!=null)
                    throw new BusinessException(1003,"所属评论楼层已删除或无效");
            }
            if(comment.getParentId()!=null && !rootId.equals(comment.getParentId()))
                throw new BusinessException(1001,"回复楼层不匹配");
            comment.setParentId(rootId); comment.setReplyToCommentId(targetId);
            comment.setReplyToUserId(target.getAuthorId()); recipient=target.getAuthorId();
        } else { comment.setParentId(null); comment.setReplyToCommentId(null); }
        comment.setId(null);
        comment.setPostId(postId);
        comment.setAuthorId(userId);
        comment.setStatus(1);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1"));

        // 清理帖子详情缓存
        invalidatePost(postId);

        AfterCommit.run(() -> redisTemplate.opsForZSet().incrementScore(RedisKeys.POST_HOT_BOARD, String.valueOf(postId), 3));
        outboxService.enqueue(new MqEvent("COMMENT", postId, userId, recipient, comment.getContent()));

        return comment;
    }

    /** 删除评论（仅作者可操作）：删评论 → 帖子评论数 -1 → 热榜 -3 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() != 1) {
            throw new BusinessException(1003, "评论不存在");
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(1008, "无操作权限");
        }

        if (!removeComment(commentId)) throw new BusinessException(1006,"评论已删除");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeComment(Long commentId) {
        Comment comment=commentMapper.selectById(commentId);
        if(comment==null || !Integer.valueOf(1).equals(comment.getStatus())) return false;
        // 先锁帖子，与删帖及其他评论写操作按同一顺序串行；受影响行数防重复删除。
        if (postMapper.selectActiveForUpdate(comment.getPostId()) == null
                || commentMapper.deleteById(commentId) != 1) return false;

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(CAST(comment_count AS SIGNED) - 1, 0)"));

        invalidatePost(comment.getPostId());
        AfterCommit.run(() -> redisTemplate.opsForZSet().incrementScore(RedisKeys.POST_HOT_BOARD, String.valueOf(comment.getPostId()), -3));
        return true;
    }

    private void invalidatePost(Long postId) {
        AfterCommit.run(() -> {
            CacheVersion.invalidate(redisTemplate, RedisKeys.POST_DETAIL + postId, RedisKeys.POST_DETAIL + postId);
            CacheVersion.invalidate(redisTemplate, RedisKeys.POST_LIST, "post:lists");
        });
    }

    /** 评论列表（按时间正序，带作者信息，树形结构） */
    @Override
    public Page<Comment> listComments(Long postId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        Page<Comment> pageParam = new Page<>(page, size);

        // 只查根评论（parent_id IS NULL），子评论在后面批量加载
        Page<Comment> result = commentMapper.selectRoots(pageParam, postId);

        List<Comment> roots = result.getRecords();
        if (!roots.isEmpty()) {
            // 收集所有根评论 ID
            List<Long> rootIds = roots.stream().map(Comment::getId).collect(Collectors.toList());

            // 批量查子评论
            List<Comment> children = commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>()
                            .eq(Comment::getPostId, postId)
                            .eq(Comment::getStatus, 1)
                            .in(Comment::getParentId, rootIds)
                            .orderByAsc(Comment::getCreatedAt).orderByAsc(Comment::getId));

            // 合并所有评论（根+子），统一加载作者信息
            List<Comment> allRecords = new ArrayList<>(roots);
            allRecords.addAll(children);
            loadAuthors(allRecords);
            for(Comment root:roots) if(!Integer.valueOf(1).equals(root.getStatus())) {
                root.setContent("该评论已删除"); root.setAuthor(null); root.setAuthorId(null);
            }

            // 子评论按 parent_id 分组
            Map<Long, List<Comment>> childrenMap = children.stream()
                    .collect(Collectors.groupingBy(Comment::getParentId));

            // 挂载子评论到根评论
            for (Comment root : roots) {
                root.setChildren(childrenMap.getOrDefault(root.getId(), Collections.emptyList()));
            }
        }

        return result;
    }

    private void loadAuthors(List<Comment> comments) {
        List<Long> authorIds = comments.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getAuthorId(),c.getReplyToUserId())).filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (authorIds.isEmpty()) return;
        List<User> users = userMapper.selectBatchIds(authorIds);
        if (users == null) return;
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        for (Comment c : comments) {
            User replied=userMap.get(c.getReplyToUserId());
            if(c.getReplyToUserId()!=null) {
                AuthorVO info=new AuthorVO(); info.setUserId(c.getReplyToUserId());
                info.setNickname(replied==null?"已注销用户":replied.getNickname());
                if(replied!=null) info.setAvatar(replied.getAvatar()); c.setReplyToAuthor(info);
            }
            User u = userMap.get(c.getAuthorId());
            if (u != null) {
                AuthorVO author = new AuthorVO();
                author.setUserId(u.getId());
                author.setNickname(u.getNickname());
                author.setAvatar(u.getAvatar());
                c.setAuthor(author);
            }
        }
    }
}
//查子评论和跟评论一共查了两次库，第一次查所有根，第二次查根的所有子，然后拼接起来插入根的children属性
