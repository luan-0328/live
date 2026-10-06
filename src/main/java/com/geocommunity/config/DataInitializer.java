package com.geocommunity.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.geocommunity.common.utils.PasswordUtil;
import com.geocommunity.entity.Category;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.CategoryMapper;
import com.geocommunity.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动时初始化内置管理员账号 + 默认分类种子数据。
 * <p>
 * 默认管理员 luan，初始密码由配置项 admin.init-password 提供（默认值见 @Value，可用环境变量覆盖），密码 BCrypt 存储。
 * 兼容旧数据：若已存在管理员但密码仍是旧明文，启动时自动重写为 BCrypt 哈希。
 * 分类表为空时写入默认分类，保证首启即可正常发帖。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String ADMIN_ACCOUNT = "luan";

    /** 初始管理员密码，生产环境通过环境变量 ADMIN_INIT_PASSWORD 覆盖 */
    @Value("${admin.init-password:456281}")
    private String adminPassword;

    /** 默认分类种子数据（name → 排序权重） */
    private static final String[][] DEFAULT_CATEGORIES = {
            {"同城生活", "1"}, {"二手闲置", "2"}, {"求职招聘", "3"}, {"房屋租售", "4"},
            {"拼车出行", "5"}, {"宠物天地", "6"}, {"餐饮美食", "7"}, {"运动健身", "8"},
            {"亲子教育", "9"}, {"求助问答", "10"},
    };

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public void run(String... args) {
        initAdmin();
        initCategories();
    }

    private void initAdmin() {
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, ADMIN_ACCOUNT)
                .eq(User::getRole, "ROLE_ADMIN"));

        if (admin == null) {
            User user = new User();
            user.setPhone(ADMIN_ACCOUNT);
            user.setPassword(PasswordUtil.hash(adminPassword));
            user.setNickname("管理员");
            user.setRole("ROLE_ADMIN");
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("内置管理员账号已创建：{}", ADMIN_ACCOUNT);
        } else if (!PasswordUtil.isBcrypt(admin.getPassword())) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, admin.getId())
                    .set(User::getPassword, PasswordUtil.hash(adminPassword)));
            log.info("管理员账号密码已从明文升级为 BCrypt：{}", ADMIN_ACCOUNT);
        }
    }

    private void initCategories() {
        if (categoryMapper.selectCount(null) > 0) {
            return;
        }
        for (String[] c : DEFAULT_CATEGORIES) {
            Category category = new Category();
            category.setName(c[0]);
            category.setSort(Integer.parseInt(c[1]));
            category.setCreatedAt(LocalDateTime.now());
            categoryMapper.insert(category);
        }
        log.info("已初始化默认分类 {} 个", DEFAULT_CATEGORIES.length);
    }
}
