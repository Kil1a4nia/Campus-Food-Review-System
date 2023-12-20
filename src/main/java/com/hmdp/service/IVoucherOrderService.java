package com.hmdp.service;/*
  @ Author : 杜亚鹏
  @ gmail : D07111211U@gmail.com  
*/

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {
        Result seckillVoucher(Long voucherID);

        Result createVoucherOrder(Long voucherID);

}
