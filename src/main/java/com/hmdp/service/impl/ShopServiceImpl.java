package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    /**
     * 查询店铺
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {

        //解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if(shop == null){
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);

    }
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1 从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3 存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
        if (shopJson != null) {
            return null;
        }

        //4 不存在 获取互斥锁
        String  lockKey = "key:shop:"+id;
        Shop shop = null;
        try {
            Boolean islock = tryLock(lockKey);
            //4.1 获取是否成功
            if(!islock){
                //4.2 失败，则休眠并重试
                Thread.sleep(10);
                queryWithMutex(id);
            }

            //4.3 成功，根据id查数据库
            shop = getById(id);
            //5 不存在，返回错误
            if (shop == null) {
                //把null写入到redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }

            //6 存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }


        return shop;
    }
    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1 从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3 存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
        if (shopJson != null) {
            return null;
        }

        //4 不存在，根据id查数据库
        Shop shop = getById(id);
        //5 不存在，返回错误
        if (shop == null) {
            //把null写入到redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        //6 存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);

        return shop;
    }

    public Boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }
    @Override
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return  Result.ok();
    }


}
