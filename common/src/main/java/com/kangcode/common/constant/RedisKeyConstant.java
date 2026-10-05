package com.kangcode.common.constant;

/**
 * Redis Key 常量与生成规则
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 余票库存：ticket:stock:{trainId}:{travelDate}:{seatType} */
    public static final String TICKET_STOCK_PREFIX = "ticket:stock:";

    /** 分布式锁：ticket:lock:{trainId}:{travelDate}:{seatType} */
    public static final String TICKET_LOCK_PREFIX = "ticket:lock:";

    /** 用户 Token：user:token:{userId} */
    public static final String USER_TOKEN_PREFIX = "user:token:";

    /** 订单号每日序列：order:seq:{yyyyMMdd} */
    public static final String ORDER_SEQ_PREFIX = "order:seq:";

    /** 幂等键：order:dup:{userId}:{trainId}:{travelDate} */
    public static final String ORDER_DUP_PREFIX = "order:dup:";

    public static String ticketStockKey(Long trainId, String travelDate, String seatType) {
        return TICKET_STOCK_PREFIX + trainId + ":" + travelDate + ":" + seatType;
    }

    public static String ticketLockKey(Long trainId, String travelDate, String seatType) {
        return TICKET_LOCK_PREFIX + trainId + ":" + travelDate + ":" + seatType;
    }

    public static String userTokenKey(Long userId) {
        return USER_TOKEN_PREFIX + userId;
    }
}
