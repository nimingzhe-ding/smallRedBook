# 小红书项目面试版说明文档

## 1. 项目一句话介绍

这是一个基于 `Spring Boot + MyBatis-Plus + Redis + MySQL` 的本地生活服务项目，核心业务包括手机号验证码登录、店铺查询、优惠券秒杀、探店博客、关注推送和签到统计。我在原有后端框架尽量不变的前提下，又新增了一个基于 `Spring AI Alibaba` 的 AI 模块，实现了智能客服和智能查询问答。

如果面试官只给你 20 秒，你可以这样说：

> 这是一个偏高并发和 Redis 场景的小红书类项目，原项目重点在缓存、登录态、秒杀、Feed 流和 GEO 检索；我在这个基础上又接入了 Spring AI Alibaba，用工具调用把店铺、优惠券、订单这些真实业务能力暴露给大模型，做了智能客服和智能查询问答。

## 2. 项目定位和业务范围

这个项目可以理解为“简化版的大众小红书/本地生活服务后端”。

核心业务能力包括：

1. 用户模块
   手机号验证码登录、用户信息查询、登出、签到、签到统计。
2. 店铺模块
   店铺详情查询、按名称搜索、按分类分页查询、按经纬度做附近店铺查询。
3. 优惠券模块
   查询店铺优惠券、创建秒杀券、秒杀下单。
4. 博客模块
   发布探店笔记、查询热门博客、点赞、查询点赞榜。
5. 社交模块
   用户关注/取关、共同关注、关注推送 Feed 流。
6. AI 模块
   智能客服、智能查询问答、工具调用、知识库匹配、会话记忆。

## 3. 技术栈

从 `pom.xml` 看，这个项目当前的技术栈如下：

1. `Spring Boot 3.5.0`
2. `Spring MVC`
3. `MyBatis-Plus 3.5.7`
4. `MySQL`
5. `Redis`
6. `Redisson`
7. `Hutool`
8. `Spring AI Alibaba 1.1.2.1`
9. `DashScope / 通义千问`

关键代码位置：

1. 应用启动类：`src/main/java/com/xhs/XiaohongshuApplication.java`
2. Maven 配置：`pom.xml`
3. 运行配置：`src/main/resources/application.yaml`

## 4. 项目整体分层

整体还是比较标准的单体后端分层：

1. `controller`
   提供 HTTP 接口，对外暴露 REST API。
2. `service`
   业务接口层。
3. `service.impl`
   核心业务实现层。
4. `mapper`
   MyBatis-Plus / XML 查询层。
5. `entity`
   数据库实体映射。
6. `dto`
   前后端交互对象和统一返回结构。
7. `config`
   MVC、异常、MyBatis、Redisson 等配置。
8. `utils`
   Redis 常量、分布式锁、缓存工具、拦截器、ThreadLocal 用户上下文等。
9. `ai`
   新增的 AI 独立模块。

如果面试官问“你有没有大改原项目结构”，你的答案应该是：

> 没有大改。我保留了原有单体分层结构，只在原系统上新增了一个 `com.xhs.ai` 模块，并在 `controller` 层增加了一个 `AiAssistantController`。原业务 service 和数据库表结构没有被重构。

## 5. 请求处理主链路

普通业务请求大致是这样流转的：

1. 请求进入 `Controller`
2. `RefreshTokenInterceptor` 先尝试从请求头 `authorization` 读取 token
3. 如果 Redis 中存在登录态，就刷新 TTL 并写入 `UserHolder`
4. `LoginInterceptor` 判断当前接口是否需要登录
5. 进入具体 `ServiceImpl`
6. 业务层访问 MySQL / Redis
7. 返回统一 `Result`

关键代码：

1. 拦截器注册：`src/main/java/com/xhs/config/MvcConfig.java`
2. token 刷新：`src/main/java/com/xhs/utils/RefreshTokenInterceptor.java`
3. 登录拦截：`src/main/java/com/xhs/utils/LoginInterceptor.java`
4. 用户上下文：`src/main/java/com/xhs/utils/UserHolder.java`
5. 统一异常：`src/main/java/com/xhs/config/WebExceptionAdvice.java`

## 6. 典型业务模块拆解

### 6.1 用户登录模块

用户登录不是用 Session，而是“验证码 + Redis Token”。

处理流程：

