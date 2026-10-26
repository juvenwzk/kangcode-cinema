package com.kangcode.server.controller;

import com.kangcode.common.core.Result;
import com.kangcode.framework.security.UserContext;
import com.kangcode.pojo.dto.TicketBuyRequest;
import com.kangcode.pojo.vo.TicketBuyVO;
import com.kangcode.server.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;

/**
 * 余票/购票接口
 */
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/remain")
    public Result<Integer> remain(@RequestParam Long screeningId,
                                  @RequestParam String showDate,
                                  @RequestParam String ticketType) {
        return Result.ok(ticketService.queryRemain(screeningId, LocalDate.parse(showDate), ticketType));
    }

    @PostMapping("/buy")
    public Result<TicketBuyVO> buy(@Valid @RequestBody TicketBuyRequest request) {
        return Result.ok(ticketService.buy(request, UserContext.userId()));
    }
}
