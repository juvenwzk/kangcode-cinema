package com.kangcode.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 库存行（管理端查看某场次某天的票种库存）
 * <p>比用户端的 {@link ScreeningSeatVO} 多了 total 和日期：
 * 管理端要判断「放了多少、还剩多少」，用户端只需要「还剩多少」。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockVO {

    private Long id;

    private Long screeningId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate showDate;

    /** 票种：普通票 / 学生票 / IMAX厅票 */
    private String ticketType;

    /** 总票数 */
    private Integer total;

    /** 余票 */
    private Integer remain;

    /** 单价（分） */
    private Long price;
}
