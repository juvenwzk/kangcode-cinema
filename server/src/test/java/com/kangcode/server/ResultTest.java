package com.kangcode.server;

import com.kangcode.common.core.Result;
import com.kangcode.common.core.ResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 单元测试示例：纯 JUnit5，不启动 Spring 容器、不依赖中间件，可直接运行。
 */
class ResultTest {

    @Test
    void ok_returnsSuccessCodeAndData() {
        Result<String> result = Result.ok("hello");
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals("hello", result.getData());
    }

    @Test
    void fail_returnsCodeAndMessage() {
        Result<Void> result = Result.fail(ResultCode.BUSINESS_ERROR);
        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), result.getCode());
        assertEquals(ResultCode.BUSINESS_ERROR.getMessage(), result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void fail_customCodeAndMessage() {
        Result<Void> result = Result.fail(1001, "余票不足");
        assertEquals(1001, result.getCode().intValue());
        assertEquals("余票不足", result.getMessage());
    }
}
