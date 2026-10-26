package com.kangcode.server.service.impl;

import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.CinemaUpdateRequest;
import com.kangcode.pojo.entity.Cinema;
import com.kangcode.server.mapper.CinemaMapper;
import com.kangcode.server.service.CinemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 影院服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CinemaServiceImpl implements CinemaService {

    private final CinemaMapper cinemaMapper;

    /**
     * 改影院后清空整个 cinema 缓存。
     * 列表接口的 key 是 'list'，以后还会有别的 key，用 allEntries 最省心。
     */
    @Override
    @CacheEvict(value = "cinema", allEntries = true)
    public void updateCinema(Long cinemaId, CinemaUpdateRequest request) {
        Cinema cinema = cinemaMapper.selectById(cinemaId);
        if (cinema == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        cinema.setCinemaName(request.getCinemaName());
        if (StringUtils.hasText(request.getCity())) {
            cinema.setCity(request.getCity());
        }
        cinemaMapper.updateById(cinema);
        log.info("影院已更新 id={} name={} city={}", cinemaId, cinema.getCinemaName(), cinema.getCity());
    }
}