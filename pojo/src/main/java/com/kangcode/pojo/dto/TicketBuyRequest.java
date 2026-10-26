package com.kangcode.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 购票请求
 */
@Data
public class TicketBuyRequest {

    @NotNull(message = "车次不能为空")
    private Long trainId;

    @NotNull(message = "乘车日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate travelDate;

    @NotBlank(message = "席别不能为空")
    private String seatType;

    @NotNull(message = "购票数量不能为空")
    @Min(value = 1, message = "购票数量至少 1 张")
    @Max(value = 5, message = "单笔最多 5 张")
    private Integer ticketCount;

    /** 乘客姓名（可选，单乘客演示；不传则订单明细里用占位值） */
    private String passengerName;

    /** 乘客证件号（可选；不传则订单明细里留空） */
    private String idCardNo;
}
