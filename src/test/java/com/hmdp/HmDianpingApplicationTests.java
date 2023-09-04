package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianpingApplicationTests {

    @Autowired
    private StringRedisTemplate redisTemplate;
    //生成全局唯一id
    @Resource
    private RedisIdWorker redisIdWorker;

    //线程池
    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void testRedisIdWorker() throws InterruptedException {
        //CountDownLatch：计数器，用来进行线程同步协作，等待所有线程完成
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id=" + id);
            }
            countDownLatch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }

        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("总耗时=" + (end - begin));

    }

}
