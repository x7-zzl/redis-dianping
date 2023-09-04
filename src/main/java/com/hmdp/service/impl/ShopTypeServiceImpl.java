package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result listShopType() {
        //1.从redis中查询商铺列表缓存
        List<String> shopTypeJSons = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        //2.是否命中缓存
        if (!CollectionUtils.isEmpty(shopTypeJSons)) {
            //集合不为空即命中缓存
            //使用stream流将json集合转为bean集合
            List<ShopType> shopTypeList = shopTypeJSons.stream().
                    map(item -> JSONUtil.toBean(item, ShopType.class))
                    .sorted(Comparator.comparingInt(ShopType::getSort))
                    .collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }

        //3.为命中缓存则查询数据库
        List<ShopType> shopTypes = lambdaQuery().orderByAsc(ShopType::getSort).list();
        //4.数据库中是否有数据
        if (CollectionUtils.isEmpty(shopTypes)) {
            //不存在则缓存一个空集合
            stringRedisTemplate.opsForValue().
                    set(RedisConstants.CACHE_SHOP_TYPE_KEY, Collections.emptyList().toString(),
                            RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商品分类信息为空");
        }
        //数据库存在，写入缓存
        //使用stream将bean集合转为json集合
        List<String> shopTypeCache = shopTypes.stream()
                .sorted(Comparator.comparingInt(ShopType::getSort))
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        //设置过期时间
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY,shopTypeCache);
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY,RedisConstants.CACHE_SHOP_TYPE_TTL,TimeUnit.MINUTES);

        return Result.ok(shopTypes);
    }
}
