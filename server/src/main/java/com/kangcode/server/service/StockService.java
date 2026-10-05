package com.kangcode.server.service;

import com.kangcode.pojo.dto.StockPublishRequest;

/**
 * 库存服务：放票 / 预热
 */
public interface StockService {

    /**
     * 放票：DB 增加库存，并把最新余票预热到 Redis
     *
     * @return 放票后的余票
     */
    int publish(StockPublishRequest request);
}