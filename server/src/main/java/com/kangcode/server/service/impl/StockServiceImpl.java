package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.StockPublishRequest;
import com.kangcode.pojo.entity.TrainSeat;
import com.kangcode.pojo.entity.TrainStation;
import com.kangcode.server.mapper.TrainSeatMapper;
import com.kangcode.server.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {


    private final long STOCK_CACHE_MINUTES=30;

    private final TrainSeatMapper trainSeatMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(StockPublishRequest request) {
        LocalDate travelDate=request.getTravelDate();
        //放历史日期的票没有意义，直接挡掉
        if(travelDate.isBefore(LocalDate.now())){
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        TrainSeat seat = trainSeatMapper.selectOne(new LambdaQueryWrapper<TrainSeat>()
                .eq(TrainSeat::getTrainId, request.getTrainId())
                .eq(TrainSeat::getTravelDate, travelDate)
                .eq(TrainSeat::getSeatType, request.getSeatType()));

        int remain;//余票的票数
        if(seat==null){
            //第一次给这个【车次+日期+席别】放票：插入一条库存记录
            TrainSeat trainSeat=new TrainSeat();
            trainSeat.setTrainId(request.getTrainId());
            trainSeat.setTravelDate(travelDate);
            trainSeat.setSeatType(request.getSeatType());
            trainSeat.setTotal(request.getCount());
            trainSeat.setRemain(request.getCount());
            trainSeat.setPrice(0L);            // 票价由管理端另行维护，这里先给 0
            trainSeat.setVersion(0);
            trainSeat.setStatus(1);
            trainSeat.setUpdateTime(LocalDateTime.now());
            trainSeatMapper.insert(trainSeat);
            remain=request.getCount();
        }else{
            //已存在：总票数和余票一起加
            // 已存在：总票数和余票一起加
            seat.setTotal(seat.getTotal() + request.getCount());
            seat.setRemain(seat.getRemain() + request.getCount());
            seat.setUpdateTime(LocalDateTime.now());
            // 实体上有 @Version，这里走乐观锁（MybatisPlusConfig 已注册乐观锁插件）
            trainSeatMapper.updateById(seat);
            remain = seat.getRemain();

        }

        //预热（写绝对值，而不是INCR）；重复执行结果一样，天然幂等
        //注意：这里必须用 ticketStockKey（余票缓存），不能用 ticketLockKey——
        //后者归 Redisson 分布式锁使用（Hash 结构），余票字符串写进去会导致购票时 WRONGTYPE。
        String key= RedisKeyConstant.ticketStockKey(
                request.getTrainId(),travelDate.toString(),request.getSeatType()
        );
        stringRedisTemplate.opsForValue()
                .set(key, String.valueOf(remain), STOCK_CACHE_MINUTES, TimeUnit.MINUTES);
        return remain;
    }
}
