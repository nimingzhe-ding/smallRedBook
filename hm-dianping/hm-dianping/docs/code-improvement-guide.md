# xiaohongshu 代码完善说明

本文档记录本次代码完善的目标、修改内容、运行配置、数据库变更和后续建议。适合作为复盘材料，也可以用于面试时说明项目优化点。

## 一、整改目标

本次主要解决以下几类问题：

1. 敏感配置泄露：API Key、数据库密码、Redis 密码不应写死在仓库中。
2. 秒杀链路稳定性：Lua 脚本、活动时间校验、队列失败补偿、数据库事务都需要更稳。
3. 权限控制：上传接口和 AI 订单查询接口不能匿名访问或越权访问。
4. 文件上传安全：需要限制文件类型、大小，并防止路径穿越删除文件。
5. 缓存工具可靠性：通用缓存工具不能硬编码店铺 key，也不能忽略传入的时间单位。
6. 数据库兜底：关注和下单需要唯一索引，不能只依赖应用层判断。
7. 测试稳定性：手工 Redis 数据初始化脚本不能作为默认自动测试执行。

## 二、配置脱敏

修改文件：

- `src/main/resources/application.yaml`

现在配置改为从环境变量读取：

```yaml
spring:
  ai:
    dashscope:
      api-key: "${DASHSCOPE_API_KEY:}"
  datasource:
    url: "${XHS_DATASOURCE_URL:jdbc:mysql://127.0.0.1:3306/xiaohongshu?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}"
    username: "${XHS_DATASOURCE_USERNAME:root}"
    password: "${XHS_DATASOURCE_PASSWORD:}"
  data:
    redis:
      host: "${XHS_REDIS_HOST:127.0.0.1}"
      port: ${XHS_REDIS_PORT:6379}
      password: "${XHS_REDIS_PASSWORD:}"
```

本地运行前建议设置：

```powershell
$env:DASHSCOPE_API_KEY="你的 DashScope Key"
$env:XHS_DATASOURCE_PASSWORD="你的 MySQL 密码"
$env:XHS_REDIS_PASSWORD="你的 Redis 密码"
$env:XHS_REDIS_HOST="127.0.0.1"
$env:XHS_IMAGE_UPLOAD_DIR="D:/xiaohongshu/nginx-1.18.0/html/xiaohongshu/imgs"
```

注意：如果之前真实 Key 已经提交过，应该到对应平台立即作废旧 Key 并生成新 Key。

## 三、秒杀链路优化

修改文件：

- `src/main/resources/seckill.lua`
- `src/main/resources/rollback-seckill.lua`
- `src/main/java/com/xhs/service/impl/VoucherOrderServiceImpl.java`
- `src/main/java/com/xhs/service/IVoucherOrderService.java`

### 1. Lua 脚本处理库存 key 不存在

原逻辑如果 Redis 中没有 `seckill:stock:{voucherId}`，`tonumber(nil)` 会报错。

现在脚本先取库存：

```lua
local stock = redis.call('GET', stockKey)
if (stock == false or tonumber(stock) <= 0) then
    return 1
end
```

这样库存 key 不存在时会按库存不足处理。

### 2. 秒杀前增加活动时间校验

接口层会先查询 `tb_seckill_voucher`：

- 活动不存在：返回 `秒杀券不存在`
- 未开始：返回 `秒杀尚未开始`
- 已结束：返回 `秒杀已经结束`

这样不会只依赖 Redis 库存判断。

### 3. 队列写入失败时回滚 Redis 状态

Lua 扣减库存成功后，订单会进入内存队列。如果队列满了，原逻辑会出现“Redis 库存已扣，但订单没入队”的问题。

现在新增 `rollback-seckill.lua`：

```lua
redis.call('INCRBY', stockKey, 1)
redis.call('SREM', orderKey, userId)
```

当 `orderTasks.offer(voucherOrder)` 失败时，会回滚 Redis 库存和用户下单标记。

### 4. 异步线程内使用事务模板

原逻辑依赖 `AopContext.currentProxy()`，且先入队再设置代理对象，存在后台线程先消费导致空指针的风险。

现在改为 `TransactionTemplate`：

```java
Boolean created = transactionTemplate.execute(status -> createVoucherOrder(voucherOrder));
```

异步线程也能稳定执行数据库事务，不再依赖请求线程里的 AOP 代理。

### 5. 数据库失败时回滚 Redis

如果数据库库存扣减、订单保存、唯一约束等步骤失败，异步线程会调用 Redis 回滚脚本，减少 Redis 与 MySQL 状态不一致的概率。

说明：当前仍然是“内存队列”版本。生产级方案建议改为 Redis Stream、RabbitMQ、Kafka 等消息队列，并建立消费组、ACK、重试和死信处理。

## 四、数据库唯一约束

修改文件：

- `src/main/resources/db/xiaohongshu.sql`

新增约束：

```sql
UNIQUE INDEX `uk_user_follow`(`user_id`, `follow_user_id`) USING BTREE
UNIQUE INDEX `uk_user_voucher`(`user_id`, `voucher_id`) USING BTREE
```

如果你的数据库已经创建过表，需要手动执行：

```sql
ALTER TABLE tb_follow
ADD UNIQUE INDEX uk_user_follow(user_id, follow_user_id);

ALTER TABLE tb_voucher_order
ADD UNIQUE INDEX uk_user_voucher(user_id, voucher_id);
```