1. 用户调用 `/user/code`
2. 后端校验手机号格式
3. 生成 6 位验证码
4. 把验证码写入 Redis，设置过期时间
5. 用户调用 `/user/login`
6. 校验验证码
7. 如果用户不存在，就自动创建用户
8. 生成 token
9. 把 `UserDTO` 写入 Redis Hash
10. 前端后续通过 `authorization` 传 token

核心代码：

1. 控制层：`src/main/java/com/xhs/controller/UserController.java`
2. 业务层：`src/main/java/com/xhs/service/impl/UserServiceImpl.java`

面试亮点可以这样说：

> 我这里没有把用户登录态存到服务端 Session，而是把精简后的 `UserDTO` 放进 Redis Hash，前端只保存 token。这样登录态更轻，也便于后面做多实例部署。

### 6.2 店铺缓存模块

店铺查询是这个项目里典型的 Redis 缓存场景。

核心能力：

1. 查询店铺详情
2. 解决缓存穿透
3. 解决缓存击穿
4. 预留逻辑过期方案

在 `ShopServiceImpl` 里你能看到三种方案：

1. `queryWithPassThrough`
   透传方案，解决缓存穿透。
2. `queryWithMutex`
   互斥锁方案，解决缓存击穿。
3. `queryWithLogicalExpire`
   逻辑过期方案，适合热点数据。

当前默认走的是 `queryWithMutex`。

关键代码：

1. `src/main/java/com/xhs/service/impl/ShopServiceImpl.java`
2. `src/main/java/com/xhs/utils/CacheClient.java`
3. `src/main/java/com/xhs/utils/RedisConstants.java`
4. `src/main/java/com/xhs/controller/ShopController.java`

面试说法：

> 我在店铺查询里重点处理了 Redis 缓存问题。缓存穿透用空值缓存，缓存击穿用互斥锁，另外还实现了逻辑过期工具方法，方便后续扩展热点数据异步重建。

### 6.3 店铺类型缓存

店铺分类是典型的低频变更、高频读取数据，适合直接缓存整个列表。

处理逻辑：

1. 先查 Redis
2. 没命中再查数据库
3. 再整体写回 Redis

关键代码：

1. `src/main/java/com/xhs/service/impl/ShopTypeServiceImpl.java`

### 6.4 GEO 附近店铺查询

项目里实现了一个比较有代表性的 Redis GEO 场景。

处理逻辑：

1. 店铺经纬度预写入 Redis GEO
2. 按分类作为 key 前缀分桶
3. 查询时用经纬度做半径搜索
4. 再按分页区间截取结果
5. 根据结果中的店铺 ID 回表查询详情
6. 按查询顺序还原排序并补充距离

核心代码：

1. `src/main/java/com/xhs/service/impl/ShopServiceImpl.java`
2. 测试写 GEO：`src/test/java/com/xhs/XiaohongshuApplicationTests.java`

面试亮点：

> GEO 查询不是直接从 MySQL 做地理位置排序，而是把地理坐标写入 Redis GEO，先做半径过滤和距离排序，再按 ID 回表，这样更适合高频“附近店铺”场景。

### 6.5 秒杀下单模块

这个模块是项目里最适合拿出来讲高并发的部分。

当前实现用了几层手段：

1. Lua 脚本原子校验
   校验库存是否足够、是否重复下单。
2. 订单异步化
   把订单请求先写入阻塞队列。
3. 单线程消费下单
   后台线程串行处理订单。
4. 分布式锁兜底
   用 Redisson 的 `RLock` 控制同一用户并发重复提交。
5. 事务创建订单
   最终在 `createVoucherOrder()` 中扣减库存并落库。

关键代码：

1. `src/main/java/com/xhs/service/impl/VoucherOrderServiceImpl.java`
2. `src/main/java/com/xhs/service/impl/VoucherServiceImpl.java`
3. `src/main/resources/seckill.lua`
4. `src/main/java/com/xhs/config/RedissonConfig.java`

如果面试官问“为什么要 Lua + 队列 + 锁一起上”，你可以这样答：

> Lua 解决的是 Redis 层面的原子性校验，阻塞队列是为了削峰和异步化，Redisson 锁是业务层最后一道兜底，防止同一用户在并发条件下重复创建订单。

### 6.6 博客点赞和 Feed 流

博客模块用了 Redis ZSet 做两个事情：

