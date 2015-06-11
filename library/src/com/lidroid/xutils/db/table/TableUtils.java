/*
 * Copyright (c) 2013. wyouflf (wyouflf@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lidroid.xutils.db.table;

import android.text.TextUtils;

import com.lidroid.xutils.db.annotation.Id;
import com.lidroid.xutils.db.annotation.Table;
import com.lidroid.xutils.db.converter.ColumnConverterFactory;
import com.lidroid.xutils.util.LogUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class TableUtils {

    private TableUtils() {
    }

    public static String getTableName(Class<?> entityType) {
        Table table = entityType.getAnnotation(Table.class);
        if (table == null || TextUtils.isEmpty(table.name())) {//没有注解字段Table
            return entityType.getName().replace('.', '_');
        }
        return table.name();
    }

    /**
     * 获取表注解默认值
     */
    public static String getExecAfterTableCreated(Class<?> entityType) {
        Table table = entityType.getAnnotation(Table.class);
        if (table != null) {
            return table.execAfterTableCreated();
        }
        return null;
    }

    /**
     * key: entityType.name 高并发、高吞吐量的线程安全HashMap实现。
     */
    private static ConcurrentHashMap<String, HashMap<String, Column>> entityColumnsMap = new ConcurrentHashMap<String, HashMap<String, Column>>();

    /* package */

    /**
     * 根据类对象获取列对象的MAP
     */
    static synchronized HashMap<String, Column> getColumnMap(Class<?> entityType) {

        if (entityColumnsMap.containsKey(entityType.getName())) {//这个类已经存储过了数据库列对象
            return entityColumnsMap.get(entityType.getName());
        }
        HashMap<String, Column> columnMap = new HashMap<String, Column>();
        String primaryKeyFieldName = getPrimaryKeyFieldName(entityType);
        addColumns2Map(entityType, primaryKeyFieldName, columnMap);//缓存类对象的属性指为Columns对象
        entityColumnsMap.put(entityType.getName(), columnMap);//缓存这个数据表的Columns对象MAP集
        return columnMap;
    }

    /**
     * 缓存类对象的属性指为Columns对象
     */
    private static void addColumns2Map(Class<?> entityType, String primaryKeyFieldName, HashMap<String, Column> columnMap) {
        if (Object.class.equals(entityType)) return;
        try {
            Field[] fields = entityType.getDeclaredFields();
            for (Field field : fields) {
                if (ColumnUtils.isTransient(field) || Modifier.isStatic(field.getModifiers())) {//忽略static及注解Transient的属性
                    continue;
                }
                if (ColumnConverterFactory.isSupportColumnConverter(field.getType())) {//属性字段支持列对象转换
                    if (!field.getName().equals(primaryKeyFieldName)) {//不是ID字段
                        Column column = new Column(entityType, field);
                        if (!columnMap.containsKey(column.getColumnName())) {//缓存列对象
                            columnMap.put(column.getColumnName(), column);
                        }
                    }
                } else if (ColumnUtils.isForeign(field)) {// 属性字段是Foreign注解，从表注解
                    Foreign column = new Foreign(entityType, field);
                    if (!columnMap.containsKey(column.getColumnName())) {
                        columnMap.put(column.getColumnName(), column);
                    }
                } else if (ColumnUtils.isFinder(field)) {//属性字段是Finder，主表注解
                    Finder column = new Finder(entityType, field);
                    if (!columnMap.containsKey(column.getColumnName())) {
                        columnMap.put(column.getColumnName(), column);
                    }
                }
            }

            if (!Object.class.equals(entityType.getSuperclass())) {//将父类属性字段缓存道MAP
                addColumns2Map(entityType.getSuperclass(), primaryKeyFieldName, columnMap);
            }
        } catch (Throwable e) {
            LogUtils.e(e.getMessage(), e);
        }
    }

    /* package */
    static Column getColumnOrId(Class<?> entityType, String columnName) {
        if (getPrimaryKeyColumnName(entityType).equals(columnName)) {
            return getId(entityType);
        }
        return getColumnMap(entityType).get(columnName);
    }

    /**
     * key: entityType.name
     */
    private static ConcurrentHashMap<String, com.lidroid.xutils.db.table.Id> entityIdMap = new ConcurrentHashMap<String, com.lidroid.xutils.db.table.Id>();

    /* package */

    /**
     * 获得对象的ID属性
     */
    static synchronized com.lidroid.xutils.db.table.Id getId(Class<?> entityType) {
        if (Object.class.equals(entityType)) {
            throw new RuntimeException("field 'id' not found");
        }
        if (entityIdMap.containsKey(entityType.getName())) {
            return entityIdMap.get(entityType.getName());
        }

        Field primaryKeyField = null;
        Field[] fields = entityType.getDeclaredFields();//获取声明字段
        if (fields != null) {
            for (Field field : fields) {
                if (field.getAnnotation(Id.class) != null) {//获取注解ID属性
                    primaryKeyField = field;
                    break;
                }
            }

            if (primaryKeyField == null) {//没有发现注解ID，根据字段生成ID
                for (Field field : fields) {
                    if ("id".equals(field.getName()) || "_id".equals(field.getName())) {
                        primaryKeyField = field;
                        break;
                    }
                }
            }
        }

        if (primaryKeyField == null) {
            return getId(entityType.getSuperclass());//没有iD属性再去他的父类去找
        }

        com.lidroid.xutils.db.table.Id id = new com.lidroid.xutils.db.table.Id(entityType, primaryKeyField);//生成数据ID对象
        entityIdMap.put(entityType.getName(), id);//存储这个ID对象
        return id;
    }

    /**
     * 获取这个类对象的ID属性字段名称
     */
    private static String getPrimaryKeyFieldName(Class<?> entityType) {
        com.lidroid.xutils.db.table.Id id = getId(entityType);
        return id == null ? null : id.getColumnField().getName();
    }

    private static String getPrimaryKeyColumnName(Class<?> entityType) {
        com.lidroid.xutils.db.table.Id id = getId(entityType);
        return id == null ? null : id.getColumnName();
    }
}
