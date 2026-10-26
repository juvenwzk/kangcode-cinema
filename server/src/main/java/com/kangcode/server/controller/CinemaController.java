package com.kangcode.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.core.Result;
import com.kangcode.pojo.entity.Cinema;
import com.kangcode.pojo.vo.CinemaVO;
import com.kangcode.server.mapper.CinemaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 影院接口
 */
@RestController
@RequestMapping("/api/cinema")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaMapper cinemaMapper;

    @GetMapping("/list")
    @Cacheable(value = "cinema", key = "'list'")
    public Result<List<CinemaVO>> list() {
        List<Cinema> halls = cinemaMapper.selectList(new LambdaQueryWrapper<Cinema>()
                .eq(Cinema::getStatus, 1)
                .orderByAsc(Cinema::getCity));

        // entity -> VO：不把数据库实体直接暴露给前端
        List<CinemaVO> vos = halls.stream()
                .map(s -> CinemaVO.builder()
                        .id(s.getId())
                        .cinemaCode(s.getCinemaCode())
                        .cinemaName(s.getCinemaName())
                        .city(s.getCity())
                        .build())
                .collect(Collectors.toList());
        return Result.ok(vos);
    }
}
