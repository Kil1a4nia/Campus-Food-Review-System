
-- 比较线程标识与锁中的表始是否一致

if(redis.call('get',KEYS[1]) == ARGV[1]) then
    return redis.call('DEL',KEYS[1])
end
return 0