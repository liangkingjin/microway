-- 秒杀Lua脚本
-- 用于原子性的库存扣减和一人一单校验
--
-- KEYS[1]: 库存key (格式: stock:scenic:{景点ID}:{游玩日期})
-- KEYS[2]: 用户订单Set key (格式: order:scenic:{景点ID}:{游玩日期})
-- ARGV[1]: 购买数量
-- ARGV[2]: 用户ID
--
-- 返回值说明：
--   1   : 秒杀成功
--  -1   : 库存不足
--  -2   : 用户已购买过该景点门票（一人一单）

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local quantity = tonumber(ARGV[1])
local userId = ARGV[2]

-- 1.检查库存是否充足
local stock = tonumber(redis.call('GET', stockKey))
if not stock or stock < quantity then
    return -1  -- 库存不足
end

-- 2.检查用户是否已下单 (一人一单)
-- 使用SISMEMBER检查用户ID是否在Set集合中
local isExist = redis.call('SISMEMBER', orderKey, userId)
if isExist == 1 then
    return -2  -- 用户已下单
end

-- 3.扣减库存
local remainStock = stock - quantity
redis.call('SET', stockKey, remainStock)

-- 4.记录用户订单到Set集合
-- 使用SADD将用户ID添加到Set，实现一人一单
redis.call('SADD', orderKey, userId)

return 1  -- 秒杀成功
