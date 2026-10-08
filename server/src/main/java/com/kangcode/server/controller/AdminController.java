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

/**
 * 管理端接口
 *
 * <p>准备工程只提供 ping，用于验证「普通用户访问管理接口返回 403」这条拦截链路。
 * 后续放票、车次/车站维护等管理接口由你自己在这里新增。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StockService stockService;
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

    @PostMapping("/stock/publish")
    public Result<Integer> publishStock(@Valid @RequestBody StockPublishRequest request){
        if(!UserContext.isAdmin()){
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.ok(stockService.publish(request));
    }

    @PutMapping("/train/{trainId}")
    public Result<Void> updateTrain(@PathVariable Long trainId,
                                    @Valid @RequestBody TrainUpdateRequest request){
        if(!UserContext.isAdmin()){
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        trainService.updateTrain(trainId,request);
        return Result.ok();
    }


    @PutMapping("/station/{stationId}")
    public Result<Void> updateStation(@PathVariable Long stationId,
                                    @Valid @RequestBody StationUpdateRequest request){
        if(!UserContext.isAdmin()){
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        stationService.updateStation(stationId,request);
        return Result.ok();
    }


}
