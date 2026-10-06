package com.geocommunity;

import com.geocommunity.common.result.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoCommunityApplicationTests {

    @Test
    void resultOk_shouldReturn200() {
        Result<String> r = Result.ok("hello");
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMessage());
        assertEquals("hello", r.getData());
    }

    @Test
    void resultFail_shouldReturnCorrectCode() {
        Result<Void> r = Result.fail(1003, "帖子不存在");
        assertEquals(1003, r.getCode());
        assertEquals("帖子不存在", r.getMessage());
    }
}
