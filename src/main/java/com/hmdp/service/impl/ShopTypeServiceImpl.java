package com.hmdp.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_TYPE_SHOP_KEY;
/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByList() {


//        //1 从redis中查询缓存
//        List<String> shopTypeList = stringRedisTemplate.opsForList().range(CACHE_TYPE_SHOP_KEY, 0, 9);
//        //2 判断是否存在
//        if (!shopTypeList.isEmpty() && shopTypeList != null) {
//            //3 存在，直接返回
//            return Result.ok(JSONUtil.toList(shopTypeList.get(0),ShopType.class));
//        }
//
//        //4 不存在，查数据库排序
//        List<ShopType> shopTypes = query().orderByAsc("sort").list();
//
//        //5 不存在，返回错误
//        if (shopTypes == null || shopTypes.isEmpty()) {
//            return Result.fail("商品种类不存在！");
//        }
//        //6 存在，写入redis
////        for (ShopType shopType:shopTypes){
////            stringRedisTemplate.opsForList().rightPush(CACHE_TYPE_SHOP_KEY,JSONUtil.toJsonStr(shopType));
////        }
//        String shopTypesToString = JSONUtil.toJsonStr(shopTypes);
//        stringRedisTemplate.opsForList().leftPushAll(CACHE_TYPE_SHOP_KEY,shopTypesToString);
//
//        return Result.ok(shopTypes);


        // 获取redis缓存
        String shopTypeList = stringRedisTemplate.opsForValue().get(CACHE_TYPE_SHOP_KEY);
        if (StrUtil.isNotBlank(shopTypeList) && !shopTypeList.isEmpty()) {
            return Result.ok(JSONUtil.toList(shopTypeList,ShopType.class));
        }
        //不存在 查数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        if (shopTypes.isEmpty() || shopTypes.size() == 0){
            return Result.fail("数据不存在");
        }
        //存在 写入redis
        String shopTypesToString = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(CACHE_TYPE_SHOP_KEY,shopTypesToString);

        return Result.ok(shopTypes);
    }
}
