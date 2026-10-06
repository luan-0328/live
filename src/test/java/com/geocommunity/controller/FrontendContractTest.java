package com.geocommunity.controller;

import com.geocommunity.common.aspect.AdminOnlyAspect;
import com.geocommunity.common.auth.SessionService;
import com.geocommunity.common.exception.GlobalExceptionHandler;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.entity.*;
import com.geocommunity.mapper.*;
import com.geocommunity.service.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FrontendContractTest {
    private UserMapper users;
    private CategoryMapper categories;
    private PostMapper posts;
    private CommentService comments;
    private MockMvc adminMvc;
    private User admin;

    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"contract"),User.class);
        UserContext.set(1L);
        users=mock(UserMapper.class); categories=mock(CategoryMapper.class); posts=mock(PostMapper.class);
        comments=mock(CommentService.class);
        admin=new User(); admin.setId(1L); admin.setStatus(1); admin.setRole("ROLE_ADMIN");
        when(users.selectById(1L)).thenReturn(admin);
        var controller=new AdminController();
        ReflectionTestUtils.setField(controller,"userMapper",users);
        ReflectionTestUtils.setField(controller,"categoryMapper",categories);
        ReflectionTestUtils.setField(controller,"postMapper",posts);
        ReflectionTestUtils.setField(controller,"commentService",comments);
        var redis=mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        ReflectionTestUtils.setField(controller,"redisTemplate",redis);
        ReflectionTestUtils.setField(controller,"sessionService",mock(SessionService.class));
        var aspect=new AdminOnlyAspect(); ReflectionTestUtils.setField(aspect,"userMapper",users);
        ReflectionTestUtils.setField(aspect,"redisTemplate",redis);
        var proxy=new AspectJProxyFactory(controller); proxy.setProxyTargetClass(true); proxy.addAspect(aspect);
        Object securedController=proxy.getProxy();
        adminMvc=MockMvcBuilders.standaloneSetup(securedController).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void adminCanUseDedicatedCommentDeleteEndpoint() throws Exception {
        when(comments.removeComment(10L)).thenReturn(true);
        adminMvc.perform(delete("/admin/comment/10")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(comments).removeComment(10L);
    }
    @Test void ordinaryUserCannotUseAdminCommentEndpoint() throws Exception {
        admin.setRole("ROLE_USER");
        adminMvc.perform(delete("/admin/comment/10")).andExpect(status().isForbidden());
        verifyNoInteractions(comments);
    }
    @Test void categoryWithPostsCannotBeDeleted() throws Exception {
        when(categories.selectForUpdate(3)).thenReturn(new Category()); when(posts.selectCount(any())).thenReturn(1L);
        adminMvc.perform(delete("/admin/category/3")).andExpect(jsonPath("$.code").value(400));
        verify(categories,never()).deleteById(any(java.io.Serializable.class));
    }
    @Test void invalidCategoryIsRejectedBeforePersistence() throws Exception {
        adminMvc.perform(post("/admin/category").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"sort\":-1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verify(categories,never()).insert(any(Category.class));
    }
    @Test void deletedUserCannotBeUnbanned() throws Exception {
        User deleted=new User(); deleted.setStatus(-1); when(users.selectForUpdate(2L)).thenReturn(deleted);
        adminMvc.perform(put("/admin/user/2/ban").contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"))
            .andExpect(jsonPath("$.code").value(400));
        verify(users,never()).update(any(),any());
    }
    @Test void adminCannotBanOwnSession() throws Exception {
        when(users.selectForUpdate(1L)).thenReturn(admin);
        adminMvc.perform(put("/admin/user/1/ban").contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
            .andExpect(jsonPath("$.code").value(400));
        verify(users,never()).update(any(),any());
    }
    @Test void repeatedBanRequestsBothSetBannedInsteadOfTogglingBack() throws Exception {
        User target=new User(); target.setId(2L); target.setStatus(1); when(users.selectForUpdate(2L)).thenReturn(target);
        for(int i=0;i<2;i++) adminMvc.perform(put("/admin/user/2/ban").contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
            .andExpect(jsonPath("$.code").value(200));
        var updates=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(users,times(2)).update(isNull(),updates.capture());
        assertTrue(updates.getAllValues().stream().allMatch(wrapper->wrapper.getParamNameValuePairs().containsValue(0)));
    }
    @Test void malformedIdsReturnClientErrorInsteadOfServerError() throws Exception {
        adminMvc.perform(delete("/admin/comment/not-a-number")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }
    @Test void invalidPhoneAndNicknameMatchFrontendConstraints() throws Exception {
        var service=mock(UserService.class); var controller=new UserController();
        ReflectionTestUtils.setField(controller,"userService",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/user/phone").contentType(MediaType.APPLICATION_JSON).content("{\"newPhone\":\"123\",\"code\":\"abcdef\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/user/profile").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\" \",\"avatar\":\"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
