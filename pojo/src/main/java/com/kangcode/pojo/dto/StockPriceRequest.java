package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 管理端改票价请求（单位：分）
 */
@Data
public class StockPriceRequest {

    @NotNull(message = "票价不能为空")
    @Min(value = 0, message = "票价不能为负数")
    private Long price;
}
