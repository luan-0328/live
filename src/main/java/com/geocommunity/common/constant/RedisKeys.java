package com.geocommunity.common.constant;

/**
 * Redis key 常量统一管理。
 * <p>
 * 所有涉及 Redis key 的地方都应该引用这里的常量，避免字符串散落各处。
 */
public interface RedisKeys {

    // ==================== 帖子缓存 ====================
    String POST_LIST = "post:list:";          // + categoryId:sort:page:size
    String POST_SEARCH = "post:search:";      // + keyword:categoryId:page:size
    String POST_DETAIL = "post:detail:";      // + postId
    String POST_NULL = "post:null:";          // + postId（穿透保护）
    String POST_LIKED = "post:liked:";        // + postId (Set)
    String POST_FAVORITES = "post:favorites:"; // + postId（收藏该帖的用户集合，删帖时反向清理用）
    String POST_VIEW = "post:view:";          // + postId（浏览计数）
    String POST_HOT_BOARD = "post:hot:24h";   // ZSET（热榜）

    // ==================== 用户 ====================
    String USER_CACHE = "user:";              // + userId
    String USER_NULL = "user:null:";          // + userId（穿透保护）
    String LOCK_USER = "lock:user:";          // + userId（击穿互斥锁）
    String FAVORITES_USER = "favorites:user:"; // + userId (Set)
    String LOCK_DETAIL = "lock:detail:";      // + postId（击穿互斥锁）

    // ==================== 认证 ====================
    String SESSION_IDLE = "session:idle:";    // + token（会话续期）
    String SESSION_USER = "session:user:";    // + userId（该用户的活跃 token 集合，封禁时强制下线用）
    String SMS_CODE = "sms:";                 // + phone（验证码）
    String LOGIN_FAIL = "login:fail:";        // + user:手机号 / admin:账号（连续密码错误计数，TTL 自动解锁）
    String SMS_COOLDOWN = "sms:cd:";          // + phone（发送冷却，防连点）
    String SMS_DAILY = "sms:daily:";          // + phone（单手机号每日发送配额）
    String SMS_IP = "sms:ip:";                // + ip（单 IP 每小时发送配额，防轮换手机号刷短信费）
    String SMS_VERIFY_FAIL = "sms:vfail:";    // + phone（验证码校验失败计数，防枚举）

}
