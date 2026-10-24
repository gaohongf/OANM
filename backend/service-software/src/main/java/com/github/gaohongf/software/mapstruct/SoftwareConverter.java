package com.github.gaohongf.software.mapstruct;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.github.gaohongf.software.entity.po.SoftwareCategoryEntity;
import com.github.gaohongf.software.entity.po.SoftwareEntity;
import com.github.gaohongf.software.entity.po.SoftwareVersionEntity;
import com.github.gaohongf.software.entity.res.SoftwareCategoryRes;
import com.github.gaohongf.software.entity.res.SoftwareDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareManageDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareManageRes;
import com.github.gaohongf.software.entity.res.SoftwareManageVersionRes;
import com.github.gaohongf.software.entity.res.SoftwareRes;
import com.github.gaohongf.software.entity.res.SoftwareVersionRes;

/**
 * 实体 → 响应。
 *
 * <h2>id 的 Long → String 与 platform 的枚举 → String 都是内建转换</h2>
 * 雪花 id 必须以字符串出网；枚举落库用 EnumTypeHandler 存的也是 name()，
 * 所以"库里存什么、接口吐什么、前端比对什么"是同一种表示。
 *
 * <h2>派生字段在 service 里补</h2>
 * categoryName、platforms、latestVersionNo、各计数在实体上不存在，
 * 这里用 IGNORE 策略跳过，由 service 组装。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SoftwareConverter {

    SoftwareCategoryRes toCategoryRes(SoftwareCategoryEntity entity);

    List<SoftwareCategoryRes> toCategoryResList(List<SoftwareCategoryEntity> entities);

    SoftwareRes toRes(SoftwareEntity entity);

    List<SoftwareRes> toResList(List<SoftwareEntity> entities);

    SoftwareDetailRes toDetailRes(SoftwareEntity entity);

    SoftwareVersionRes toVersionRes(SoftwareVersionEntity entity);

    List<SoftwareVersionRes> toVersionResList(List<SoftwareVersionEntity> entities);

    SoftwareManageRes toManageRes(SoftwareEntity entity);

    List<SoftwareManageRes> toManageResList(List<SoftwareEntity> entities);

    SoftwareManageDetailRes toManageDetailRes(SoftwareEntity entity);

    SoftwareManageVersionRes toManageVersionRes(SoftwareVersionEntity entity);

    List<SoftwareManageVersionRes> toManageVersionResList(List<SoftwareVersionEntity> entities);
}
