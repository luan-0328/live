# 项目审查报告（2026-10-03）

> 后续已按用户要求修复：保留模拟验证码和二级回复，其余主要问题已修改。本文记录修改前的审查发现；当前结果、验证范围及必要升级步骤见[修复说明](FIXES_2026-10-03.md)。

结论：工程结构基本齐全，前后端均能构建，现有单元测试通过；但存在鉴权、并发计数、事务缓存一致性和功能接线问题，当前不能认定为完整、可直接上线的系统。

本次只审查，未修改业务源码或数据库。P1 表示应优先修复的鉴权、数据正确性或可靠性问题；P2 表示功能、隐私和边界问题。

## 验证范围及结果

| 验证 | 结果 |
|---|---|
| `mvn test` | 63 项通过，0 失败、0 错误 |
| `mvn clean verify` | 83 个 Java 源文件重新编译、63 项测试通过、Spring Boot 可执行 JAR 打包成功 |
| `npm run build`（frontend） | 成功；主 JS 包约 1.10 MB，gzip 约 366 KB，有分包警告 |
| 隔离回归探针 | 复现以下第 1、2、3 项；调用实际项目类，使用 Mockito 和 Spring mock request/response，不连接真实服务 |
| 数据库、中间件、HTTP 集成及 Docker | 未验证；本机 3306、6379、5672、8080 未发现监听，Docker 命令不可用 |

后端构建产物位于 [geo-community-1.0.0.jar](/D:/post/live/target/geo-community-1.0.0.jar)。探针源码位于 [AuditProbe.java](/D:/post/live/target/audit/AuditProbe.java)，属于临时验证文件，下次 Maven clean 会清除。

现有 `GeoCommunityApplicationTests` 只检查 Result 对象，并非 Spring Boot 上下文启动测试；其余测试主要 Mock Mapper、Redis 和 MQ。测试通过不代表 SQL、真实事务、拦截器链和消息路由都正确。README 声称的历史端到端验收与压测数据，本次未重新验证。

## 优先修复的问题

### 1. P1：编辑帖子允许修改作者和系统计数（探针已复现）

位置：[PostController.java:107](/D:/post/live/src/main/java/com/geocommunity/controller/PostController.java:107)、[PostServiceImpl.java:293](/D:/post/live/src/main/java/com/geocommunity/service/impl/PostServiceImpl.java:293)。

接口直接接收 Post 实体。服务只检查原帖作者是否为当前用户，随后把请求实体交给 `updateById`。自己的帖子可以通过请求体修改 `authorId`、`likeCount`、`commentCount`、`viewCount`、`createdAt` 等字段。探针确认这些字段未经剔除便进入 Mapper。创建帖子虽然覆盖作者和状态，也没有清除用户传入的系统计数。

建议：使用独立的创建/编辑 DTO，明确白名单字段；服务构造更新对象，只更新标题、内容、分类、图片和位置。创建时统一初始化计数。补充标题、内容、图片数量及坐标的后端校验。

### 2. P1：注销或强制下线后，旧 JWT 仍能通过私人 GET 鉴权（探针已复现）

位置：[RefreshInterceptor.java:39](/D:/post/live/src/main/java/com/geocommunity/common/auth/RefreshInterceptor.java:39)、[AuthInterceptor.java:23](/D:/post/live/src/main/java/com/geocommunity/common/auth/AuthInterceptor.java:23)。

RefreshInterceptor 在验证会话存在之前就设置 UserContext；会话已删除时仍保留用户身份。AuthInterceptor 对所有 GET 直接放行。因此旧 JWT 可继续用于 `/user/me`、`/user/favorites`、`/notification/list`、`/report/my` 等私人查询，直到 JWT 自身过期。后台另有 AdminOnlyAspect 校验，不能把该结论扩展为已证实的后台绕过。

建议：先验证会话，再设置当前用户；公开 GET 使用明确的路径白名单，私人 GET 与写接口统一鉴权。公开页面可允许匿名访问，但无效会话不能携带已登录身份。Redis 故障时直接降级为 JWT 的策略，也应重新评估其对封禁和登出的影响。

### 3. P1：自动续期会使有效请求被拒绝，登出还可能留下新会话（探针已复现拒绝请求）

位置：[RefreshInterceptor.java:57](/D:/post/live/src/main/java/com/geocommunity/common/auth/RefreshInterceptor.java:57)、[AuthInterceptor.java:35](/D:/post/live/src/main/java/com/geocommunity/common/auth/AuthInterceptor.java:35)、[AuthController.java:220](/D:/post/live/src/main/java/com/geocommunity/controller/AuthController.java:220)。

