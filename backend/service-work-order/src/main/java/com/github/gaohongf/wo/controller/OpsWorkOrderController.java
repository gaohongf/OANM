package com.github.gaohongf.wo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.wo.res.WorkOrderRes;

@RestController
@RequestMapping("/api/ops/work_order")
public class OpsWorkOrderController {

    /**
     * 演示 {@code @User} 的解析效果。
     *
     * @param userId 用来指定 createBy/updateBy 指向哪个用户, 省得为了联调改代码。
     *               默认 1, 需要先在 service-auth 里建出这个用户。
     */
    @GetMapping("/{id}")
    public WorkOrderRes getWorkOrder(
            @PathVariable("id") String id,
            @RequestParam(name = "userId", defaultValue = "1") Long userId) {
        WorkOrderRes res = new WorkOrderRes();
        res.setId(id);
        res.setName("测试工单");
        res.setCreateBy(userId);
        res.setUpdateBy(userId);
        return res;
    }
}
