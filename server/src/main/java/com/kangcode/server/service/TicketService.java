package com.kangcode.server.service;

import com.kangcode.pojo.dto.TicketBuyRequest;
import com.kangcode.pojo.vo.TicketBuyVO;

import java.time.LocalDate;

/**
 * 余票/购票服务
 */
public interface TicketService {

    /**
     * 余票查询：Redis 缓存优先（Spring Cache + 手写缓存），兜底查库回填
     */
    Integer queryRemain(Long trainId, LocalDate travelDate, String seatType);

    /**
     * 购票：分布式锁 + Redis 原子预扣 -> 发送下单消息 -> 异步落库
     */
    TicketBuyVO buy(TicketBuyRequest request, Long userId);
}
