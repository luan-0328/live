package com.geocommunity.common.auth;

import com.geocommunity.common.utils.JwtUtil;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.controller.AuthController;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRegressionTest {
    private final JwtUtil jwt = new JwtUtil("audit-regression-key-at-least-32-bytes-long", 7);
    private SessionService sessions;
    private UserMapper users;
    private RefreshInterceptor refresh;
    private final AuthInterceptor auth = new AuthInterceptor();
    private String token;

    @BeforeEach void setup() {
        sessions = mock(SessionService.class); users = mock(UserMapper.class);
        refresh = new RefreshInterceptor();
        ReflectionTestUtils.setField(refresh, "jwtUtil", jwt);
        ReflectionTestUtils.setField(refresh, "sessionService", sessions);
        ReflectionTestUtils.setField(refresh, "userMapper", users);
        token = jwt.createToken(42L, "ROLE_USER");
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token); return request;
    }
    private void activeUser() {
        User user = new User(); user.setId(42L); user.setStatus(1); user.setRole("ROLE_USER");
        when(users.selectById(42L)).thenReturn(user); when(sessions.isActive(token)).thenReturn(true);
    }
    @Test void revokedTokenCannotAccessPrivateGet() throws Exception {
        var request = request("GET", "/notification/list"); var response = new MockHttpServletResponse();
        refresh.preHandle(request, response, new Object());
        assertNull(UserContext.get()); assertFalse(auth.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus()); verifyNoInteractions(users);
    }
    @Test void revokedTokenCanOnlyBrowsePublicPageAnonymously() throws Exception {
        var request = request("GET", "/post/123"); var response = new MockHttpServletResponse();
        refresh.preHandle(request, response, new Object());
        assertTrue(auth.preHandle(request, response, new Object())); assertNull(UserContext.get());
    }
    @Test void redisOutageDoesNotRestoreRevokedIdentity() throws Exception {
        when(sessions.isActive(token)).thenThrow(new IllegalStateException("offline"));
        var request = request("POST", "/post"); var response = new MockHttpServletResponse();
        refresh.preHandle(request, response, new Object());
        assertFalse(auth.preHandle(request, response, new Object())); assertNull(UserContext.get());
    }
    @Test void bannedUserCannotUseSessionThatStillExists() throws Exception {
        activeUser(); User banned = new User(); banned.setStatus(0);
        when(users.selectById(42L)).thenReturn(banned);
        var request = request("GET", "/user/favorites"); var response = new MockHttpServletResponse();
        refresh.preHandle(request, response, new Object());
        assertFalse(auth.preHandle(request, response, new Object()));
    }
    @Test void nearExpiryRotationDoesNotRejectCurrentPostOrSessionCheck() throws Exception {
        JwtUtil nearExpiry = mock(JwtUtil.class); Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("42");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
        when(nearExpiry.parseToken(token)).thenReturn(claims);
        when(nearExpiry.createToken(42L, "ROLE_USER")).thenReturn("new-token");
        when(nearExpiry.getExpireMs()).thenReturn(jwt.getExpireMs());
        ReflectionTestUtils.setField(refresh, "jwtUtil", nearExpiry); activeUser();
        when(sessions.rotate(eq(token), eq(42L), eq("new-token"), anyLong(), anyLong())).thenReturn("new-token");
        var request = request("POST", "/post/1/like"); var response = new MockHttpServletResponse();
        refresh.preHandle(request, response, new Object());
        assertTrue(auth.preHandle(request, response, new Object())); assertEquals("new-token", response.getHeader("X-Auth-Token"));
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        assertEquals(200, controller.checkSession(request).getCode());
    }
    @Test void logoutDoesNotRotateAndRevokesCurrentSession() {
        activeUser(); var request = request("POST", "/auth/logout"); var response = new MockHttpServletResponse();
        refresh.preHandle(request, response, new Object());
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        ReflectionTestUtils.setField(controller, "sessionService", sessions);
        assertEquals(200, controller.logout(request).getCode());
        verify(sessions).logout(42L, token); verify(sessions, never()).rotate(anyString(), anyLong(), anyString(), anyLong(), anyLong());
    }
    @Test void sameSecondLoginsHaveIndependentTokenIds() {
        assertNotEquals(jwt.createToken(42L, "ROLE_USER"), jwt.createToken(42L, "ROLE_USER"));
    }
    @Test void completionClearsThreadIdentity() {
        activeUser(); var request = request("GET", "/user/me"); var response = new MockHttpServletResponse();
        refresh.preHandle(request, response, new Object()); assertEquals(42L, UserContext.get());
        refresh.afterCompletion(request, response, new Object(), null); assertNull(UserContext.get());
    }
}
