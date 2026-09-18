-- KEYS 使用相同哈希标签，未来 Redis Cluster 中也会落到同一槽位。
local stateKey = KEYS[1]
local buyersKey = KEYS[2]
local userId = ARGV[1]
local requestId = ARGV[2]

if redis.call('EXISTS', stateKey) == 0 then
    return 5
end

-- Redis TIME 避免不同应用实例的本机时钟偏差影响活动边界。
local redisTime = redis.call('TIME')
local now = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
local startAt = tonumber(redis.call('HGET', stateKey, 'startAt'))
local endAt = tonumber(redis.call('HGET', stateKey, 'endAt'))

if now < startAt then
    return 1
end
if now >= endAt then
    return 2
end

-- 先判断本人是否已经成功预扣，使重试在售罄后仍返回明确的重复结果。
if redis.call('HEXISTS', buyersKey, userId) == 1 then
    return 4
end

local stock = tonumber(redis.call('HGET', stateKey, 'stock'))
if stock == nil or stock <= 0 then
    return 3
end

redis.call('HINCRBY', stateKey, 'stock', -1)
redis.call('HSET', buyersKey, userId, requestId)
redis.call('PEXPIREAT', buyersKey, endAt + 86400000)
return 0