1. 点赞榜
   用 ZSet 记录点赞用户和点赞时间。
2. 关注推送
   用户发博客后，把博客 ID 推送到粉丝的收件箱 ZSet。

读取关注 Feed 时，使用的是“滚动分页”思路：

1. 按分数倒序查询
2. 记录 `minTime`
3. 记录同分元素偏移量 `offset`
4. 下一次分页继续从这个游标往后拉

关键代码：

1. `src/main/java/com/xhs/service/impl/BlogServiceImpl.java`
2. `src/main/java/com/xhs/controller/BlogController.java`
3. `src/main/java/com/xhs/dto/ScrollResult.java`

面试亮点：

> 普通分页适合静态列表，但 Feed 流更适合滚动分页。我这里用的是 Redis ZSet 的时间戳做 score，再通过 `lastId + offset` 实现滚动翻页。

### 6.7 关注和共同关注

关注关系除了写库，还写了一份 Redis Set，方便做集合运算。

场景：

1. 关注某人
2. 取关某人
3. 判断是否已关注
4. 查询共同关注

共同关注做法：

1. 每个用户一个 `follows:{userId}` Set
2. 两个 Set 做交集
3. 得到共同关注用户 ID 列表
4. 再回库查询用户信息

关键代码：

1. `src/main/java/com/xhs/service/impl/FollowServiceImpl.java`

### 6.8 签到统计

签到模块用了 Redis Bitmap。

处理思路：

1. 每个用户每月一个 key
2. 用 `SETBIT` 记录当天是否签到
3. 用 `BITFIELD` 拉取本月签到位图
4. 通过位运算统计连续签到天数

关键代码：

1. `src/main/java/com/xhs/service/impl/UserServiceImpl.java`

## 7. 项目里的 Redis 用法总结

这是面试里很加分的一段，可以直接总结成表述：

1. `String`
   验证码、店铺缓存、空值缓存、秒杀库存。
2. `Hash`
   登录态用户信息。
3. `Set`
   关注关系、共同关注。
4. `ZSet`
   博客点赞榜、Feed 收件箱。
5. `Bitmap`
   用户签到。
6. `GEO`
   附近店铺查询。
7. `Lua`
   秒杀资格原子校验。

面试简答版：

> 这个项目里 Redis 不只是缓存，我基本把它当成了多结构的业务中间层来用，登录态、GEO、ZSet、Bitmap、Lua 都覆盖到了。

## 8. AI 模块详细拆解

这一部分是你新增的核心亮点，面试时可以重点讲。

### 8.1 为什么要单独加 AI 模块

原项目本质上已经有很多结构化业务能力，比如：

1. 店铺查询
2. 分类查询
3. 优惠券查询
4. 秒杀订单查询
5. 登录态识别

这些能力非常适合改造成大模型的工具调用能力。所以我没有把 AI 做成一个“纯聊天壳子”，而是把它做成：

1. 可理解业务语义
2. 可调用真实系统能力
3. 可结合知识库回答规则问题
4. 可做多轮会话的业务助手

### 8.2 为什么要先升级到 Spring Boot 3

原项目最初是 `Spring Boot 2.7.18`，但 `Spring AI Alibaba` 当前体系是基于 Boot 3 / Spring 6 的。

所以我先做了底层兼容：

1. Boot 2.7 升级到 Boot 3.5
2. `javax.*` 全量切到 `jakarta.*`
3. `mybatis-plus-boot-starter` 切到 `mybatis-plus-spring-boot3-starter`
4. 新增 `spring-ai-alibaba-starter-dashscope`

这一步本身也是一个工程能力亮点，因为它不是单纯“加个依赖”，而是框架兼容改造。

### 8.3 AI 模块目录结构

AI 相关代码主要集中在以下位置：

1. 配置层
   `src/main/java/com/xhs/ai/config/AiAssistantConfig.java`
   `src/main/java/com/xhs/ai/config/AiAssistantProperties.java`
2. 控制层
   `src/main/java/com/xhs/controller/AiAssistantController.java`
3. 核心服务层
   `src/main/java/com/xhs/ai/service/AiAssistantService.java`
4. 工具层
   `src/main/java/com/xhs/ai/tool/XiaohongshuAiTools.java`
5. 知识库层
   `src/main/java/com/xhs/ai/knowledge/AiKnowledgeService.java`
   `src/main/resources/ai/knowledge/*.json`
