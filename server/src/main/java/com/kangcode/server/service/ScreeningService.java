package com.kangcode.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kangcode.pojo.dto.ScreeningCreateRequest;
import com.kangcode.pojo.dto.ScreeningUpdateRequest;
import com.kangcode.pojo.vo.ScreeningSeatVO;
import com.kangcode.pojo.vo.ScreeningVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 场次服务
 */
public interface ScreeningService {

    IPage<ScreeningVO> pageScreening(int page, int size, String keyword);

    ScreeningVO getScreeningDetail(Long screeningId);

    void updateScreening(Long screeningId, ScreeningUpdateRequest screeningUpdateRequest);

    /** 查询某场次某天的票种、票价、余票（给前端选票用） */
    List<ScreeningSeatVO> listSeats(Long screeningId, LocalDate showDate);

    /** 查询某场次有哪些日期已经放票（前端自动定位到有票的日期） */
    List<LocalDate> listShowDates(Long screeningId);

    /** 新增场次（管理端），返回新场次 id */
    Long createScreening(ScreeningCreateRequest request);

    /** 删除场次（管理端）：已有订单时拒绝；否则连带删除影厅排期与库存行 */
    void deleteScreening(Long screeningId);
}
