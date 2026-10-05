package com.kangcode.server.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kangcode.common.core.Result;
import com.kangcode.pojo.vo.TrainVO;
import com.kangcode.server.service.TrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 车次接口
 */
@RestController
@RequestMapping("/api/train")
@RequiredArgsConstructor
public class TrainController {

    private final TrainService trainService;

    @GetMapping("/page")
    public Result<IPage<TrainVO>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword) {
        return Result.ok(trainService.pageTrain(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<TrainVO> detail(@PathVariable Long id) {
        return Result.ok(trainService.getTrainDetail(id));
    }
}
