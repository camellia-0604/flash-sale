local stateKey = KEYS[1]
local buyersKey = KEYS[2]
local userId = ARGV[1]
local requestId = ARGV[2]

-- 只有当前用户仍绑定同一个请求时才能补偿，重复死信或旧请求不会多加库存。
local reservedRequest = redis.call('HGET', buyersKey, userId)
if reservedRequest == false or reservedRequest ~= requestId then
    return 0
end

-- 状态 Key 已过期时无法安全重建库存，保留待对账记录交给人工或后续任务处理。
if redis.call('EXISTS', stateKey) == 0 then
    return -1
end

redis.call('HDEL', buyersKey, userId)
redis.call('HINCRBY', stateKey, 'stock', 1)
return 1
