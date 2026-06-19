package com.property.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.entity.PaymentMethod;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMethodMapper extends BaseMapper<PaymentMethod> {
}
