package com.github.gaohongf.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.entity.req.SaveApiRouteCommand;
import com.github.gaohongf.auth.entity.res.ApiRouteRes;
import com.github.gaohongf.auth.service.ApiRouteService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 网关路由管理。
 * <p>
 * 每次增删改之后都会把变更广播出去（见 {@code RouteChangeNotifier}）, 网关据此热更新路由,
 * 不需要重启。所以这里改完立刻生效, 不是"改完等下一次发版"。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/auth/api-routes")
public class ApiRouteController {

    private final ApiRouteService apiRouteService;

    /** 全部路由（含已停用的）。每个服务一条, 量级很小, 不分页。 */
    @GetMapping
    public List<ApiRouteRes> list() {
        return apiRouteService.list();
    }

    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    @PostMapping
    public void create(@RequestBody @Valid SaveApiRouteCommand command) {
        apiRouteService.create(command);
    }

    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}")
    public void update(
            @PathVariable("id") Long id,
            @RequestBody @Valid SaveApiRouteCommand command) {
        apiRouteService.update(id, command);
    }

    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        apiRouteService.delete(id);
    }
}
