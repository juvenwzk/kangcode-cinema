package com.kangcode.server.service;

import com.kangcode.pojo.dto.StockPublishRequest;
import com.kangcode.pojo.vo.StockVO;

import java.time.LocalDate;
import java.util.List;

public interface StockService {

    int publish(StockPublishRequest request);

    /**
     * 管理端查看库存。
     *
     * @param screeningId 场次 id
     * @param showDate    放映日期；传 null 表示查该场次今天及以后的所有库存行
     */
    List<StockVO> listStock(Long screeningId, LocalDate showDate);

    /** 管理端改票价（单位：分） */
    void updatePrice(Long stockId, Long price);
}
