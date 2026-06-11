# 小红书 AI 模块接入文档

## 1. 模块目标

在不改变原有业务结构的前提下，为当前项目新增两个独立的 AI 能力：

1. 智能客服
2. 智能查询问答

本次实现采用 `Spring AI Alibaba + DashScope`，AI 能力通过新增模块接入，原有店铺、优惠券、订单、登录等业务接口和 service 结构保持不变。

## 2. 为什么先做兼容改造

当前项目原始版本是 `Spring Boot 2.7.18`，而官方 `Spring AI Alibaba` 最新兼容说明对应的是 `Spring Boot 3.x` 体系。

本次已经完成的兼容改造：

1. `Spring Boot 2.7.18 -> 3.5.0`
2. `javax.* -> jakarta.*`
3. `mybatis-plus-boot-starter -> mybatis-plus-spring-boot3-starter`
4. 新增 `spring-ai-alibaba-starter-dashscope`

参考资料：

1. Spring AI Alibaba 兼容说明：`https://java2ai.com/docs/1.1.2.0/overview/version-explanation`
2. Spring AI Alibaba 快速开始：`https://java2ai.com/en/docs/quick-start`
3. Spring AI ChatClient 文档：`https://docs.spring.io/spring-ai/reference/api/chatclient.html`

## 3. 当前实现架构

### 3.1 新增模块结构

新增代码位于 `com.xhs.ai` 包及一个新的 AI 控制器：

1. `com.xhs.ai.config`
2. `com.xhs.ai.dto`
3. `com.xhs.ai.enums`
4. `com.xhs.ai.knowledge`
5. `com.xhs.ai.prompt`
6. `com.xhs.ai.service`
7. `com.xhs.ai.tool`
8. `com.xhs.controller.AiAssistantController`

### 3.2 模块组成

1. `AiAssistantService`
   负责统一编排智能客服与智能查询问答流程。
2. `AiPromptService`
   负责加载不同场景的系统提示词。
3. `AiKnowledgeService`
   负责加载本地知识库 JSON，并按关键字命中业务知识片段。
4. `XiaohongshuAiTools`
   把现有店铺、分类、优惠券、订单能力封装为 AI 可调用工具。
5. `ChatMemory`
   采用 `MessageWindowChatMemory + InMemoryChatMemoryRepository` 保存短期上下文窗口。

### 3.3 为什么这样设计

这样设计有三个目的：

1. 不侵入原业务 service，AI 模块只做“组合调用”
2. 让模型回答时优先调用真实业务工具，减少幻觉
3. 通过本地知识库补充平台规则、订单状态、登录说明等业务语义

## 4. 功能说明

### 4.1 智能客服

接口：`POST /ai/customer-service/chat`

适合处理的问题：

1. 登录、验证码怎么用
2. 秒杀券怎么下单
3. 优惠券规则怎么看
4. 我的订单是什么状态
5. 这个平台能做什么

处理逻辑：

1. 加载客服系统提示词
2. 根据用户问题命中客服知识库
3. 结合会话上下文构造用户提示
4. 通过 `Spring AI Alibaba ChatClient` 发起调用
5. 遇到店铺、券、订单等精确数据时自动调用工具

### 4.2 智能查询问答

接口：`POST /ai/query/chat`

适合处理的问题：

1. 帮我找评分高的火锅店
2. Mamala 有什么券
3. 系统里有哪些店铺分类
4. 查询我最近的团购券订单
5. 某个店铺的人均、营业时间、地址是多少

处理逻辑：

1. 加载查询系统提示词
2. 命中查询知识库
3. 优先使用工具拉取真实数据
4. 对结果做自然语言整理后返回

## 5. AI 工具能力

当前已经暴露给模型的工具如下：

1. `searchShopsByKeyword`
   根据店铺名称、商圈、地址模糊查询店铺
2. `searchShopsByTypeName`
   根据店铺分类名称查询店铺
3. `getShopDetail`
   根据店铺 ID 查询详情
4. `getShopVouchers`
   查询店铺下可用优惠券
