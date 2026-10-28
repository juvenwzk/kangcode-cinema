package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 影厅下拉选项（管理端新增场次时选厅用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallOptionVO {

    private Long id;

    private Long cinemaId;

    /** 影厅编号，如 1 号厅 */
    private Integer hallNo;
}