6. 提示词层
   `src/main/java/com/xhs/ai/prompt/AiPromptService.java`
   `src/main/resources/ai/prompts/*.txt`

### 8.4 AI 提供了哪些能力

当前有两个场景：

1. 智能客服
   接口：`POST /ai/customer-service/chat`
2. 智能查询问答
   接口：`POST /ai/query/chat`

另外有一个会话清理接口：

1. `DELETE /ai/session/{scene}/{sessionId}`

控制器代码：

1. `src/main/java/com/xhs/controller/AiAssistantController.java`

### 8.5 AI 模块的核心调用链

一次 AI 请求的链路大概是：

1. 前端调用 `/ai/customer-service/chat` 或 `/ai/query/chat`
2. `AiAssistantController` 收到请求
3. 进入 `AiAssistantService`
4. 根据场景选择系统提示词
5. 根据用户问题从本地知识库里做召回
6. 绑定会话 ID 和 `ChatMemory`
7. 把当前登录用户 ID、知识片段、用户问题拼成 prompt
8. 使用 `ChatClient` 发起模型调用
9. 模型在需要时自动调用 `XiaohongshuAiTools`
10. 工具返回真实业务数据
11. 大模型基于真实数据组织自然语言回答
12. 返回统一 `AiChatResponse`

核心代码：

1. `src/main/java/com/xhs/ai/service/AiAssistantService.java`

### 8.6 为什么不用“纯 Prompt 问答”，而要做工具调用

因为这个项目里很多问题不是开放式知识，而是系统内的实时业务数据，例如：

1. 某店铺有什么券
2. 某个分类下有哪些商户
3. 某个用户最近的订单状态

这些问题如果只靠 Prompt，大模型会幻觉；所以我做了工具化暴露，把已有 service / mapper 封装成模型可调用能力。

### 8.7 工具调用设计

工具类在：

1. `src/main/java/com/xhs/ai/tool/XiaohongshuAiTools.java`

当前主要提供了这些工具：

1. `searchShopsByKeyword`
   按店铺名、商圈、地址模糊搜索。
2. `searchShopsByTypeName`
   按店铺分类推荐高分店铺。
3. `getShopDetail`
   根据店铺 ID 查详情。
4. `getShopVouchers`
   查询店铺优惠券。
5. `listShopTypes`
   查询所有店铺分类。
6. `getRecentVoucherOrdersByUserId`
   查询用户最近团购券订单。

这几个工具的设计思路是：

1. 输入参数尽量简单，方便模型理解
2. 返回结构是 `Map<String,Object>` 或 `List<Map<String,Object>>`
3. 输出字段尽量是业务可读字段，不把数据库原始结构直接暴露给模型
4. 对金额、评分、时间做了一层格式化

这部分在面试中可以这样表述：

> 我没有让大模型直接去“猜你数据库里有什么”，而是把真实业务能力封装成 Tool，让模型去调用系统内已有 service。这样模型回答的是“带数据来源”的，不是纯生成。

### 8.8 提示词设计

提示词不是一个统一模板，而是按场景拆分了两套：

1. 客服提示词
   `src/main/resources/ai/prompts/customer-service-system-prompt.txt`
2. 查询提示词
   `src/main/resources/ai/prompts/query-system-prompt.txt`

设计目标不同：

1. 客服 Prompt
   更偏平台使用说明、业务规则说明、是否需要登录、是否可用等。
2. 查询 Prompt
   更偏数据查询、结果整理、推荐和比较。

这样拆分的原因是：

1. 降低单一 Prompt 过于泛化的问题
2. 让场景边界更清晰
3. 更方便后续做多 Agent / 多场景扩展

### 8.9 本地知识库设计

知识库不走向量数据库，而是走轻量本地 JSON + 关键字召回。

原因：

1. 当前项目体量不大
2. 规则知识是结构化、稳定的
3. 先做简单可控版本，更适合课程项目和演示场景

相关代码：

1. `src/main/java/com/xhs/ai/knowledge/AiKnowledgeService.java`
2. `src/main/resources/ai/knowledge/customer-service-knowledge.json`
3. `src/main/resources/ai/knowledge/query-knowledge.json`

召回逻辑：

1. 先把知识项加载到内存
2. 对用户问题做 normalize
3. 根据标题、关键词、正文内容做简单打分
4. 取 `TopK`
5. 如果都没命中，就返回 `general` 兜底知识

