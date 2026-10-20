package com.github.gaohongf.files.dao.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hutool.core.lang.Dict;

/**
 * {@code jsonb} 列与 X File Storage 的 {@link Dict}（附加属性）之间的转换。
 *
 * <h2>为什么不直接用 MyBatis-Plus 的 JacksonTypeHandler</h2>
 * 它用 {@code PreparedStatement#setString} 写参数, PostgreSQL 不接受
 * {@code varchar} 到 {@code jsonb} 的隐式转换, 插入会直接失败。这里改成绑定
 * {@link PGobject}, 由驱动按 {@code jsonb} 类型发送。
 *
 * <p>
 * 只在本项目的{@link com.github.gaohongf.files.entity.po.FileDetailEntity}上通过
 * {@code @TableField(typeHandler = ...)} 显式引用, 不做全局注册 —— 全局注册会把它
 * 套到所有 {@code Dict} 字段上。
 */
public class JsonbTypeHandler extends BaseTypeHandler<Dict> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Dict parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        try {
            jsonb.setValue(MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException ex) {
            throw new SQLException("无法把文件附加属性序列化为 JSON", ex);
        }
        ps.setObject(i, jsonb);
    }

    @Override
    public Dict getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Dict getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Dict getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static Dict parse(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            Dict dict = new Dict();
            dict.putAll(MAPPER.readValue(json, MAP_TYPE));
            return dict;
        } catch (JsonProcessingException ex) {
            throw new SQLException("无法把文件附加属性解析为 JSON", ex);
        }
    }
}
