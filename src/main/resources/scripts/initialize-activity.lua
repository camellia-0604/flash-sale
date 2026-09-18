-- 只在活动首次进入 Redis 时写入 MySQL 库存，应用重启不能把已经预扣的库存重置。
local stateKey = KEYS[1]
local startAt = ARGV[1]
local endAt = ARGV[2]
local stock = ARGV[3]
local expireAt = tonumber(endAt) + 86400000

if redis.call('EXISTS', stateKey) == 0 then
    redis.call('HSET', stateKey,
            'startAt', startAt,
            'endAt', endAt,
            'stock', stock)
    redis.call('PEXPIREAT', stateKey, expireAt)
    return 1
end

-- 时间配置可以随应用重启刷新，但已有 Redis 库存必须保留。
redis.call('HSET', stateKey, 'startAt', startAt, 'endAt', endAt)
redis.call('PEXPIREAT', stateKey, expireAt)
return 0
