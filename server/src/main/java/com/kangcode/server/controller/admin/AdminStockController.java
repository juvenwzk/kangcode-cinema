package com.kangcode.server.controller.admin;

import com.kangcode.common.core.Result;
import com.kangcode.pojo.dto.StockPriceRequest;
import com.kangcode.pojo.dto.StockPublishRequest;
import com.kangcode.pojo.vo.StockVO;
import com.kangcode.server.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * 管理端 · 票池与库存
 */
@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;

    /** 放票（增）：同时写 DB 库存行、预热 Redis 余票、登记布隆过滤器 */
    @PostMapping("/publish")
    public Result<Integer> publish(@Valid @RequestBody StockPublishRequest request) {
        return Result.ok(stockService.publish(request));
    }

    /** 查：某场次某天（不传日期则查今天及以后）的票种库存 */
    @GetMapping("/list")
    public Result<List<StockVO>> list(@RequestParam Long screeningId,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate showDate) {
        return Result.ok(stockService.listStock(screeningId, showDate));
    }

    /** 改：调整票价（放票时票价先记 0，真正定价走这里） */
    @PutMapping("/{stockId}/price")
    public Result<Void> updatePrice(@PathVariable Long stockId,
                                    @Valid @RequestBody StockPriceRequest request) {
        stockService.updatePrice(stockId, request.getPrice());
        return Result.ok();
    }
}
