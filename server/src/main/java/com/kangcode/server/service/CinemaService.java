package com.kangcode.server.service;

import com.kangcode.pojo.dto.CinemaCreateRequest;
import com.kangcode.pojo.dto.CinemaUpdateRequest;
import com.kangcode.pojo.vo.CinemaVO;
import com.kangcode.pojo.vo.HallOptionVO;

import java.util.List;

/**
 * 影院服务
 */
public interface CinemaService {

    /** 用户端影院列表（只含 status=1，走 Redis 缓存） */
    List<CinemaVO> listCinemas();

    /** 管理端影院列表（含已停用的；不缓存，管理端要看到改完的实时结果） */
    List<CinemaVO> listAllForAdmin(String keyword);

    /**
     * 修改影院（管理端）：实现类上带 @CacheEvict(allEntries = true)，改完清空 cinema 缓存
     */
    void updateCinema(Long cinemaId, CinemaUpdateRequest request);

    /** 新增影院（管理端），返回新影院 id */
    Long createCinema(CinemaCreateRequest request);

    /** 删除影院（管理端）。影院下还有场次时拒绝删除，避免留下孤儿数据 */
    void deleteCinema(Long cinemaId);

    /** 某影院下的影厅列表（新增场次时选厅用） */
    List<HallOptionVO> listHalls(Long cinemaId);
}