5. `listShopTypes`
   查询系统内店铺分类
6. `getRecentVoucherOrdersByUserId`
   查询当前用户最近团购券订单

这些工具全部复用现有 MyBatis-Plus service / mapper，不改原业务表结构。

## 6. 会话记忆设计

当前版本使用 `MessageWindowChatMemory`，只保留有限轮对话窗口。

特点：

1. 不改数据库
2. 不改 Redis 现有业务结构
3. 便于快速接入和调试

当前限制：

1. 会话记忆是进程内存级，应用重启后会丢失
2. 只适合当前演示和课程项目体量

如果后续要扩展成正式生产版，建议把 `InMemoryChatMemoryRepository` 替换成 Redis 或数据库实现。

## 7. 配置说明

### 7.1 Maven 依赖

核心依赖已经加入 `pom.xml`：

1. `spring-ai-alibaba-starter-dashscope`
2. `mybatis-plus-spring-boot3-starter`
3. `Spring Boot 3.5.0`

### 7.2 application.yaml 配置

已新增如下配置：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:please-replace-with-your-dashscope-api-key}

xiaohongshu:
  ai:
    model: qwen-plus
    customer-service-temperature: 0.6
    query-temperature: 0.2
    max-tokens: 1500
    max-messages: 12
    knowledge-top-k: 3
```

### 7.3 关键配置含义

1. `spring.ai.dashscope.api-key`
   DashScope 的 API Key
2. `xiaohongshu.ai.model`
   默认聊天模型，当前默认 `qwen-plus`
3. `customer-service-temperature`
   客服场景的温度，更偏自然交流
4. `query-temperature`
   查询场景的温度，更偏稳定和准确
5. `max-tokens`
   单次回复最大 token
6. `max-messages`
   会话记忆保留轮数
7. `knowledge-top-k`
   每次命中的本地知识片段数量

## 8. 接口文档

### 8.1 智能客服

请求：

`POST /ai/customer-service/chat`

```json
{
  "sessionId": "service-001",
  "message": "我怎么登录？",
  "reset": false
}
```

返回示例：

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "scene": "customer-service",
    "sessionId": "service-001",
    "answer": "你需要先调用 /user/code 发送验证码，再调用 /user/login 完成登录。",
    "knowledgeRefs": [
      "登录与验证码"
    ],
    "currentUserId": null,
    "timestamp": "2026-04-14T20:00:00"
  },
  "total": null
}
```

### 8.2 智能查询问答

请求：

`POST /ai/query/chat`

```json
{
  "sessionId": "query-001",
  "message": "Mamala 有什么券？",
  "reset": false
}
```

### 8.3 清空会话

请求：

`DELETE /ai/session/{scene}/{sessionId}`

示例：

```text
DELETE /ai/session/customer-service/service-001
DELETE /ai/session/query/query-001
```

## 9. 与原项目的关系

本次改动遵循“新增优先、原逻辑少改”的原则。

原有系统只发生了这些必要变化：

1. 升级到 Boot 3 兼容 Spring AI Alibaba
2. `javax` 包替换为 `jakarta`
3. 登录拦截器白名单新增 `/ai/**`
4. `application.yaml` 新增 AI 配置

原有业务接口路径、controller/service 分层、Redis 秒杀逻辑、店铺缓存逻辑都没有被重构。

## 10. 扩展建议

如果后续还要继续扩展，可以按下面方向演进：

1. 把会话记忆改为 Redis 持久化
2. 增加博客、关注、签到等更多业务工具
3. 增加管理员知识库、运营知识库等多知识域
4. 为查询问答增加结构化返回
5. 增加 SSE 流式输出接口

## 11. 本次落地结论

现在项目已经具备一个可运行的 AI 模块骨架，并且是贴合当前小红书业务数据的：

1. 有独立 AI 控制层
2. 有 Spring AI Alibaba 对话接入
3. 有工具调用
4. 有本地知识库
5. 有会话上下文
6. 有详细接入文档

你只需要补上 `DashScope API Key`，启动项目后就可以开始调用这两个接口。
