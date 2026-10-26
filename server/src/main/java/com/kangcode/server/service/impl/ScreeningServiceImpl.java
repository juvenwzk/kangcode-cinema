package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.ScreeningUpdateRequest;
import com.kangcode.pojo.entity.Cinema;
import com.kangcode.pojo.entity.Screening;
import com.kangcode.pojo.entity.ScreeningSeat;
import com.kangcode.pojo.entity.Hall;
import com.kangcode.pojo.vo.HallVO;
import com.kangcode.pojo.vo.ScreeningSeatVO;
import com.kangcode.pojo.vo.ScreeningVO;
import com.kangcode.server.mapper.CinemaMapper;
import com.kangcode.server.mapper.ScreeningMapper;
import com.kangcode.server.mapper.ScreeningSeatMapper;
import com.kangcode.server.mapper.HallMapper;
import com.kangcode.server.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 场次服务实现
 */
@Service
@RequiredArgsConstructor
public class ScreeningServiceImpl implements ScreeningService {

    private final ScreeningMapper screeningMapper;
    private final HallMapper hallMapper;
    private final CinemaMapper cinemaMapper;
    private final ScreeningSeatMapper screeningSeatMapper;

    /**
     * 查询某场次某天的票种 / 票价 / 余票。
     * <p>前端选票需要票价和余票，原来只能查 remain，没有列表接口，所以补这一个只读方法。
     */
    @Override
    public List<ScreeningSeatVO> listSeats(Long screeningId, LocalDate showDate) {
        List<ScreeningSeat> seats = screeningSeatMapper.selectList(new LambdaQueryWrapper<ScreeningSeat>()
                .eq(ScreeningSeat::getScreeningId, screeningId)
                .eq(ScreeningSeat::getShowDate, showDate)
                .eq(ScreeningSeat::getStatus, 1)
                .orderByAsc(ScreeningSeat::getPrice));
        if (seats.isEmpty()) {
            return Collections.emptyList();
        }
        return seats.stream()
                .map(s -> ScreeningSeatVO.builder()
                        .ticketType(s.getTicketType())
                        .price(s.getPrice())
                        .remain(s.getRemain())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 某场次已放票的日期列表（升序）。
     */
    @Override
    public List<LocalDate> listShowDates(Long screeningId) {
        List<Object> objs = screeningSeatMapper.selectObjs(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScreeningSeat>()
                .select("DISTINCT show_date")
                .eq("screening_id", screeningId)
                .ge("show_date", LocalDate.now())
                .orderByAsc("show_date"));
        if (objs == null || objs.isEmpty()) {
            return Collections.emptyList();
        }
        return objs.stream()
                .filter(o -> o != null)
                .map(o -> o instanceof LocalDate ? (LocalDate) o : LocalDate.parse(String.valueOf(o)))
                .collect(Collectors.toList());
    }

    @Override
    public IPage<ScreeningVO> pageScreening(int page, int size, String keyword) {
        LambdaQueryWrapper<Screening> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), q -> q
                        .like(Screening::getScreeningNo, keyword)
                        .or()
                        .like(Screening::getMovieName, keyword))
                .orderByAsc(Screening::getScreeningNo);

        IPage<Screening> entityPage = screeningMapper.selectPage(new Page<>(page, size), wrapper);

        // entity -> VO：不把数据库实体直接暴露给前端
        IPage<ScreeningVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Cacheable(value = "screening", key = "#screeningId")
    public ScreeningVO getScreeningDetail(Long screeningId) {
        Screening screening = screeningMapper.selectById(screeningId);
        if (screening == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        ScreeningVO vo = toVO(screening);
        vo.setHalls(listHalls(screeningId));
        return vo;
    }
    /**
     * 改场次后清掉该场次的详情缓存。
     * key 的写法必须和 getScreeningDetail 上的 @Cacheable 完全一致，否则删的不是同一个 key。
     */
    @Override
    @CacheEvict(value = "screening", key = "#screeningId")
    public void updateScreening(Long screeningId, ScreeningUpdateRequest request) {
        Screening screening = screeningMapper.selectById(screeningId);
        if (screening == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (StringUtils.hasText(request.getMovieName())) {
            screening.setMovieName(request.getMovieName());
        }
        if (request.getStatus() != null) {
            screening.setStatus(request.getStatus());
        }
        screening.setUpdateTime(LocalDateTime.now());
        screeningMapper.updateById(screening);
    }

    /**
     * 组装影厅列表（任务 2-1，已实现）：hall 按 hallNo 升序 + 批量查 cinema 回填站名/电报码。
     * 先把 cinemaId 收集起来一次性 selectBatchIds，避免 N+1 查询。
     */
    private List<HallVO> listHalls(Long screeningId){
        //   1. 用 hallMapper 查 hall，条件 screeningId = ?，按 hallNo 升序；
        List<Hall> screeningHalls=hallMapper.selectList(
                new LambdaQueryWrapper<Hall>()
                        .eq(Hall::getScreeningId,screeningId)
                        .orderByAsc(Hall::getHallNo)
        );

        if(screeningHalls.isEmpty()){
            return Collections.emptyList();
        }
        //   2. 收集所有 cinemaId，一次性用 cinemaMapper.selectBatchIds(ids) 查影院，
        List<Long> cinemaIds=screeningHalls.stream()
                .map(Hall::getCinemaId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Cinema> cinemaMap=cinemaMapper.selectBatchIds(cinemaIds)
                .stream()
                .collect(Collectors.toMap(Cinema::getId, Function.identity()));

        return screeningHalls.stream().map(ts -> {
            Cinema cinema = cinemaMap.get(ts.getCinemaId());
            HallVO item = new HallVO();
            item.setCinemaId(ts.getCinemaId());
            item.setCinemaCode(cinema == null ? null : cinema.getCinemaCode());
            item.setCinemaName(cinema == null ? null : cinema.getCinemaName());
            item.setHallNo(ts.getHallNo());
            item.setOpenTime(ts.getOpenTime());
            item.setCloseTime(ts.getCloseTime());
            item.setCleanupMinutes(ts.getCleanupMinutes());
            item.setRowCount(ts.getRowCount());
            return item;
        }).collect(Collectors.toList());
    }


    /** 实体转 VO */
    private ScreeningVO toVO(Screening screening) {
        return ScreeningVO.builder()
                .id(screening.getId())
                .screeningNo(screening.getScreeningNo())
                .movieName(screening.getMovieName())
                .cinemaId(screening.getCinemaId())
                .hallId(screening.getHallId())
                .startTime(screening.getStartTime())
                .endTime(screening.getEndTime())
                .status(screening.getStatus())
                .build();
    }
}
