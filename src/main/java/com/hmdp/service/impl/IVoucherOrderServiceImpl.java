package com.hmdp.service.impl;/*
  @ Author : 杜亚鹏
  @ gmail : D07111211U@gmail.com  
*/

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class IVoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherID) {
        // 1.查询优惠券信息
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
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherID);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherID) {
        // 5 实现一人一单
        // 5.1 查询订单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherID).count();
        if (count > 0) {
            return Result.fail("达到购买上限！");
        }
        // 5.扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherID)
                .gt("stock",0)
                .update();
        // 5.1创建失败
        if (!success) {
            return Result.fail("库存不足");
        }
        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1 订单ID
        long orderID = redisIdWorker.nextID("order");
        voucherOrder.setId(orderID);
        // 6.2 用户ID
        voucherOrder.setUserId(userId);
        // 6.3 优惠券ID
        voucherOrder.setVoucherId(voucherID);
        // 7.返回订单ID
        save(voucherOrder);

        return Result.ok(orderID);
    }
}


