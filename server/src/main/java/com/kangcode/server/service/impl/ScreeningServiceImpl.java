package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.framework.oss.OssService;
import com.kangcode.pojo.dto.ScreeningCreateRequest;
import com.kangcode.pojo.dto.ScreeningUpdateRequest;
import com.kangcode.pojo.entity.Cinema;
import com.kangcode.pojo.entity.MovieOrder;
import com.kangcode.pojo.entity.Screening;
import com.kangcode.pojo.entity.ScreeningSeat;
import com.kangcode.pojo.entity.Hall;
import com.kangcode.pojo.vo.HallVO;
import com.kangcode.pojo.vo.ScreeningSeatVO;
import com.kangcode.pojo.vo.ScreeningVO;
import com.kangcode.server.mapper.CinemaMapper;
import com.kangcode.server.mapper.MovieOrderMapper;
import com.kangcode.server.mapper.ScreeningMapper;
import com.kangcode.server.mapper.ScreeningSeatMapper;
import com.kangcode.server.mapper.HallMapper;
import com.kangcode.server.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final MovieOrderMapper movieOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final OssService ossService;

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
        // posterUrl 三种语义：null = 不动；"" = 清空海报；有值 = 换新图
        if (request.getPosterUrl() != null) {
            String oldPoster = screening.getPosterUrl();
            screening.setPosterUrl(StringUtils.hasText(request.getPosterUrl()) ? request.getPosterUrl() : null);
            if (StringUtils.hasText(oldPoster) && !oldPoster.equals(screening.getPosterUrl())) {
                ossService.deleteByUrl(oldPoster);
            }
        }
        screening.setUpdateTime(LocalDateTime.now());
        screeningMapper.updateById(screening);
    }

    /**
     * 新增场次（管理端）。
     * <p>四道校验的顺序是刻意的：先查唯一键（最便宜的索引查询），再查外部引用，
     * 最后做跨字段的时间比较——这样绝大多数非法请求都在第一条 SQL 就被挡掉。</p>
     */
    @Override
    public Long createScreening(ScreeningCreateRequest request) {
        // 1. 场次号唯一（uk_screening_no）
        Long dup = screeningMapper.selectCount(new LambdaQueryWrapper<Screening>()
                .eq(Screening::getScreeningNo, request.getScreeningNo()));
        if (dup != null && dup > 0) {
            throw new BusinessException("场次号已存在：" + request.getScreeningNo());
        }

        // 2. 影院必须存在且未停用
        Cinema cinema = cinemaMapper.selectById(request.getCinemaId());
        if (cinema == null) {
            throw new BusinessException("影院不存在：" + request.getCinemaId());
        }
        if (cinema.getStatus() != null && cinema.getStatus() == 0) {
            throw new BusinessException("影院已停用，无法新增场次");
        }

        // 3. 影厅必须存在且属于该影院，否则会出现「A 影院的场次挂在 B 影院的厅」这种脏数据
        Hall hall = hallMapper.selectById(request.getHallId());
        if (hall == null) {
            throw new BusinessException("影厅不存在：" + request.getHallId());
        }
        if (!request.getCinemaId().equals(hall.getCinemaId())) {
            throw new BusinessException("影厅不属于所选影院");
        }

        // 4. 散场必须晚于开场：跨字段比较 Bean Validation 做不了，只能放业务层
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("散场时间必须晚于开场时间");
        }

        Screening screening = new Screening();
        screening.setScreeningNo(request.getScreeningNo());
        screening.setMovieName(request.getMovieName());
        screening.setPosterUrl(StringUtils.hasText(request.getPosterUrl()) ? request.getPosterUrl() : null);
        screening.setCinemaId(request.getCinemaId());
        screening.setHallId(request.getHallId());
        screening.setStartTime(request.getStartTime());
        screening.setEndTime(request.getEndTime());
        screening.setStatus(1);
        screening.setCreateTime(LocalDateTime.now());
        screening.setUpdateTime(LocalDateTime.now());
        screeningMapper.insert(screening);
        return screening.getId();
    }

    /**
     * 删除场次（管理端）。
     * <p>已有订单的场次一律不准删——订单是财务凭证，删了场次订单就变成查不到明细的孤儿单。
     * 这种场次只能改成 status=0（下架），前端不再展示，历史数据仍可查。</p>
     * <p>删干净要动三张表：库存行、影厅排期、场次本身，所以必须在一个事务里。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "screening", key = "#screeningId")
    public void deleteScreening(Long screeningId) {
        Screening screening = screeningMapper.selectById(screeningId);
        if (screening == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        Long orderCount = movieOrderMapper.selectCount(new LambdaQueryWrapper<MovieOrder>()
                .eq(MovieOrder::getScreeningId, screeningId));
        if (orderCount != null && orderCount > 0) {
            throw new BusinessException("该场次已有 " + orderCount + " 笔订单，不能删除，请改为下架（状态置 0）");
        }

        // 先找出库存行：删库之前要把对应的 Redis 余票 key 一起清掉，
        // 否则用户拿着旧场次 id 来查，缓存会告诉他「还有 200 张票」。
        List<ScreeningSeat> seats = screeningSeatMapper.selectList(new LambdaQueryWrapper<ScreeningSeat>()
                .eq(ScreeningSeat::getScreeningId, screeningId));
        for (ScreeningSeat seat : seats) {
            stringRedisTemplate.delete(RedisKeyConstant.ticketStockKey(
                    screeningId, seat.getShowDate().toString(), seat.getTicketType()));
        }
        // 注意：布隆过滤器删不掉元素（布隆的固有特性，没有 delete API），
        // 所以它在 Redis 里会残留旧 key —— 后果只是漏一次 DB 查询，不会误判成「有票」。

        screeningSeatMapper.delete(new LambdaQueryWrapper<ScreeningSeat>()
                .eq(ScreeningSeat::getScreeningId, screeningId));
        hallMapper.delete(new LambdaQueryWrapper<Hall>()
                .eq(Hall::getScreeningId, screeningId));
        screeningMapper.deleteById(screeningId);
        // 场次删了，海报也没人引用，顺手从 OSS 清掉
        ossService.deleteByUrl(screening.getPosterUrl());
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
                .posterUrl(screening.getPosterUrl())
                .cinemaId(screening.getCinemaId())
                .hallId(screening.getHallId())
                .startTime(screening.getStartTime())
                .endTime(screening.getEndTime())
                .status(screening.getStatus())
                .build();
    }
}
