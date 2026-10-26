package com.kangcode.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kangcode.pojo.dto.ScreeningUpdateRequest;
import com.kangcode.pojo.vo.ScreeningVO;

/**
 * 场次服务
 */
public interface ScreeningService {

    IPage<ScreeningVO> pageScreening(int page, int size, String keyword);

    ScreeningVO getScreeningDetail(Long screeningId);

    void updateScreening(Long screeningId, ScreeningUpdateRequest screeningUpdateRequest);
}