JWT 剩余不足一天时，刷新拦截器删除旧 session、创建新 session，却不改变原请求的 Authorization。后续写接口鉴权、后台切面及 `/auth/check` 仍检查旧 token，于是返回 401。探针复现了响应同时含新 token 和 HTTP 401；前端 HTTP 错误分支会清空登录态。

同样的顺序作用于 `/auth/logout`：先刷新创建新 session，登出方法只删除请求中的旧 token，新会话可能仍有效。跨域直连时，CORS 还未暴露 `X-Auth-Token` 响应头。

建议：本次请求基于已验证身份通过，续期在合适阶段执行；轮换策略处理并发请求，避免过早删除旧 session；登出禁止自动轮换并清除应撤销的会话。跨域场景暴露刷新响应头。

### 4. P1：验证码仍是固定模拟值，手机号所有权没有真实验证

位置：[AuthController.java:84](/D:/post/live/src/main/java/com/geocommunity/controller/AuthController.java:84)、[UserServiceImpl.java:238](/D:/post/live/src/main/java/com/geocommunity/service/impl/UserServiceImpl.java:238)。

发送验证码只是将固定值 `123456` 写入 Redis，没有短信供应商调用。注册和换绑手机号都依赖这个值，无法证明号码属于操作者。换绑接口也没有注册接口同等的校验失败限流，且 Controller 未启用 DTO 校验。

建议：模拟验证码只允许在显式开发配置下使用，生产启用随机验证码和真实短信服务；按注册/换绑用途隔离验证码，原子消费并统一试错限制。

### 5. P1：取消操作与删除操作并发时会重复扣减计数

位置：[PostServiceImpl.java:423](/D:/post/live/src/main/java/com/geocommunity/service/impl/PostServiceImpl.java:423)、[UserServiceImpl.java:426](/D:/post/live/src/main/java/com/geocommunity/service/impl/UserServiceImpl.java:426)、[CommentServiceImpl.java:105](/D:/post/live/src/main/java/com/geocommunity/service/impl/CommentServiceImpl.java:105)。

取消点赞、取消关注、删除评论和作者删帖均先查询，再删除，然后无条件扣减计数。两个事务可同时查到相同关系/内容；后来的删除实际影响 0 行，代码仍扣减计数。事务注解和原子自减不能防止这类重复操作。

结果可能是计数少于真实数量；schema 中计数是 UNSIGNED，减到零以下会产生数据库下溢错误，而不是合法保存负数。取消收藏也会重复减热榜分数。

建议：以条件删除或状态转换的受影响行数作为唯一依据，只有成功改变状态的请求才能调整计数及热榜。必要时对竞争对象加锁。增加真实数据库的并发取消、删除与举报竞争测试。

### 6. P1：事务内删缓存、改 Redis 和发 MQ，可能污染已提交数据的视图

位置：[PostServiceImpl.java:263](/D:/post/live/src/main/java/com/geocommunity/service/impl/PostServiceImpl.java:263)、[CommentServiceImpl.java:54](/D:/post/live/src/main/java/com/geocommunity/service/impl/CommentServiceImpl.java:54)、[UserServiceImpl.java:371](/D:/post/live/src/main/java/com/geocommunity/service/impl/UserServiceImpl.java:371)。

多个事务方法在数据库提交前就删除缓存。另一读请求可在此期间查到旧的已提交数据并写回缓存，提交后没有再次失效，旧详情可能保留 30～40 分钟。Redis 集合和热榜更新也不随数据库回滚；通知消费者可能在事务提交前收到事件，或收到最终回滚的业务事件。

建议：将缓存失效和非关键 Redis 操作移至事务提交后的回调。需要可靠通知时使用事务 Outbox、投递确认和重试补偿；只使用提交后回调仍不能保证数据库与消息系统原子提交。缓存的并发回填问题还需按一致性要求设计版本或补偿机制。

### 7. P1：死信路由也匹配原业务队列，失败消息可能再次进入业务消费

位置：[RabbitConfig.java:29](/D:/post/live/src/main/java/com/geocommunity/config/RabbitConfig.java:29)、[RabbitConfig.java:39](/D:/post/live/src/main/java/com/geocommunity/config/RabbitConfig.java:39)。

