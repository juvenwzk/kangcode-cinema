package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.framework.oss.OssService;
import com.kangcode.pojo.dto.CinemaCreateRequest;
import com.kangcode.pojo.dto.CinemaUpdateRequest;
import com.kangcode.pojo.entity.Cinema;
import com.kangcode.pojo.entity.Hall;
import com.kangcode.pojo.entity.Screening;
import com.kangcode.pojo.vo.CinemaVO;
import com.kangcode.pojo.vo.HallOptionVO;
import com.kangcode.server.mapper.CinemaMapper;
import com.kangcode.server.mapper.HallMapper;
import com.kangcode.server.mapper.ScreeningMapper;
import com.kangcode.server.service.CinemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 影院服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CinemaServiceImpl implements CinemaService {

    private final CinemaMapper cinemaMapper;
    private final ScreeningMapper screeningMapper;
    private final HallMapper hallMapper;
    private final OssService ossService;

    /**
     * 用户端列表：只查 status=1，结果进 Redis。
     * 这段逻辑原先直接写在 Controller 里（Controller 注 Mapper、自己转 VO），
     * 现在收进 Service：缓存注解和实体转 VO 只出现一次，Controller 只管协议。
     */
    @Override
    @Cacheable(value = "cinema", key = "'list'")
    public List<CinemaVO> listCinemas() {
        List<Cinema> cinemas = cinemaMapper.selectList(new LambdaQueryWrapper<Cinema>()
                .eq(Cinema::getStatus, 1)
                .orderByAsc(Cinema::getCity));
        return cinemas.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 管理端列表：含停用影院，且不加缓存。
     * 管理端刚点完「停用」就要看到状态变化，走缓存反而让人以为没生效。
     */
    @Override
    public List<CinemaVO> listAllForAdmin(String keyword) {
        LambdaQueryWrapper<Cinema> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), q -> q
                        .like(Cinema::getCinemaName, keyword)
                        .or()
                        .like(Cinema::getCinemaCode, keyword))
                .orderByAsc(Cinema::getId);
        return cinemaMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

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
        // logoUrl 的三种语义：null = 不动；"" = 清空；有值 = 换成新图
        if (request.getLogoUrl() != null) {
            String oldLogo = cinema.getLogoUrl();
            cinema.setLogoUrl(StringUtils.hasText(request.getLogoUrl()) ? request.getLogoUrl() : null);
            deleteOldLogo(oldLogo, cinema.getLogoUrl());
        }
        cinemaMapper.updateById(cinema);
        log.info("影院已更新 id={} name={} city={} logo={}",
                cinemaId, cinema.getCinemaName(), cinema.getCity(), cinema.getLogoUrl());
    }

    @Override
    @CacheEvict(value = "cinema", allEntries = true)
    public Long createCinema(CinemaCreateRequest request) {
        // cinema_code 上有唯一索引 uk_cinema_code，这里先查一次只为给出可读提示；
        // 并发兜底仍靠数据库唯一索引：两个请求同时通过检查时，后插入的那个会报 DuplicateKey
        Long exists = cinemaMapper.selectCount(new LambdaQueryWrapper<Cinema>()
                .eq(Cinema::getCinemaCode, request.getCinemaCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException("影院编码已存在：" + request.getCinemaCode());
        }

        Cinema cinema = new Cinema();
        cinema.setCinemaCode(request.getCinemaCode());
        cinema.setCinemaName(request.getCinemaName());
        cinema.setCity(request.getCity());
        cinema.setLogoUrl(StringUtils.hasText(request.getLogoUrl()) ? request.getLogoUrl() : null);
        cinema.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        cinemaMapper.insert(cinema);
        log.info("影院已新增 id={} code={} name={}", cinema.getId(), cinema.getCinemaCode(), cinema.getCinemaName());
        return cinema.getId();
    }

    @Override
    @CacheEvict(value = "cinema", allEntries = true)
    public void deleteCinema(Long cinemaId) {
        Cinema cinema = cinemaMapper.selectById(cinemaId);
        if (cinema == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        // 库里没有建外键，删除前必须自己检查引用，否则会留下挂不到影院的孤儿场次
        Long screeningCount = screeningMapper.selectCount(new LambdaQueryWrapper<Screening>()
                .eq(Screening::getCinemaId, cinemaId));
        if (screeningCount != null && screeningCount > 0) {
            throw new BusinessException("该影院下还有 " + screeningCount + " 个场次，请先删除场次");
        }
        cinemaMapper.deleteById(cinemaId);
        // 影院没了，它的 Logo 也就没人引用，顺手清掉，免得 OSS 里越攒越多孤儿文件
        ossService.deleteByUrl(cinema.getLogoUrl());
        log.info("影院已删除 id={} name={}", cinemaId, cinema.getCinemaName());
    }

    @Override
    public List<HallOptionVO> listHalls(Long cinemaId) {
        return hallMapper.selectList(new LambdaQueryWrapper<Hall>()
                        .eq(Hall::getCinemaId, cinemaId)
                        .orderByAsc(Hall::getHallNo))
                .stream()
                .map(h -> HallOptionVO.builder()
                        .id(h.getId())
                        .cinemaId(h.getCinemaId())
                        .hallNo(h.getHallNo())
                        .build())
                .collect(Collectors.toList());
    }

    /** 实体转 VO */
    private CinemaVO toVO(Cinema cinema) {
        return CinemaVO.builder()
                .id(cinema.getId())
                .cinemaCode(cinema.getCinemaCode())
                .cinemaName(cinema.getCinemaName())
                .city(cinema.getCity())
                .logoUrl(cinema.getLogoUrl())
                .status(cinema.getStatus())
                .build();
    }

    /**
     * 换图后删掉旧对象。
     * 只在新旧都有值且确实不同时才删：避免用户没改图却把图删了，
     * 也避免两行记录恰好引用同一张图时误删别人还在用的文件。
     */
    private void deleteOldLogo(String oldLogo, String newLogo) {
        if (StringUtils.hasText(oldLogo) && !oldLogo.equals(newLogo)) {
            ossService.deleteByUrl(oldLogo);
        }
    }
}
