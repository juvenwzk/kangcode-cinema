package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.constant.TicketCacheConstant;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.StockPublishRequest;
import com.kangcode.pojo.entity.ScreeningSeat;
import com.kangcode.pojo.entity.Hall;
import com.kangcode.pojo.vo.StockVO;
import com.kangcode.server.mapper.ScreeningSeatMapper;
import com.kangcode.server.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {


    private final ScreeningSeatMapper screeningSeatMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RBloomFilter<String> screeningBloomFilter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(StockPublishRequest request) {
        LocalDate showDate=request.getShowDate();
        //放历史日期的票没有意义，直接挡掉
        if(showDate.isBefore(LocalDate.now())){
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        ScreeningSeat seat = screeningSeatMapper.selectOne(new LambdaQueryWrapper<ScreeningSeat>()
                .eq(ScreeningSeat::getScreeningId, request.getScreeningId())
                .eq(ScreeningSeat::getShowDate, showDate)
                .eq(ScreeningSeat::getTicketType, request.getTicketType()));

        int remain;//余票的票数
        if(seat==null){
            //第一次给这个【场次+日期+票种】放票：插入一条库存记录
            ScreeningSeat screeningSeat=new ScreeningSeat();
            screeningSeat.setScreeningId(request.getScreeningId());
            screeningSeat.setShowDate(showDate);
            screeningSeat.setTicketType(request.getTicketType());
            screeningSeat.setTotal(request.getCount());
            screeningSeat.setRemain(request.getCount());
            screeningSeat.setPrice(0L);            // 票价由管理端另行维护，这里先给 0
            screeningSeat.setVersion(0);
            screeningSeat.setStatus(1);
            screeningSeat.setUpdateTime(LocalDateTime.now());
            screeningSeatMapper.insert(screeningSeat);
            remain=request.getCount();
        }else{
            //已存在：总票数和余票一起加
            // 已存在：总票数和余票一起加
            seat.setTotal(seat.getTotal() + request.getCount());
            seat.setRemain(seat.getRemain() + request.getCount());
            seat.setUpdateTime(LocalDateTime.now());
            // 实体上有 @Version，这里走乐观锁（MybatisPlusConfig 已注册乐观锁插件）
            screeningSeatMapper.updateById(seat);
            remain = seat.getRemain();

        }

        //预热（写绝对值，而不是INCR）；重复执行结果一样，天然幂等
        //注意：这里必须用 ticketStockKey（余票缓存），不能用 ticketLockKey——
        //后者归 Redisson 分布式锁使用（Hash 结构），余票字符串写进去会导致购票时 WRONGTYPE。
        String key= RedisKeyConstant.ticketStockKey(
                request.getScreeningId(),showDate.toString(),request.getTicketType()
        );
        stringRedisTemplate.opsForValue()
                .set(key, String.valueOf(remain), TicketCacheConstant.STOCK_CACHE_MINUTES, TimeUnit.MINUTES);

        screeningBloomFilter.add(request.getScreeningId()+":"+showDate+":"+request.getTicketType());
        return remain;
    }

    /**
     * 管理端查看库存。
     * <p>用户端的 /seats 只返回「票种 + 票价 + 余票」，管理端还要知道 total（放了多少），
     * 所以单独一个 VO，不跟用户端共用。</p>
     */
    @Override
    public List<StockVO> listStock(Long screeningId, LocalDate showDate) {
        LambdaQueryWrapper<ScreeningSeat> wrapper = new LambdaQueryWrapper<ScreeningSeat>()
                .eq(ScreeningSeat::getScreeningId, screeningId);
        if (showDate != null) {
            wrapper.eq(ScreeningSeat::getShowDate, showDate);
        } else {
            // 不传日期就只看今天及以后，避免管理端被历史数据淹没
            wrapper.ge(ScreeningSeat::getShowDate, LocalDate.now());
        }
        wrapper.orderByAsc(ScreeningSeat::getShowDate).orderByAsc(ScreeningSeat::getPrice);

        return screeningSeatMapper.selectList(wrapper).stream()
                .map(s -> StockVO.builder()
                        .id(s.getId())
                        .screeningId(s.getScreeningId())
                        .showDate(s.getShowDate())
                        .ticketType(s.getTicketType())
                        .total(s.getTotal())
                        .remain(s.getRemain())
                        .price(s.getPrice())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 改票价（管理端）。
     * <p>放票时票价先写 0（因为放票只关心数量），真正定价走这里。
     * 票价只影响下单时的金额计算，不涉及库存，所以不需要动 Redis 余票缓存。</p>
     */
    @Override
    public void updatePrice(Long stockId, Long price) {
        ScreeningSeat seat = screeningSeatMapper.selectById(stockId);
        if (seat == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        seat.setPrice(price);
        seat.setUpdateTime(LocalDateTime.now());
        screeningSeatMapper.updateById(seat);
        log.info("票价已更新 stockId={} {} {} -> {} 分", stockId, seat.getShowDate(), seat.getTicketType(), price);
    }
}
