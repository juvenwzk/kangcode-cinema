package com.kangcode.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.core.Result;
import com.kangcode.pojo.entity.Station;
import com.kangcode.pojo.vo.StationVO;
import com.kangcode.server.mapper.StationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 车站接口
 */
@RestController
@RequestMapping("/api/station")
@RequiredArgsConstructor
public class StationController {

    private final StationMapper stationMapper;

    @GetMapping("/list")
    @Cacheable(value = "station", key = "'list'")
    public Result<List<StationVO>> list() {
        List<Station> stations = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .eq(Station::getStatus, 1)
                .orderByAsc(Station::getCity));

        // entity -> VO：不把数据库实体直接暴露给前端
        List<StationVO> vos = stations.stream()
                .map(s -> StationVO.builder()
                        .id(s.getId())
                        .stationCode(s.getStationCode())
                        .stationName(s.getStationName())
                        .city(s.getCity())
                        .build())
                .collect(Collectors.toList());
        return Result.ok(vos);
    }
}