业务队列绑定 `notification.#`，死信使用同一交换机和 `notification.dlq` 路由键。后者同时匹配业务队列和死信队列。根据路由配置推导，死信将被再次送入业务队列；持续消费失败时可能反复重试，不能实现代码注释描述的单次隔离。真实 broker 行为本次未运行验证。

建议：业务绑定改为当前生产者使用的精确 `notification`，或建立独立死信交换机。规则依据：[RabbitMQ Topic 路由](https://www.rabbitmq.com/tutorials/tutorial-five-java)、[死信路由说明](https://www.rabbitmq.com/docs/dlx)。

## 功能和数据问题

### 8. P2：分页只限制上限，负数 size 可绕过分页

位置：[PostServiceImpl.java:107](/D:/post/live/src/main/java/com/geocommunity/service/impl/PostServiceImpl.java:107)、[MyBatisPlusConfig.java:18](/D:/post/live/src/main/java/com/geocommunity/config/MyBatisPlusConfig.java:18)，其他分页服务也存在同样写法。

`Math.min(size, 100)` 会保留负数；分页插件未配置 maxLimit。`size=-1` 可触发不分页查询，公开列表可能一次返回全量帖子，放大数据库、序列化与缓存负载。插件的负数禁用分页语义见[官方说明](https://baomidou.com/en/plugins/pagination/)。

建议：将 size 约束在 1～100，或拒绝非法值；全局设置 maxLimit。对 keyword、sort、经纬度和 radius 同时增加边界校验。

### 9. P2：前端“回复”实际创建根评论；后端缺少父评论校验

位置：[PostDetail.vue:237](/D:/post/live/frontend/src/views/PostDetail.vue:237)、[PostDetail.vue:247](/D:/post/live/frontend/src/views/PostDetail.vue:247)、[CommentServiceImpl.java:55](/D:/post/live/src/main/java/com/geocommunity/service/impl/CommentServiceImpl.java:55)。

前端回复只填入文本前缀，提交体只有 content，没有 parentId，因此并未实现用户可用的二级回复。后端接受 parentId，但不验证父评论存在、同属一帖、仍可见及层级。直接请求能创建不可显示的回复或跨帖关联；列表只加载根及其直接孩子，删除根评论后孩子也无法被展示，计数却可能保留。

建议：前端保存并提交回复目标；服务验证父评论，并将二级回复统一关联根评论。明确根删除时保留占位还是级联删除，计数采用同一口径。

### 10. P2：点赞/收藏状态只读 Redis，丢失缓存后与数据库不一致

位置：[PostController.java:85](/D:/post/live/src/main/java/com/geocommunity/controller/PostController.java:85)、[PostServiceImpl.java:386](/D:/post/live/src/main/java/com/geocommunity/service/impl/PostServiceImpl.java:386)。

详情通过 Redis Set 判断当前用户是否点赞/收藏，没有 DB 回源。Redis 重启、清理或写入失败后，数据库仍有关系，页面却显示未操作，再次操作又被唯一索引拒绝。Compose 的 Redis 服务没有配置数据卷。

建议：数据库为关系真值，缓存缺失可回源并重建，或直接用批量关系查询。持久化只能减少丢失，不能替代回源与一致性处理。

### 11. P2：管理员/举报删帖路径没有统一维护作者计数和关联数据

位置：[AdminController.java:148](/D:/post/live/src/main/java/com/geocommunity/controller/AdminController.java:148)、[ReportServiceImpl.java:131](/D:/post/live/src/main/java/com/geocommunity/service/impl/ReportServiceImpl.java:131)。

作者自行删帖会递减 post_count，管理员强删没有递减。举报成立仅将帖子状态改为 -1、删除详情和列表缓存，没有执行与强删相同的关系、热榜清理，也没有递减作者 post_count。不同入口导致个人发帖数和相关关系口径不同。举报处理还采用“先读待处理、后无条件更新”，同一举报并发处理缺少状态竞争控制。

建议：抽取统一的内容处置服务，以状态成功转换为前提维护计数、关系和缓存；举报处理用条件更新或锁确保只处理一次。

### 12. P2：公开社交列表返回完整手机号，后台用户列表返回密码哈希

位置：[UserServiceImpl.java:455](/D:/post/live/src/main/java/com/geocommunity/service/impl/UserServiceImpl.java:455)、[UserServiceImpl.java:477](/D:/post/live/src/main/java/com/geocommunity/service/impl/UserServiceImpl.java:477)、[AdminController.java:98](/D:/post/live/src/main/java/com/geocommunity/controller/AdminController.java:98)。

关注/粉丝列表只清除 password，没有脱敏 phone，当前用户可获得关联用户完整号码；后台列表直接返回 User，包含 password 哈希。即使前端不展示，网络响应仍含这些字段。

建议：公共用户 DTO 仅返回必要公开字段；后台返回专用 DTO，禁止序列化密码哈希。

### 13. P2：通知唯一索引没有正确表达事件幂等

位置：[schema.sql:157](/D:/post/live/sql/schema.sql:157)、[MqConsumer.java:144](/D:/post/live/src/main/java/com/geocommunity/mq/MqConsumer.java:144)。

唯一键是 `(user_id, from_user_id, type, post_id)`。同一用户在同一帖发布第二条不同评论，也会撞上相同唯一键并被忽略，后续评论通知丢失。关注事件 post_id 为 NULL，MySQL 唯一索引允许多个 NULL，重复投递的关注通知又无法去重。规则见[MySQL UNIQUE 索引说明](https://dev.mysql.com/doc/refman/8.0/en/create-index.html)。

建议：为业务事件生成稳定 eventId，消费者以事件 ID 和接收用户去重。不同评论使用不同事件 ID，同一事件重投仍保持相同 ID。当前批量通知 SQL 已含 ON DUPLICATE KEY UPDATE，本次未发现“一条重复导致整批丢弃”的问题。

### 14. P2：热榜衰减以读后覆盖写实现，会丢失并发互动分数

位置：[HotBoardDecayTask.java:42](/D:/post/live/src/main/java/com/geocommunity/task/HotBoardDecayTask.java:42)、[HotBoardDecayTask.java:69](/D:/post/live/src/main/java/com/geocommunity/task/HotBoardDecayTask.java:69)。

任务先取整榜快照，再写绝对分数。快照读取后发生的 ZINCRBY 会被旧快照覆盖；移除低分项也可能移除刚获得互动的帖子。多个应用实例还会各自定时衰减，放大衰减频率。

建议：以 Lua 等原子操作处理当前分值，配合单实例调度或分布式锁；大榜分批处理，避免长时间阻塞 Redis。

## 完整性及部署注意事项

- 前后端源码、10 张表 DDL、Mapper XML、Docker、Nginx、配置示例和测试基本齐全，frontend/package-lock.json 已纳入版本控制。
- 帖子编辑和附近查询只有 API 封装，未找到前端页面调用；README 已明确说明附近查询前端未接线，编辑也应明确实际交付范围。
- 后台帖子列表声称含已删除帖子，实际使用带 `@TableLogic` 的常规 selectPage，会自动排除逻辑删除数据。需专用查询实现管理视图。
- 上传服务依赖 OSS，未配置密钥时不可用；本次没有验证真实上传和图片访问。
- 生产配置允许公开开发 JWT 密钥与管理员默认密码回退；复制 .env.example 也只得到示例占位值。应在生产启动时拒绝占位值，而不是仅用注释提醒。
- 默认 Dockerfile 使用根目录 app.jar，Maven 最新产物在 target 内；交付前必须确认部署 JAR 与当前源码一致。源码部署的 Compose 覆盖文件只构建后端，仍需按文档预先生成 frontend/dist。
- SQL 升级脚本需手动执行，且两个脚本存在重叠索引，不能无条件重复执行。建议采用有版本记录的迁移工具并明确老库升级顺序。
- SCAN 清缓存、浏览计数刷库和热榜衰减都是全量任务，数据增长后需检查内存与耗时。浏览计数刷库未同步失效详情缓存，页面浏览数可能持续落后至缓存到期。
- 图片校验只依赖请求 MIME，上传目录未限制；应验证真实图片内容并限定 dir。此项属于上传输入校验完善，本次未做恶意文件上传测试。

## 建议执行顺序

1. 修复实体绑定、会话鉴权与刷新、模拟验证码生产隔离。
2. 修复并发取消/删除及事务提交后的缓存与消息处理，隔离死信路由。
3. 修复分页、二级回复、关系状态回源、删除计数、用户响应字段和通知幂等。
4. 接通约定交付的前端功能，再用独立测试库运行真实 HTTP + MySQL + Redis + RabbitMQ 的集成验收。

最低回归场景应包含：临期 token 的写请求/会话检查/登出，登出及封禁后的私人 GET，恶意编辑字段，并发取消与删除，事务回滚后的缓存及消息，死信隔离，Redis 清空后的点赞收藏，评论回复及根删除，同帖多次评论通知，生产配置校验和新库/老库部署。
