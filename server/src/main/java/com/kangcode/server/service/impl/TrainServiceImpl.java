package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.TrainUpdateRequest;
import com.kangcode.pojo.entity.Station;
import com.kangcode.pojo.entity.Train;
import com.kangcode.pojo.entity.TrainStation;
import com.kangcode.pojo.vo.TrainStationVO;
import com.kangcode.pojo.vo.TrainVO;
import com.kangcode.server.mapper.StationMapper;
import com.kangcode.server.mapper.TrainMapper;
import com.kangcode.server.mapper.TrainStationMapper;
import com.kangcode.server.service.TrainService;
import lombok.RequiredArgsConstructor;
import org.apache.el.lang.FunctionMapperImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 车次服务实现
 */
@Service
@RequiredArgsConstructor
public class TrainServiceImpl implements TrainService {

    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;
    private final StationMapper stationMapper;

    @Override
    public IPage<TrainVO> pageTrain(int page, int size, String keyword) {
        LambdaQueryWrapper<Train> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), q -> q
                        .like(Train::getTrainNo, keyword)
                        .or()
                        .like(Train::getTrainName, keyword))
                .orderByAsc(Train::getTrainNo);

        IPage<Train> entityPage = trainMapper.selectPage(new Page<>(page, size), wrapper);

        // entity -> VO：不把数据库实体直接暴露给前端
        IPage<TrainVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Cacheable(value = "train", key = "#trainId")
    public TrainVO getTrainDetail(Long trainId) {
        Train train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        TrainVO vo = toVO(train);
        vo.setStations(listStations(trainId));
        return vo;
    }
    /**
     * 改车次后清掉该车次的详情缓存。
     * key 的写法必须和 getTrainDetail 上的 @Cacheable 完全一致，否则删的不是同一个 key。
     */
    @Override
    @CacheEvict(value = "train", key = "#trainId")
    public void updateTrain(Long trainId, TrainUpdateRequest request) {
        Train train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (StringUtils.hasText(request.getTrainName())) {
            train.setTrainName(request.getTrainName());
        }
        if (request.getStatus() != null) {
            train.setStatus(request.getStatus());
        }
        train.setUpdateTime(LocalDateTime.now());
        trainMapper.updateById(train);
    }

    /**
     * 组装经停站列表（任务 2-1，已实现）：train_station 按 stationNo 升序 + 批量查 station 回填站名/电报码。
     * 先把 stationId 收集起来一次性 selectBatchIds，避免 N+1 查询。
     */
    private List<TrainStationVO> listStations(Long trainId){
        //   1. 用 trainStationMapper 查 train_station，条件 trainId = ?，按 stationNo 升序；
        List<TrainStation> trainStations=trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStation>()
                        .eq(TrainStation::getTrainId,trainId)
                        .orderByAsc(TrainStation::getStationNo)
        );

        if(trainStations.isEmpty()){
            return Collections.emptyList();
        }
        //   2. 收集所有 stationId，一次性用 stationMapper.selectBatchIds(ids) 查车站，
        List<Long> stationIds=trainStations.stream()
                .map(TrainStation::getStationId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Station> stationMap=stationMapper.selectBatchIds(stationIds)
                .stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));

        return trainStations.stream().map(ts -> {
            Station station = stationMap.get(ts.getStationId());
            TrainStationVO item = new TrainStationVO();
            item.setStationId(ts.getStationId());
            item.setStationCode(station == null ? null : station.getStationCode());
            item.setStationName(station == null ? null : station.getStationName());
            item.setStationNo(ts.getStationNo());
            item.setArriveTime(ts.getArriveTime());
            item.setLeaveTime(ts.getLeaveTime());
            item.setStopMinutes(ts.getStopMinutes());
            item.setDistanceKm(ts.getDistanceKm());
            return item;
        }).collect(Collectors.toList());
    }


    /** 实体转 VO */
    private TrainVO toVO(Train train) {
        return TrainVO.builder()
                .id(train.getId())
                .trainNo(train.getTrainNo())
                .trainName(train.getTrainName())
                .startStationId(train.getStartStationId())
                .endStationId(train.getEndStationId())
                .startTime(train.getStartTime())
                .endTime(train.getEndTime())
                .status(train.getStatus())
                .build();
    }
}
