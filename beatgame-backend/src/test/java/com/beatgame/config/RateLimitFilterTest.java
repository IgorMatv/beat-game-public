package com.beatgame.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void roomInfoGet_isRateLimitedAfterCapacityExceeded() throws ServletException, IOException {
        for (int i = 0; i < 30; i++) {
            assertThat(getStatus("/api/rooms/ABC123")).isNotEqualTo(429);
        }
        assertThat(getStatus("/api/rooms/ABC123")).isEqualTo(429);
    }

    @Test
    void roomPlayersGet_isRateLimitedAfterCapacityExceeded() throws ServletException, IOException {
        for (int i = 0; i < 30; i++) {
            assertThat(getStatus("/api/rooms/ABC123/players")).isNotEqualTo(429);
        }
        assertThat(getStatus("/api/rooms/ABC123/players")).isEqualTo(429);
    }

    @Test
    void roomReadBuckets_areIndependentPerIp() throws ServletException, IOException {
        for (int i = 0; i < 30; i++) {
            getStatus("/api/rooms/ABC123", "10.0.0.1");
        }
        assertThat(getStatus("/api/rooms/ABC123", "10.0.0.1")).isEqualTo(429);
        assertThat(getStatus("/api/rooms/ABC123", "10.0.0.2")).isNotEqualTo(429);
    }

    @Test
    void unrelatedGet_isNeverRateLimited() throws ServletException, IOException {
        for (int i = 0; i < 40; i++) {
            assertThat(getStatus("/api/genres")).isNotEqualTo(429);
        }
    }

    private int getStatus(String uri) throws ServletException, IOException {
        return getStatus(uri, "203.0.113.1");
    }

    private int getStatus(String uri, String remoteAddr) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
