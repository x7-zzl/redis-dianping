package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.ConstantPool;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    //根据手机号 生成验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号(鉴于短信验证码要付费，需改为邮箱)
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //2.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.保存验证码到session
//        session.setAttribute(ConstantPool.VERIFICATION_CODE, code);

        //4.保存验证码到redis  //set key value ex 120
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送验证码成功，验证码：{}", code);

        return Result.ok();
    }

    /**
     * 登录
     *
     * @param loginForm 前端的用户输入的数据封装好放入loginForm中，传到后台，与数据进行比对
     * @param session   存储值写出常量的形式，为了规范，一般定义一个类，然后定义所有常量
     * @return
     */
    @Override
    public Result Login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {//2.格式错误
            return Result.fail("手机号格式错误");
        }
        //3.从redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if (cacheCode == null || !cacheCode.equals(code)) {
            //不一致，报错
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号查询用户
//        select * from tb_usr where phone=?  通过mybatis-plus通过的快捷方法
        User user = query().eq("phone", phone).one();

        //5.判断用户是否存在
        if (user == null) { //不存在
            //6.不存在，创建用户信息保存
            user = createUserWithPhone(phone);
        }

        //7.保存用户信息到redis中
        //7.1 随机生成token,作为登录令牌
        String token = UUID.randomUUID().toString();
        //把user中的所有属性拷贝到UserDTO中，返回值是UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //7.2 将user对象转为hashmap存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //7.3 存储
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.4设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.返回token
        return Result.ok(token);
    }

    //退出登录
    @Override
    public Result logout() {
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(ConstantPool.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        save(user);
        return user;
    }


}
