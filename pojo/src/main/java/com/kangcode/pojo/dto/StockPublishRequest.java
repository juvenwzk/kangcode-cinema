package com.kangcode.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 放票 / 库存预热请求（管理端）
 */
@Data
public class StockPublishRequest {

    @NotNull(message = "场次不能为空")
    private Long screeningId;

    @NotNull(message = "放映日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate showDate;

    @NotBlank(message = "票种不能为空")
    private String ticketType;

    /** 本次放票数量（增量），至少 1 张 */
    @NotNull(message = "放票数量不能为空")
    @Min(value = 1, message = "放票数量至少 1 张")
    private Integer count;
}