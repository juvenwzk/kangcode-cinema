package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.entity.Train;
import com.kangcode.pojo.vo.TrainVO;
import com.kangcode.server.mapper.StationMapper;
import com.kangcode.server.mapper.TrainMapper;
import com.kangcode.server.mapper.TrainStationMapper;
import com.kangcode.server.service.TrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

        // TODO(P2 任务 2-1)：组装经停站列表（train_station + station）→ List<TrainStationVO>
        //   1. 用 trainStationMapper 查 train_station，条件 trainId = ?，按 stationNo 升序；
        //   2. 收集所有 stationId，一次性用 stationMapper.selectBatchIds(ids) 查车站，
        //      做成 Map<Long, Station>（不要在 for 循环里查库，会 N+1）；
        //   3. 组装 List<TrainStationVO>：stationId / stationCode / stationName / stationNo /
        //      arriveTime / leaveTime / stopMinutes / distanceKm，然后 vo.setStations(list)；
        //   4. 注意本方法带 @Cacheable，返回对象会被序列化进 Redis，VO 必须有无参构造。
        //   验收：GET /api/train/1 的 data.stations 是数组且长度 > 0；连调两次，第二次走缓存。

        return vo;
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
