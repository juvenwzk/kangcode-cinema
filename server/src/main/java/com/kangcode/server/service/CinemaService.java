package com.kangcode.server.service;

import com.kangcode.pojo.dto.CinemaUpdateRequest;

/**
 * 影院服务
 */
public interface CinemaService {

    /**
     * 修改影院（管理端）：实现类上带 @CacheEvict(allEntries = true)，改完清空 cinema 缓存
     */
    void updateCinema(Long cinemaId, CinemaUpdateRequest request);
}