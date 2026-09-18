# FlashSale

一个五天完成的限量商品秒杀服务，用来专项验证 Redis Lua、异步下单、消息可靠性、补偿和压测能力，与 OrderHub 的完整商城履约闭环形成互补。

Day 2 已完成 Redis 活动预热、Lua 原子校验活动时间/库存/一人一单、抢购资格接口和真实并发测试。当前成功响应表示 Redis 已预扣库存；MySQL 订单将在 Day 3 通过 Redis Stream 异步创建。

## 本地验证

```powershell
mvn test
Copy-Item .env.example .env
docker compose up -d --build --wait
Invoke-RestMethod http://localhost:8081/api/activities
$headers = @{ 'X-User-Id' = '1001'; 'X-Request-Id' = 'demo-request-1001' }
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/activities/1/reservations -Headers $headers
```

Day 2 真实并发验证：先启动 Compose，再让 Maven 进程读取本地 `.env`，运行 `mvn -Dtest=FlashSaleReservationIT test`。该场景使用 160 个用户争抢 100 件库存，断言成功数、剩余库存、购买用户数和重复请求结果。

教学与每日进度位于本地 `docs/`，该目录不会进入 Git。当前没有执行 Git 初始化或提交。
