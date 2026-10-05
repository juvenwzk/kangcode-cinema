package com.kangcode.server.service.impl;

import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.StationUpdateRequest;
import com.kangcode.pojo.entity.Station;
import com.kangcode.server.mapper.StationMapper;
import com.kangcode.server.service.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
@Service
@RequiredArgsConstructor
@Slf4j
public class StationServiceImpl implements StationService {


    private final StationMapper stationMapper;

    @Override
    public void updateStation(Long stationId, StationUpdateRequest request) {
        Station station=stationMapper.selectById(stationId);
        if(station==null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        station.setStationName(request.getStationName());

        if(StringUtils.hasText(request.getCity())){
            station.setCity(request.getCity());
        }

        stationMapper.updateById(station);
        log.info("车站已更新 id={} name={} city={}", stationId, station.getStationName(), station.getCity());


    }
}
