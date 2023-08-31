package com.hmdp.utils;

/**
 * @author nightmare
 * @date 2023/8/28 22:46
 */
//为了规范，一般信定义一个类，然后定义所有常量
public class ConstantPool {
    //写成常量的形式，避免魔法值的使用
    public static final String VERIFICATION_CODE = "verificationCode";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final String USER = "user";

    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;

    public static final String IMAGE_UPLOAD_DIR = "D:\\lesson\\nginx-1.18.0\\html\\hmdp\\imgs\\";
}