这个设计的优点：

1. 不依赖向量库
2. 可解释性好
3. 本地调试简单

局限也要能讲出来：

1. 不适合大规模知识库
2. 对语义召回能力有限
3. 更像规则检索而不是语义检索

### 8.10 会话记忆设计

会话记忆采用：

1. `MessageWindowChatMemory`
2. `InMemoryChatMemoryRepository`

配置在：

1. `src/main/java/com/xhs/ai/config/AiAssistantConfig.java`

设计含义：

1. 每个会话根据 `scene + sessionId` 组成唯一 conversationId
2. 只保留固定数量的消息窗口
3. 用于支撑多轮问答

为什么这里先用内存记忆：

1. 代码接入最简单
2. 不破坏现有 Redis 业务结构
3. 更适合现在这个项目的演示场景

如果面试官问“生产上这样够吗”，建议你主动说：

> 目前只是演示版，记忆存在 JVM 内存里，应用重启会丢。生产环境我会把 ChatMemoryRepository 换成 Redis 或数据库实现。

### 8.11 AI 配置设计

AI 配置被抽到了 `xiaohongshu.ai` 前缀下：

1. 模型名
2. 客服温度
3. 查询温度
4. 最大 token
5. 最大记忆消息数
6. 知识库 TopK

对应代码：

1. `src/main/java/com/xhs/ai/config/AiAssistantProperties.java`

这样做的好处是：

1. 运行参数和业务逻辑解耦
2. 后续方便调参
3. 多环境切换更简单

### 8.12 AI 模块和原业务怎么衔接

这是面试里很关键的一点。

我的设计不是把 AI 做成单独孤岛，而是把 AI 作为“现有业务能力的自然语言入口”：

1. AI 不直接碰数据库
2. AI 不重写原业务逻辑
3. AI 只负责：
   场景编排、知识召回、会话记忆、模型调用、工具选择
4. 真正的数据查询仍然走原有 `service / mapper`

比如：

1. 查询优惠券
   AI 调用 `voucherMapper.queryVoucherOfShop()`
2. 查询店铺
   AI 调用 `shopService`
3. 查询订单
   AI 调用 `voucherOrderService`

所以你可以这么总结：

> AI 模块本质上是对原业务系统做了一层“自然语言编排层”，而不是另外造一个业务系统。

### 8.13 我在 AI 模块里的工程取舍

这部分非常适合面试。

我做过的核心取舍：

1. 取舍一：不引入向量库
   因为当前知识量小，先用 JSON + 打分召回，成本低，可解释性高。
2. 取舍二：不用数据库持久化聊天记忆
   因为当前更偏 demo 和课程项目，先用内存窗口实现多轮。
3. 取舍三：不重构原业务层
   因为目标是“最小改动接入 AI”，保留原项目结构。
4. 取舍四：区分客服和查询两个场景
   因为它们的 Prompt 和回答风格不一样，拆开更稳定。

### 8.14 AI 模块当前限制

你最好在面试时主动说出限制，这样显得你判断更成熟。

当前限制包括：

1. 聊天记忆是内存级，服务重启会丢失
2. 知识库召回不是语义级，只是轻量关键字匹配
3. 没有做流式输出，当前是普通同步响应
4. 没有接前端聊天页面，当前主要是后端 API
5. 工具数量还不算多，主要覆盖店铺、券、订单三类高频场景

### 8.15 AI 模块后续优化方向

如果面试官问“你会继续怎么做”，你可以答：

1. 把聊天记忆持久化到 Redis
2. 接入向量库，替换轻量知识召回
3. 增加博客、关注、签到等更多工具
4. 增加 SSE 流式接口
5. 对工具返回增加统一 schema
6. 引入多 Agent 或工作流拆分客服与查询链路

## 9. 这个项目最适合讲的技术亮点

如果你需要“面试高频亮点总结”，我建议重点讲这 6 个：

1. Redis 多数据结构实践
   String、Hash、Set、ZSet、Bitmap、GEO、Lua 都用到了。
2. 登录态无状态化
   验证码登录 + Redis token。
3. 缓存体系设计
   穿透、击穿、逻辑过期。
4. 秒杀高并发方案
   Lua + 异步队列 + 分布式锁 + 事务。
5. Feed 流和滚动分页
   ZSet + 时间戳游标。
