package com.hmdp.service.impl;/*
  @ Author : 杜亚鹏
  @ gmail : D07111211U@gmail.com  
*/

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class IVoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucheOrderHandler());
    }

    private class VoucheOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        //获取锁对象
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();
        if (!isLock) {
            return;
        }
        try {

            proxy.createVoucherOrder(voucherOrder);

        } finally {
            lock.unlock();
        }
    }

    private  IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {

        Long userId = UserHolder.getUser().getId();

        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2.判断返回值是否为0
        int r = result.intValue();
        if(r != 0) {
            //2.1不为0 代表没有购买资格
            return Result.fail( r == 1 ? "库存不足" : "请勿重复下单");
        }

        //2.2为0，有购买资格，把下单信息保存到阻塞队列

        // 保存到阻塞队列
        // 3.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 3.1 订单ID
        long orderId = redisIdWorker.nextID("order");
        voucherOrder.setId(orderId);
        // 3.2 用户ID
        voucherOrder.setUserId(userId);
        // 3.3 优惠券ID
        voucherOrder.setVoucherId(voucherId);
        // 3.4 添加到阻塞队列
        orderTasks.add(voucherOrder);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //返回订单信息

        return  Result.ok(orderId);
    }

/*    // 1.查询优惠券信息
    SeckillVoucher voucher = iSeckillVoucherService.getById(voucherID);
    // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
        //尚未开始
        return Result.fail("秒杀尚未开始");
    }
    // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
        return Result.fail("秒杀已经结束");
    }
    // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
        return Result.fail("库存不足");
    }
    Long userId = UserHolder.getUser().getId();

    //获取锁对象
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"order:"+userId);
    RLock lock = redissonClient.getLock("lock:order:" + userId);

    boolean isLock= lock.tryLock();
        if (!isLock) {
        return Result.fail("点击过于频繁");
    }
        try {
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createVoucherOrder(voucherID);

    }finally {
        lock.unlock();
    }*/


    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5 实现一人一单
        // 5.1 查询订单
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            return;
        }
        // 5.扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)
                .update();
        // 5.1创建失败
        if (!success) {
            return;
        }

        // 7.创建订单
        save(voucherOrder);

    }
}


