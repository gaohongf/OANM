package com.github.gaohongf.wo.mapstruct;

import java.util.Arrays;
import java.util.List;

import org.mapstruct.Mapper;
import org.springframework.util.StringUtils;

import com.github.gaohongf.wo.entity.po.WorkOrderEntity;
import com.github.gaohongf.wo.entity.res.RecentWorkOrderRes;
import com.github.gaohongf.wo.entity.res.WorkOrderDetailRes;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;

/**
 * 工单实体 → 响应。
 *
 * <h2>三个枚举字段不用写映射方法</h2>
 * MapStruct 内建"枚举 ↔ String"的转换（枚举 → String 走 {@code name()}），
 * 而落库用的 {@code EnumTypeHandler} 存的也是 {@code name()} ——
 * 于是"库里存什么、接口吐什么、前端比对什么"是同一种表示，中间没有一层需要记的映射表。
 *
 * <h2>{@code id} 的 Long → String 也是内建转换</h2>
 * 见 {@link WorkOrderRes} 的类注释：雪花 ID 必须以字符串出网。
 * 这个转换看起来"顺手"，但它是<b>有意</b>的 —— 所以响应类型上写的是 String，
 * 而不是在这里手动 {@code String.valueOf}。
 */
@Mapper(componentModel = "spring")
public interface WorkOrderConverter {

    WorkOrderRes toRes(WorkOrderEntity entity);

    List<WorkOrderRes> toResList(List<WorkOrderEntity> entities);

    /** 详情=列表行 + 正文。两个方法分别声明, 而不是让列表也返回全部字段。 */
    WorkOrderDetailRes toDetailRes(WorkOrderEntity entity);

    /** 概览的"最近动态"：只取展示需要的几个字段，不带 {@code @User} 字段。 */
    RecentWorkOrderRes toRecentRes(WorkOrderEntity entity);

    List<RecentWorkOrderRes> toRecentResList(List<WorkOrderEntity> entities);

    /**
     * 库里的"一行一个"图片地址 → 响应里的数组。
     * <p>
     * MapStruct 发现这个 String → List&lt;String&gt; 的默认方法后会自动用它转换
     * {@code imageUrls}，不需要在 {@code @Mapping} 里再写表达式。
     */
    default List<String> toImageUrls(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return List.of();
        }
        return Arrays.stream(imageUrls.split("\n"))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();
    }
}
