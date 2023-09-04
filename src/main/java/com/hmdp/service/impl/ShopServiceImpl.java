package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    public Shop queryWithPassThrough(Long id) {

        String key = CACHE_SHOP_KEY + id;
        //1/从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在, 命中数据
        if (StrUtil.isNotBlank(shopJson)) {//此非空方法判断（如换行符。空白都是true）
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //3.命中空值，不为null，就是空值（空字符串）
        if (shopJson != null) {
            return null;
        }

        //4 实现缓存重建
        //4.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {

            boolean isLock = tryLock(lockKey);
            //4.2判断是否获取成功
            if (!isLock) {
                //4.3失败，休眠重试
                Thread.sleep(50);
                return queryWithPassThrough(id);
            }

            //模拟正常项目的延迟
            Thread.sleep(200);

            //获取锁成功后，应该再次检测redis缓存是否存在
            //接1，2，3

            //4.4 成功，根据id查询数据库
            shop = getById(id);
            //5.不存在，返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {//抛出打断异常
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(lockKey);
        }

        //7.返回
        return shop;
    }

    //获取锁，互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //拆箱的给出中可能查询空指针
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


    //主动更新策略，先修改数据库，再删除缓存
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            Result.fail("店铺id不能为空");
        }
        //1.更新数据
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

}


