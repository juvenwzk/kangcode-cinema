package com.kangcode.common.constant;

/**
 * 余票缓存与购票链路的常量
 *
 * <p>原来散在 TicketServiceImpl / StockServiceImpl / StockReconcileTask 三处，
 * 其中 EMPTY_VALUE 曾被重复定义（靠注释约定两边一致），现在统一收敛到这里。</p>
 */
public final class TicketCacheConstant {

    private TicketCacheConstant() {
    }

    /** 空值标记：缓存里存这个字符串表示"DB 里也没有"，用来防穿透 */
    public static final String EMPTY_VALUE = "EMPTY";

    /** 正常余票缓存 TTL：10 分钟 */
    public static final long STOCK_TTL_SECONDS = 10 * 60;

    /** TTL 随机抖动上限：60 秒（防雪崩：同一批预热的 key 错峰过期） */
    public static final long TTL_JITTER_SECONDS = 60;

    /** 空值缓存 TTL：60 秒 */
    public static final long EMPTY_TTL_SECONDS = 60;

    /** 下单幂等键 TTL：30 秒（防手抖重复提交） */
    public static final long DUP_TTL_SECONDS = 30;

    /** 抢互斥锁最多等待 3 秒 */
    public static final long LOCK_WAIT_SECONDS = 3;

    /** 锁租约 10 秒：即使服务宕机，锁也会自动释放，不会死锁 */
    public static final long LOCK_LEASE_SECONDS = 10;

    /** 放票预热 / 对账写回的库存缓存 TTL：30 分钟 */
    public static final long STOCK_CACHE_MINUTES = 30;
}