执行前请先检查是否有重复数据：

```sql
SELECT user_id, follow_user_id, COUNT(*)
FROM tb_follow
GROUP BY user_id, follow_user_id
HAVING COUNT(*) > 1;

SELECT user_id, voucher_id, COUNT(*)
FROM tb_voucher_order
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1;
```

如果有重复数据，需要先清理重复记录，再添加唯一索引。

## 五、上传接口安全

修改文件：

- `src/main/java/com/xhs/controller/UploadController.java`
- `src/main/java/com/xhs/config/MvcConfig.java`

### 1. 上传接口需要登录

`MvcConfig` 不再放行 `/upload/**`，上传和删除图片都需要携带有效 token。

### 2. 限制文件类型和大小

当前限制：

- 最大 5MB
- 仅允许 `jpg`、`jpeg`、`png`、`gif`、`webp`
- `Content-Type` 必须以 `image/` 开头

### 3. 防止路径穿越

删除图片时会将目标路径规范化，并检查最终路径必须位于上传根目录下：

```java
Path target = root.resolve(normalizedName).normalize();
if (!target.startsWith(root)) {
    throw new IllegalArgumentException("错误的文件路径");
}
```

同时只允许删除 `blogs/` 目录下的文件，避免误删上传目录外的内容。

## 六、AI 订单查询越权修复

修改文件：

- `src/main/java/com/xhs/config/MvcConfig.java`
- `src/main/java/com/xhs/ai/tool/XiaohongshuAiTools.java`

### 1. AI 接口需要登录

`MvcConfig` 不再放行 `/ai/**`。调用 AI 接口时，拦截器会从 token 中恢复当前登录用户。

### 2. AI 工具不再信任模型传入的 userId

原方法按参数 `userId` 查询订单，存在“传入别人的 userId 查询别人订单”的风险。

现在订单查询强制使用：

```java
UserDTO currentUser = UserHolder.getUser();
Long currentUserId = currentUser.getId();
```

即使模型或用户输入了别人的 ID，也只会查当前登录用户自己的订单。

## 七、缓存工具修复

修改文件：

- `src/main/java/com/xhs/utils/CacheClient.java`
- `src/main/java/com/xhs/service/impl/ShopServiceImpl.java`

### 1. 修复 TimeUnit 被忽略的问题

原 `CacheClient.set` 固定使用秒：

```java
set(key, value, time, TimeUnit.SECONDS)
```

现在使用调用方传入的 `timeUnit`。

### 2. 修复硬编码缓存 key

原 `queryWithPassThrough` 和 `queryWithLogicalExpire` 内部硬编码 `CACHE_SHOP_KEY`，导致它不是通用组件。

现在统一使用入参 `keyPrefix + id`。

### 3. 缓存重建改用线程池

原逻辑每次缓存重建都 `new Thread()`，高并发下线程不可控。

现在使用固定线程池：

```java
private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(5);
```

### 4. 修复店铺缓存互斥锁误删

`ShopServiceImpl.queryWithMutex` 原来在 `finally` 中无条件 `unlock(lockKey)`。如果当前线程没拿到锁，也会删掉别的线程的锁。

现在只有拿到锁的线程才释放锁：

```java
if (isLock) {
    unlock(lockKey);
}
```

## 八、测试调整

修改文件：

- `src/test/java/com/xhs/XiaohongshuApplicationTests.java`

`loadShopData` 是 Redis GEO 数据初始化脚本，需要 MySQL 和 Redis 都可用，不适合作为默认自动测试。

现在整个测试类标记为：

```java
@Disabled("Manual Redis GEO data loader. Enable it only when MySQL and Redis are ready.")
```

如果需要重新导入店铺 GEO 数据，可以临时去掉 `@Disabled`，并确认：

1. MySQL 已导入 `xiaohongshu.sql`
2. Redis 已启动并能连接
3. `application.yaml` 或环境变量里的 Redis/MySQL 配置正确

## 九、验证结果

已执行：

```powershell
mvn clean compile
mvn test
```

结果：

- `mvn clean compile` 成功
- `mvn test` 成功，当前 1 个手工测试被跳过

仍存在本机 Maven 警告：

```text
D:\java\Maven\apache-maven-3.9.11-bin\apache-maven-3.9.11\conf\settings.xml
line 206, column 13
```

这个不是项目代码问题，是 Maven `settings.xml` 中疑似存在不可见字符或错误 XML 内容。建议打开该文件第 206 行附近，删除异常空白字符或修复 XML 标签。

## 十、后续建议

本次已修复主要风险，但如果继续提升项目质量，建议按下面顺序做：

1. 把秒杀内存队列升级为 Redis Stream 或 RabbitMQ，支持 ACK、重试、死信和服务重启后继续消费。
2. 给登录验证码增加发送频率限制，例如同一手机号 60 秒内只能发送一次。
3. 给接口参数增加 Bean Validation，例如手机号、分页参数、上传参数。
4. 给关注、点赞、秒杀写单元测试或集成测试，避免后续改动破坏并发逻辑。
5. AI 会话记忆目前使用内存存储，服务重启会丢失；需要持久化可改 Redis 或数据库。
6. 统一修复源码注释乱码，建议确认源文件编码为 UTF-8。
