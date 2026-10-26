package com.kangcode.server.controller;

import com.kangcode.common.core.Result;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.framework.security.UserContext;
import com.kangcode.pojo.dto.CinemaUpdateRequest;
import com.kangcode.pojo.dto.StockPublishRequest;
import com.kangcode.pojo.dto.ScreeningUpdateRequest;
import com.kangcode.server.service.CinemaService;
import com.kangcode.server.service.StockService;
import com.kangcode.server.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 管理端接口
 *
 * <p>准备工程只提供 ping，用于验证「普通用户访问管理接口返回 403」这条拦截链路。
 * 后续放票、场次/影院维护等管理接口由你自己在这里新增。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StockService stockService;
    private final ScreeningService screeningService;
    private final CinemaService cinemaService;


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

    @PutMapping("/screening/{screeningId}")
    public Result<Void> updateScreening(@PathVariable Long screeningId,
                                    @Valid @RequestBody ScreeningUpdateRequest request){
        if(!UserContext.isAdmin()){
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        screeningService.updateScreening(screeningId,request);
        return Result.ok();
    }


    @PutMapping("/cinema/{cinemaId}")
    public Result<Void> updateCinema(@PathVariable Long cinemaId,
                                    @Valid @RequestBody CinemaUpdateRequest request){
        if(!UserContext.isAdmin()){
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        cinemaService.updateCinema(cinemaId,request);
        return Result.ok();
    }


}
