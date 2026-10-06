package com.geocommunity.controller;

import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.JwtUtil;
import com.geocommunity.common.utils.PasswordUtil;
import com.geocommunity.common.utils.RateLimiter;
import com.geocommunity.dto.AdminLoginRequest;
import com.geocommunity.dto.LoginRequest;
import com.geocommunity.dto.LoginResponse;
import com.geocommunity.dto.RegisterRequest;
import com.geocommunity.entity.User;
import com.geocommunity.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /** 连续密码错误达到该次数后，暂时拒绝该账号登录 */
    private static final int LOGIN_MAX_FAIL = 3;

    /** 登录触发限制后的等待时长（秒） */
    private static final long LOGIN_LOCK_SECONDS = 120;

    /** 同一手机号发送验证码的冷却时间（秒） */
    private static final long SMS_COOLDOWN_SECONDS = 60;

    /** 同一手机号每日发送验证码上限 */
    private static final int SMS_MAX_PER_PHONE_DAILY = 10;

    /** 同一 IP 每小时发送验证码上限（防止脚本轮换手机号刷爆短信费） */
    private static final int SMS_MAX_PER_IP_HOURLY = 20;

    /** 同一手机号验证码校验失败上限（否则 6 位验证码可被枚举） */
    private static final int SMS_MAX_VERIFY_FAIL = 5;

    /** 验证码失败计数的窗口（分钟） */
    private static final long SMS_VERIFY_FAIL_WINDOW_MINUTES = 10;

    /** 验证码有效期（分钟） */
    private static final long SMS_CODE_TTL_MINUTES = 5;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private com.geocommunity.common.auth.SessionService sessionService;

    /**
     * 将 token 存入 Redis 会话（滑动续期），并登记到该用户的会话索引。
     * TTL 与 JWT 有效期对齐（默认 7 天），避免"token 未过期但会话先失效"导致活跃用户被无故踢出。
     * 会话索引用于管理员封禁时强制下线：遍历删除该用户所有活跃会话。
     */
    private void saveSession(Long userId, String token) {
        sessionService.save(userId, token, jwtUtil.getExpireMs());
    }

    /**
     * 取真实客户端 IP。应用跑在 nginx 后面，getRemoteAddr() 拿到的只是 nginx 容器 IP，
     * 必须读 nginx 用 proxy_set_header 写入的 X-Real-IP。
     * 之所以可信：app 的 8080 端口只绑定 127.0.0.1，外部无法绕过 nginx 直连来伪造该头。
     */
    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }

    /** 发送短信验证码（模拟：固定 123456） */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            return Result.fail(400, "手机号格式不正确");
        }

        // 1) 手机号冷却：先挡住连点，避免后面的配额计数被无效请求顶满
        String cooldownKey = RedisKeys.SMS_COOLDOWN + phone;
        if (!rateLimiter.tryAcquire(cooldownKey, SMS_COOLDOWN_SECONDS, TimeUnit.SECONDS)) {
            return Result.fail(429, "发送过于频繁，请 {} 秒后再试", rateLimiter.remainingSeconds(cooldownKey));
        }

        // 2) 单手机号每日配额：防同一号码被反复轰炸
        if (rateLimiter.overLimit(RedisKeys.SMS_DAILY + phone,
                SMS_MAX_PER_PHONE_DAILY, 1, TimeUnit.DAYS)) {
            return Result.fail(429, "该手机号今日验证码发送次数已达上限");
        }

        // 3) 单 IP 每小时配额：防攻击者轮换手机号消耗短信额度
        if (rateLimiter.overLimit(RedisKeys.SMS_IP + clientIp(request),
                SMS_MAX_PER_IP_HOURLY, 1, TimeUnit.HOURS)) {
            return Result.fail(429, "操作过于频繁，请稍后再试");
        }

        redisTemplate.opsForValue().set(RedisKeys.SMS_CODE + phone, "123456",
                SMS_CODE_TTL_MINUTES, TimeUnit.MINUTES);
        return Result.ok();
    }

    /** 密码登录：手机号 + 密码 → 签发 token */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        String failKey = RedisKeys.LOGIN_FAIL + "user:" + req.getPhone();

        if (rateLimiter.count(failKey) >= LOGIN_MAX_FAIL) {
            return Result.fail(429, "密码错误次数过多，请 {} 秒后再试", rateLimiter.remainingSeconds(failKey));
        }

        User user = userService.findByPhone(req.getPhone());
        // 账号不存在也计入失败：否则可以靠错误提示枚举手机号，且能绕过限流无限试探
        if (user == null || !PasswordUtil.matches(req.getPassword(), user.getPassword())) {
            rateLimiter.recordSliding(failKey, LOGIN_LOCK_SECONDS, TimeUnit.SECONDS);
            return Result.fail(1001, "手机号或密码错误");
        }
        // 密码已正确即视为本人操作，先清计数；封禁账号也照清，避免解封后仍被限流
        rateLimiter.clear(failKey);
        if (user.getStatus() != 1) {
            return Result.fail(1002, "账号已封禁");
        }

        String token = jwtUtil.createToken(user.getId(), user.getRole());
        saveSession(user.getId(), token);
        return Result.ok(new LoginResponse(token, user.getId(), user.getNickname(), user.getRole()));
    }

    /** 注册新用户（需要短信验证码） */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        String failKey = RedisKeys.SMS_VERIFY_FAIL + req.getPhone();
        if (rateLimiter.count(failKey) >= SMS_MAX_VERIFY_FAIL) {
            return Result.fail(429, "验证码错误次数过多，请重新获取验证码");
        }

        String code = redisTemplate.opsForValue().get(RedisKeys.SMS_CODE + req.getPhone());
        if (code == null || !code.equals(req.getCode())) {
            rateLimiter.recordSliding(failKey, SMS_VERIFY_FAIL_WINDOW_MINUTES, TimeUnit.MINUTES);
            return Result.fail(1001, "验证码错误或过期");
        }
        rateLimiter.clear(failKey);
        redisTemplate.delete(RedisKeys.SMS_CODE + req.getPhone());

        if (userService.isPhoneExists(req.getPhone())) {
            return Result.fail(1009, "手机号已注册");
        }

        User user = new User();
        user.setPhone(req.getPhone());
        user.setPassword(PasswordUtil.hash(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setRole("ROLE_USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userService.save(user);

        String token = jwtUtil.createToken(user.getId(), "ROLE_USER");
        saveSession(user.getId(), token);
        return Result.ok(new LoginResponse(token, user.getId(), user.getNickname(), "ROLE_USER"));
    }

    /** 管理员密码登录 */
    @PostMapping("/admin-login")
    public Result<LoginResponse> adminLogin(@Valid @RequestBody AdminLoginRequest req) {
        String failKey = RedisKeys.LOGIN_FAIL + "admin:" + req.getAccount();

        if (rateLimiter.count(failKey) >= LOGIN_MAX_FAIL) {
            return Result.fail(429, "密码错误次数过多，请 {} 秒后再试", rateLimiter.remainingSeconds(failKey));
        }

        User user = userService.findAdmin(req.getAccount(), req.getPassword());
        if (user == null) {
            rateLimiter.recordSliding(failKey, LOGIN_LOCK_SECONDS, TimeUnit.SECONDS);
            return Result.fail(1001, "管理员账号或密码错误");
        }

        rateLimiter.clear(failKey);
        String token = jwtUtil.createToken(user.getId(), "ROLE_ADMIN");
        saveSession(user.getId(), token);
        return Result.ok(new LoginResponse(token, user.getId(), user.getNickname(), "ROLE_ADMIN"));
    }

    /** 退出登录（清除 Redis 会话及会话索引） */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Long userId;
            try { userId = jwtUtil.getUserId(token); }
            catch (Exception invalidToken) { return Result.ok(); }
            sessionService.logout(userId, token);
        }
        return Result.ok();
    }

    /**
     * 校验当前会话是否有效（JWT 未过期且 Redis 会话存活）。
     * 前端启动时调用一次：本地 token 已过期/会话已失效则返回 401，
     * 用于自动登出旧账号，避免"看起来已登录、点接口才被踢"的割裂体验。
     */
    @GetMapping("/check")
    public Result<Void> checkSession(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Result.fail(401, "未登录");
        }
        String token = header.substring(7);
        if (jwtUtil.isExpired(token)) {
            return Result.fail(401, "登录已过期");
        }
        if (com.geocommunity.common.utils.UserContext.get() == null) {
            return Result.fail(401, "登录已过期");
        }
        return Result.ok();
    }
}
