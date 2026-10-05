package com.kangcode.server.controller;

import com.kangcode.common.core.Result;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.framework.security.UserContext;
import com.kangcode.pojo.dto.StationUpdateRequest;
import com.kangcode.pojo.dto.StockPublishRequest;
import com.kangcode.pojo.dto.TrainUpdateRequest;
import com.kangcode.server.service.StationService;
import com.kangcode.server.service.StockService;
import com.kangcode.server.service.TrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor                     // 新增：让 final 字段能构造注入
public class AdminController {

    private final StockService stockService;  // 新增
    private final TrainService trainService;
    private final StationService stationService;

    @GetMapping("/ping")
    public Result<String> ping() {
        // 拦截器已经把登录用户放进 UserContext，这里校验角色
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.ok("admin ok");
    }

    /**
     * 放票 / 库存预热：POST /api/admin/stock/publish
     */
    @PostMapping("/stock/publish")
    public Result<Integer> publishStock(@Valid @RequestBody StockPublishRequest request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.ok(stockService.publish(request));
    }

    @PutMapping("train/{trainId}")
    public Result updateTrain(@PathVariable Long trainId, @Valid @RequestBody TrainUpdateRequest request){

        if(!UserContext.isAdmin()){
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        trainService.updateTrain(trainId,request);

        return Result.ok();
    }

    @PutMapping("/station/{stationId}")
    public Result<Void> updateStation(@PathVariable Long stationId,
                                      @Valid @RequestBody StationUpdateRequest request) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        stationService.updateStation(stationId, request);
        return Result.ok();
    }




}