6. AI 工具调用集成
   把存量业务能力升级成大模型可调用工具。

## 10. 面试时怎么讲这个项目

### 10.1 一分钟版本

> 这是一个本地生活服务后端项目，技术栈是 Spring Boot、MyBatis-Plus、Redis、MySQL。原项目重点解决了验证码登录、店铺缓存、秒杀下单、Feed 流和签到统计等问题。我在这个基础上新增了一个 AI 模块，基于 Spring AI Alibaba 做了智能客服和智能查询问答，通过 Tool 调用复用现有店铺、优惠券、订单能力，让模型基于真实数据回答问题。

### 10.2 三分钟版本

> 项目整体是典型单体后端，controller、service、mapper 分层比较清晰。核心业务包括用户登录、店铺查询、优惠券秒杀、博客点赞和关注推送。  
> 登录采用手机号验证码 + Redis token；店铺详情用 Redis 缓存，处理了缓存穿透和击穿；附近店铺查询用了 Redis GEO；秒杀模块用了 Lua + 阻塞队列 + Redisson 锁；博客模块用 ZSet 做点赞榜和 Feed 流。  
> 在这个基础上，我把项目升级到 Spring Boot 3，接入 Spring AI Alibaba，新加了智能客服和智能查询问答模块。AI 模块不是纯 Prompt 聊天，而是通过 Tool 调用查询真实业务数据，再结合本地知识库和会话记忆生成回答，这样幻觉更少，也更贴合业务。

### 10.3 五分钟版本

五分钟版本建议讲这三层：

1. 业务层
   这是什么项目，解决什么业务问题。
2. 系统层
   登录、缓存、秒杀、Feed 流分别怎么做。
3. AI 层
   为什么接 AI，怎么做工具调用，怎么做知识库和会话记忆，为什么这样设计。

## 11. 面试官可能会问的问题

### 11.1 为什么登录态放 Redis，不放 Session

答：

> 因为 Redis token 更适合前后端分离和多实例部署，而且我只缓存 `UserDTO`，比直接存整个 Session 更轻。

### 11.2 秒杀为什么要用 Lua

答：

> 因为库存判断和一人一单判断必须原子执行，Lua 在 Redis 内执行可以避免并发下多次网络往返导致的不一致。

### 11.3 关注 Feed 为什么用滚动分页

答：

> 因为 Feed 流是持续写入的，普通页码分页在新增数据后会发生数据漂移，滚动分页更稳定。

### 11.4 你新增的 AI 模块和普通大模型对话有什么区别

答：

> 普通对话更多依赖模型生成，我这里是“模型 + 工具 + 知识 + 会话记忆”的组合。模型不是瞎猜，而是调用系统内真实能力再回答。

### 11.5 为什么没直接上向量库

答：

> 因为当前知识量比较小，规则知识多、结构稳定，我先用了 JSON + 关键字打分召回。这样实现简单、可控，适合当前项目阶段。后续如果知识规模扩大，我会再换成向量检索。

## 12. 简历上怎么写

你可以写成这样：

> 小红书后端项目，基于 Spring Boot、MyBatis-Plus、Redis、MySQL 实现本地生活服务系统，覆盖验证码登录、店铺缓存、GEO 附近检索、优惠券秒杀、博客 Feed 流和签到统计等核心功能；新增基于 Spring AI Alibaba 的智能客服与智能查询问答模块，通过 Tool Calling 复用店铺、优惠券、订单等业务能力，并结合本地知识库和会话记忆实现多轮业务问答。

## 13. 我建议你面试时强调的关键词

这些词你可以有意识地往外说：

1. 无状态登录
2. Redis 多结构实战
3. 缓存击穿 / 穿透
4. GEO 检索
5. Lua 原子脚本
6. 异步削峰
7. Feed 流
8. 滚动分页
9. Tool Calling
10. Prompt 分场景
11. 轻量知识召回
12. 会话记忆

## 14. 最后总结

这个项目最有价值的点，不是“功能很多”，而是它覆盖了一条比较完整的后端成长路径：

1. 从传统 CRUD 走到 Redis 多结构设计
2. 从单纯查库走到缓存与高并发
3. 从业务接口走到自然语言 AI 编排

如果你在面试中能把“原系统能力”和“AI 增量能力”讲成一个整体，这个项目会比单纯背八股更有说服力。
