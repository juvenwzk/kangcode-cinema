package com.kangcode.server.service;

import com.kangcode.pojo.dto.StationUpdateRequest;

/**
 * 车站服务
 */
public interface StationService {

    /**
     * 修改车站（管理端）：实现类上带 @CacheEvict(allEntries = true)，改完清空 station 缓存
     */
    void updateStation(Long stationId, StationUpdateRequest request);
}