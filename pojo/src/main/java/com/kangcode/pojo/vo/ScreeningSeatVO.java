package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场次票种（给前端选票用）：票种 + 单价 + 余票
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningSeatVO {

    /** 票种：普通票 / 学生票 / IMAX厅票 */
    private String ticketType;

    /** 单价（分） */
    private Long price;

    /** 余票 */
    private Integer remain;
}
