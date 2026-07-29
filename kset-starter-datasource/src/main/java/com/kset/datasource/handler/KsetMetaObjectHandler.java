package com.kset.datasource.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;

/**
 * 自动填充 createTime / updateTime、createdAt / updatedAt、createDate / updateDate。
 */
public class KsetMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Date date = new Date();
        insertTime(metaObject, "createTime", date);
        insertTime(metaObject, "updateTime", date);
        insertTime(metaObject, "createdAt", date);
        insertTime(metaObject, "updatedAt", date);
        insertTime(metaObject, "createDate", date);
        insertTime(metaObject, "updateDate", date);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Date date = new Date();
        updateTime(metaObject, "updateTime", date);
        updateTime(metaObject, "updatedAt", date);
        updateTime(metaObject, "updateDate", date);
    }

    private void insertTime(MetaObject metaObject, String fieldName, Date date) {
        if (!metaObject.hasSetter(fieldName)) {
            return;
        }
        if (Date.class.equals(metaObject.getSetterType(fieldName))) {
            strictInsertFill(metaObject, fieldName, Date.class, date);
        }
    }

    private void updateTime(MetaObject metaObject, String fieldName, Date date) {
        if (!metaObject.hasSetter(fieldName)) {
            return;
        }
        if (Date.class.equals(metaObject.getSetterType(fieldName))) {
            strictUpdateFill(metaObject, fieldName, Date.class, date);
        }
    }
}
