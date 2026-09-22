# FlashSale

FlashSale 是一个独立的限量商品秒杀服务，集中展示高并发资格判断和最终一致性处理。它与 OrderHub 的商城履约链路互补，不重复实现商品、支付和物流等通用业务。

## 核心链路

```text
HTTP 请求
  -> Redis Lua 原子校验时间、库存和一人一单
  -> RabbitMQ Publisher Confirm
  -> 异步消费者
  -> MySQL 条件扣库存并创建订单
  -> 失败重试 / 死信 / Redis 幂等补偿 / 定时对账
```

- Redis Lua 在一次执行中完成资格判断和库存预扣，避免并发超卖。
- RabbitMQ 把请求线程与数据库写入解耦；唯一索引和条件更新守住最终事实。
- 发布失败按 `REJECTED` 与 `UNKNOWN` 分类，避免确认超时后错误归还库存。
- 消费失败经过三次重试进入死信，失败记录和补偿 Lua 支持重复执行。
- Micrometer 输出业务结果计数和端到端耗时，k6 脚本提供可重复的本机压测入口。

## 本地运行

```powershell
Copy-Item .env.example .env
docker compose up -d --build --wait
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8081/api/activities
```

抢购和订单查询：

```powershell
$headers = @{ 'X-User-Id' = '1001'; 'X-Request-Id' = 'demo-request-1001' }
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/activities/1/reservations -Headers $headers
Invoke-RestMethod -Uri http://localhost:8081/api/orders/by-request/demo-request-1001 -Headers @{ 'X-User-Id' = '1001' }
```

## 测试与压测

```powershell
mvn test
mvn -Dtest=FlashSaleReservationIT,FlashSaleOrderIT,MessageReliabilityIT test
$env:K6_VUS = '20'
$env:K6_DURATION = '10s'
docker compose --profile loadtest run --rm k6
```

k6 使用唯一用户和请求号，输出吞吐量、P95/P99、HTTP 失败率以及业务接受/拒绝数量。默认演示活动只有 100 件库存，超过库存后的请求返回 `SOLD_OUT` 属于正常业务结果；比较容量前应使用独立测试活动并记录机器配置。

业务指标可通过 `/actuator/metrics/flashsale.reservation.results` 和 `/actuator/metrics/flashsale.reservation.duration` 查看，Prometheus 文本位于 `/actuator/prometheus`。RabbitMQ 管理台默认位于 `http://localhost:15673`。

详细教学与每日实测记录保存在本地 `docs/`，不会提交到 Git。

## 项目边界

- 演示版通过经过格式校验的 `X-User-Id` 表示登录用户，认证职责由上游承担。
- Redis、RabbitMQ 与 MySQL 之间没有分布式强事务，系统以确认、幂等、重试、补偿和对账实现最终一致。
- 压测结果只代表测试机器、参数和数据集，不直接等同于生产容量。