package com.kangcode.server.task;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.pojo.entity.ScreeningSeat;
import com.kangcode.server.mapper.ScreeningSeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterWarmUp implements ApplicationRunner {
    private final ScreeningSeatMapper screeningSeatMapper;
    private final RBloomFilter<String> screeningBloomFilter;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<ScreeningSeat> seats=screeningSeatMapper.selectList(
                new LambdaQueryWrapper<ScreeningSeat>()
                .select(ScreeningSeat::getScreeningId,
                        ScreeningSeat::getShowDate,
                        ScreeningSeat::getTicketType
                        ));

        for (ScreeningSeat seat : seats) {
            // key 格式必须与 queryRemain 查询时完全一致：screeningId + ":" + date + ":" + ticketType
            screeningBloomFilter.add(seat.getScreeningId() + ":"
                    + seat.getShowDate() + ":" + seat.getTicketType());
        }
        log.info("布隆过滤器预热完成,登记{}个组合", seats.size());
    }
}
