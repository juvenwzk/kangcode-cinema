package com.kangcode.common.constant;

/**
 * Redis Key 常量与生成规则
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 余票库存：screening:stock:{screeningId}:{showDate}:{ticketType} */
    public static final String TICKET_STOCK_PREFIX = "screening:stock:";

    /** 分布式锁：screening:lock:{screeningId}:{showDate}:{ticketType} */
    public static final String TICKET_LOCK_PREFIX = "screening:lock:";

    /** 用户 Token：user:token:{userId} */
    public static final String USER_TOKEN_PREFIX = "user:token:";

    /** 订单号每日序列：order:seq:{yyyyMMdd} */
    public static final String ORDER_SEQ_PREFIX = "order:seq:";
    //布隆过滤器：order:bloom:screening(整个过滤器就是这一个key的bit串)
    public static final String SCREENING_BLOOM_KEY="order:bloom:screening";

    /** 幂等键：order:dup:{userId}:{screeningId}:{showDate} */
    public static final String ORDER_DUP_PREFIX = "order:dup:";

    public static String ticketStockKey(Long screeningId, String showDate, String ticketType) {
        return TICKET_STOCK_PREFIX + screeningId + ":" + showDate + ":" + ticketType;
    }

    public static String ticketLockKey(Long screeningId, String showDate, String ticketType) {
        return TICKET_LOCK_PREFIX + screeningId + ":" + showDate + ":" + ticketType;
    }

    public static String userTokenKey(Long userId) {
        return USER_TOKEN_PREFIX + userId;
    }

    /** 下单幂等键：order:dup:{userId}:{screeningId}:{showDate} */
    public static String orderDupKey(Long userId, Long screeningId, String showDate) {
        return ORDER_DUP_PREFIX + userId + ":" + screeningId + ":" + showDate;
    }

    public static String orderSeqKey(String day) {
        return ORDER_SEQ_PREFIX + day;

    }
}
