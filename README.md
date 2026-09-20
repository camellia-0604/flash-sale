# FlashSale

一个五天完成的限量商品秒杀服务，用来专项验证 Redis Lua、异步下单、消息可靠性、补偿和压测能力，与 OrderHub 的完整商城履约闭环形成互补。

Day 4 已完成 Redis Lua 资格预扣、RabbitMQ Publisher Confirm、异步下单、消费重试与死信、MySQL 条件扣库存、唯一约束幂等、失败记录、Redis 补偿和定时对账。明确未投递的消息立即补偿，确认结果未知的消息先等待消费或对账，避免错误加回库存。

## 本地验证

```powershell
mvn test
Copy-Item .env.example .env
docker compose up -d --build --wait
Invoke-RestMethod http://localhost:8081/api/activities
$headers = @{ 'X-User-Id' = '1001'; 'X-Request-Id' = 'demo-request-1001' }
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/activities/1/reservations -Headers $headers
Invoke-RestMethod -Uri http://localhost:8081/api/orders/by-request/demo-request-1001 -Headers @{ 'X-User-Id' = '1001' }
```

Day 2 Redis 并发验证运行 `mvn -Dtest=FlashSaleReservationIT test`。Day 3/4 的真实链路验证运行 `mvn -Dtest=FlashSaleOrderIT,MessageReliabilityIT test`：前者验证异步成单与重复消费幂等，后者故障注入 MySQL 库存拒绝，验证三次重试、死信、失败记录和 Redis 资格补偿。

RabbitMQ 管理台默认位于 `http://localhost:15673`。教学与每日进度位于本地 `docs/`，该目录不会进入 Git。
