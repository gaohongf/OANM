package com.github.gaohongf.auth.register;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.entity.po.PermissionEntity;
import com.github.gaohongf.auth.satoken.AuthorityChangeNotifier;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 把上报上来的端点键登记成权限行。
 *
 * <h2>为什么只需要"键"</h2>
 * 权限键就是 `METHOD:路径模式`，与鉴权拦截器推导出来的形式完全一致，所以入库时不需要
 * 任何转换 —— 上报方直接把拦截器会用的那个键发过来即可。
 *
 * <h2>幂等</h2>
 * 每次服务启动都会重新上报一遍（端点会因为发版而增删）。所以这里按 key 去重，
 * 已存在的不动 —— 特别是<b>不会覆盖 {@code label}</b>：管理员手工改过的可读名字
 * 不该被下一次重启冲掉。
 *
 * <h2>只增不删</h2>
 * 端点被删掉之后，对应的权限行<b>保留</b>。理由：那条权限可能已经被授予了某些角色，
 * 直接删掉会让那些角色少一条授权却不留痕迹；而且如果那个端点只是这次发版临时下线，
 * 下次回来时授权还在。代价是权限表里会积累历史条目，靠定期人工清理。
 */
@Slf4j
@Service
@AllArgsConstructor
public class EndpointRegistrationService {

    private final PermissionDao permissionDao;
    private final AuthorityChangeNotifier authorityChangeNotifier;

    /**
     * 幂等登记一批权限键。
     *
     * @param keys 权限键，形如 {@code GET:/api/ops/work_order/{id}}
     * @return 本次<b>新建</b>的键（已存在的不计入）
     */
    @Transactional
    public List<String> register(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        // 去重后再查，避免同一批里重复的键互相干扰
        Set<String> distinct = new LinkedHashSet<>(keys);
        distinct.removeIf(key -> key == null || key.isBlank());
        if (distinct.isEmpty()) {
            return List.of();
        }

        Set<String> existing = permissionDao
                .selectList(Wrappers.<PermissionEntity>lambdaQuery()
                        .in(PermissionEntity::getPermissionKey, distinct))
                .stream()
                .map(PermissionEntity::getPermissionKey)
                .collect(Collectors.toSet());

        List<String> created = distinct.stream()
                .filter(key -> !existing.contains(key))
                .toList();

        if (created.isEmpty()) {
            return List.of();
        }

        for (String key : created) {
            PermissionEntity entity = new PermissionEntity();
            entity.setPermissionKey(key);
            // label 先用键本身占位（库里该列可空，但界面主要显示 label，给个值比空着好）。
            // 它只是给人看的可读名字，管理员在权限管理里改成"查看工单"这类即可 —— 而
            // 上面的幂等逻辑保证下次重启不会把这个改动冲掉。
            entity.setLabel(key);
            permissionDao.insert(entity);
        }

        // 新权限行本身不影响任何人的现有授权（还没授给谁），但权限列表变了，
        // 保守起见让缓存失效一次 —— 成本很低，而漏掉的话权限管理界面可能显示旧的列表。
        authorityChangeNotifier.allChanged();

        log.info("自注册新增 {} 条权限（共上报 {} 条）: {}", created.size(), distinct.size(), created);
        return created;
    }
}
