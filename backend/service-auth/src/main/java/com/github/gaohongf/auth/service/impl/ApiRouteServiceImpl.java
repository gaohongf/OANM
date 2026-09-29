package com.github.gaohongf.auth.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.auth.dao.ApiRouteDao;
import com.github.gaohongf.auth.entity.po.ApiRouteEntity;
import com.github.gaohongf.auth.entity.req.SaveApiRouteCommand;
import com.github.gaohongf.auth.entity.res.ApiRouteRes;
import com.github.gaohongf.auth.gateway.RouteChangeNotifier;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.res.GatewayRouteRes;
import com.github.gaohongf.auth.service.ApiRouteService;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class ApiRouteServiceImpl implements ApiRouteService {

    private final ApiRouteDao apiRouteDao;
    private final RouteChangeNotifier routeChangeNotifier;

    @Override
    public List<ApiRouteRes> list() {
        return apiRouteDao.selectList(Wrappers.<ApiRouteEntity>lambdaQuery()
                        .orderByAsc(ApiRouteEntity::getRouteOrder)
                        .orderByAsc(ApiRouteEntity::getId))
                .stream()
                .map(ApiRouteServiceImpl::toRes)
                .toList();
    }

    @Override
    @Transactional
    public void create(SaveApiRouteCommand command) {
        command.check();
        requirePathPatternFree(command.getPathPattern(), null);

        ApiRouteEntity entity = new ApiRouteEntity();
        applyCommand(entity, command);
        entity.setLocked(false);
        entity.setSource("MANUAL");
        apiRouteDao.insert(entity);

        routeChangeNotifier.changed(String.valueOf(entity.getId()));
    }

    @Override
    @Transactional
    public void update(Long id, SaveApiRouteCommand command) {
        command.check();
        ApiRouteEntity entity = apiRouteDao.selectById(id);
        if (entity == null) {
            R.error(AuthRsm.API_ROUTE_NOT_FOUND);
        }
        requirePathPatternFree(command.getPathPattern(), id);

        applyCommand(entity, command);
        apiRouteDao.updateById(entity);

        routeChangeNotifier.changed(String.valueOf(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (apiRouteDao.selectById(id) == null) {
            R.error(AuthRsm.API_ROUTE_NOT_FOUND);
        }
        apiRouteDao.deleteById(id);

        routeChangeNotifier.changed(String.valueOf(id));
    }

    @Override
    public List<GatewayRouteRes> listEnabledAsGatewayRoutes() {
        return apiRouteDao.selectList(Wrappers.<ApiRouteEntity>lambdaQuery()
                        .eq(ApiRouteEntity::getEnabled, true)
                        .orderByAsc(ApiRouteEntity::getRouteOrder)
                        .orderByAsc(ApiRouteEntity::getId))
                .stream()
                .map(entity -> GatewayRouteRes.ofPathPrefix(
                        String.valueOf(entity.getId()),
                        entity.getUri(),
                        entity.getRouteOrder() == null ? 0 : entity.getRouteOrder(),
                        // 上面已经过滤过 enabled 了, 这里恒为 true。带上这个字段是为了让网关
                        // 拿到的是完整定义（它自己也有一套"停用"的语义）, 而不是依赖调用方过滤。
                        true,
                        entity.getPathPattern()))
                .toList();
    }

    @Override
    public List<String> listEnabledPathPatterns() {
        return apiRouteDao.selectList(Wrappers.<ApiRouteEntity>lambdaQuery()
                        .eq(ApiRouteEntity::getEnabled, true)
                        .orderByAsc(ApiRouteEntity::getRouteOrder))
                .stream()
                .map(ApiRouteEntity::getPathPattern)
                .toList();
    }

    private void applyCommand(ApiRouteEntity entity, SaveApiRouteCommand command) {
        entity.setName(command.getName());
        entity.setPathPattern(command.getPathPattern());
        entity.setUri(command.getUri());
        entity.setRouteOrder(command.getRouteOrder() == null ? 0 : command.getRouteOrder());
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
    }

    /**
     * 路径前缀不能重复。
     * <p>
     * 库上有 UNIQUE 约束, 这里显式查一次是为了给出明确的提示, 而不是让数据库抛
     * DuplicateKeyException —— 那个报错看不出是哪条路由和哪条冲突。
     *
     * @param excludeId 修改时排除自己, 否则"改了名字但没改前缀"会被误判为冲突
     */
    private void requirePathPatternFree(String pathPattern, Long excludeId) {
        boolean taken = apiRouteDao.exists(Wrappers.<ApiRouteEntity>lambdaQuery()
                .eq(ApiRouteEntity::getPathPattern, pathPattern)
                .ne(excludeId != null, ApiRouteEntity::getId, excludeId));
        if (taken) {
            R.error(AuthRsm.API_ROUTE_PATH_EXISTS);
        }
    }

    private static ApiRouteRes toRes(ApiRouteEntity entity) {
        return new ApiRouteRes(
                entity.getId(),
                entity.getName(),
                entity.getPathPattern(),
                entity.getUri(),
                entity.getRouteOrder(),
                entity.getEnabled(),
                entity.getLocked(),
                entity.getSource());
    }
}
