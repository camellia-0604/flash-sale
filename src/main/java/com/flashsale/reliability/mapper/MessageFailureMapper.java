package com.flashsale.reliability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.reliability.entity.MessageFailure;
import org.apache.ibatis.annotations.Mapper;

/** 消息失败记录数据入口。 */
@Mapper
public interface MessageFailureMapper extends BaseMapper<MessageFailure> {
}
