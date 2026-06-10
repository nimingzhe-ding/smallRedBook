local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

local stock = redis.call('GET', stockKey)
if (stock == false or tonumber(stock) <= 0) then
    return 1
end

if (redis.call('SISMEMBER', orderKey, userId) == 1) then
    return 2
end

redis.call('INCRBY', stockKey, -1)
redis.call('SADD', orderKey, userId)
return 0
