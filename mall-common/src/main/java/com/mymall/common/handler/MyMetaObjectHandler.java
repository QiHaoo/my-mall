package com.mymall.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.mymall.common.util.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充
 *
 * <p>插入时填充：createTime、updateTime、createBy、updateBy、isDeleted(0)、version(1)
 * <p>更新时填充：updateTime、updateBy
 *
 * <p>采用 strictInsertFill/strictUpdateFill 严格模式：字段已有值时不覆盖；
 * 仅对实体中声明了对应 {@link com.baomidou.mybatisplus.annotation.FieldFill} 的字段生效，
 * 因此不影响未继承 BaseEntity 的实体（如手写实体无 fill 注解的字段不会被触碰）。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getUserId();

        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
        this.strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
        this.strictInsertFill(metaObject, "version", Integer.class, 1);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", Long.class, UserContext.getUserId());
    }
}
