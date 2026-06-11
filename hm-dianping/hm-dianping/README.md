# Xiaohongshu

基于 Spring Boot 3 的小红书风格内容社区与视频互动平台。项目覆盖笔记发布、视频弹幕、私信、Feed 流、秒杀、搜索、对象存储上传和缓存同步，重点放在高并发链路、最终一致性和工程可落地性。

## 技术栈

- Java 17, Spring Boot 3.5, MyBatis-Plus
- Redis, Redisson, Lua
- Kafka, Dead Letter Topic, Outbox
- Netty WebSocket
- Canal, MySQL binlog
- Elasticsearch
- Aliyun OSS
- Spring AI Alibaba DashScope
- Docker Compose

## 核心能力

- 内容社区：笔记发布、评论、点赞、收藏、关注、通知、搜索和推荐。
- Feed 流：推拉结合，关注关系强的用户走推模式，热点和广场内容走拉模式。
- 秒杀下单：Redis 预扣库存、Lua 原子校验、一人一单、Kafka 异步下单、死信队列和补偿回滚。
- 视频弹幕：Netty WebSocket 按 `videoId` 维护房间，先实时广播，再异步投递 Kafka，由消费者批量落库并刷新 Redis ZSet。
- 私信：Netty WebSocket 实时通道，发送消息时写 MySQL 和 outbox，后台 relay 投递 Kafka，消费者推送本机在线连接，失败进入 DLT 并写入补偿表。
- OSS 上传：支持后端签名直传、分片上传、断点续传和异常分片清理补偿。
- 缓存与搜索同步：Canal 订阅 MySQL binlog，将核心表变更同步到 Redis 和 Elasticsearch，并带断线重连、指数退避和失败补偿。
- 稳定性治理：滑动窗口限流、接口幂等、请求日志、慢链路定位、补偿事件表、Actuator 指标。

## 快速启动

1. 准备 JDK 17、Maven 3.8+、Docker Desktop、MySQL 8.0 和 Redis 7+。
2. 复制 `.env.example` 为 `.env`，按本地环境填写数据库、Redis、OSS 等配置。
3. 创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS xiaohongshu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

4. 按顺序执行脚本：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/xiaohongshu.sql
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/upgrade-roles.sql
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/upgrade-indexes.sql
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/upgrade-content-community.sql
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/upgrade-mall.sql
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/upgrade-private-message.sql
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/upgrade-compensation.sql
mysql -h 127.0.0.1 -P 3306 -u root -p xiaohongshu < src/main/resources/db/upgrade-private-message-kafka.sql
```

5. 启动应用：

```bash
docker compose up -d kafka elasticsearch canal app
```

如果 MySQL 和 Redis 也想交给 Docker：

```bash
docker compose --profile docker-db up -d mysql redis kafka elasticsearch canal app
```

## 访问地址

- Web 页面：http://localhost:8081/
- 健康检查：http://localhost:8081/actuator/health
- 弹幕 WebSocket：`ws://localhost:8090/ws/danmaku`
- 私信 WebSocket：`ws://localhost:8091/ws/messages`
- Elasticsearch：http://localhost:9200/
- Kafka 外部端口：`localhost:9092`

## 关键配置

| 变量 | 说明 |
| --- | --- |
| `XHS_DATASOURCE_URL` | MySQL 连接地址 |
| `XHS_DATASOURCE_USERNAME` | MySQL 用户名 |
| `XHS_DATASOURCE_PASSWORD` | MySQL 密码 |
| `XHS_REDIS_HOST` | Redis 地址 |
| `XHS_REDIS_PASSWORD` | Redis 密码 |
| `XHS_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers |
| `XHS_ELASTICSEARCH_URIS` | Elasticsearch 地址 |
| `XHS_CANAL_ENABLED` | 是否启用 Canal 同步 |
| `XHS_STORAGE_TYPE` | `local` 或 `oss` |
| `XHS_OSS_ENDPOINT` | OSS endpoint |
| `XHS_OSS_BUCKET` | OSS bucket |
| `XHS_DANMAKU_WS_PORT` | 弹幕 WebSocket 端口 |
| `XHS_PRIVATE_MESSAGE_WS_PORT` | 私信 WebSocket 端口 |
| `XHS_AI_ENABLED` | 是否启用 AI 模块 |

## 项目结构

```text
src/main/java/com/xhs
  ai/                 AI 客服与查询助手
  canal/              Canal binlog 客户端与同步分发
  compensation/       补偿事件状态与调度
  config/             Web、Kafka、Redis、限流、权限等配置
  controller/         HTTP API
  danmaku/            视频弹幕 WebSocket 和 Kafka 链路
  privatemessage/     私信 WebSocket、outbox、Kafka 消费
  service/            业务接口
  service/impl/       业务实现
  service/storage/    本地存储和 OSS 存储
src/main/resources/db 数据库初始化与升级脚本
src/main/resources/static 前端静态页面
docs                  面试讲解和模块说明文档
```

## 安全注意

- `.env` 已被忽略，不要提交真实数据库密码、OSS AccessKey、AI API Key。
- OSS 生产环境建议使用 RAM 子账号和最小权限策略。
- 如果密钥曾经暴露在截图、日志或提交记录里，建议在控制台轮换。
- 生产环境建议把 `logging.level.com.xhs` 调整为 `info` 或 `warn`，避免 DEBUG 日志过多。
