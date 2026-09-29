# 工程规范

## 文件位置
1. backend 存放后端源码
2. web 存放前端源码
3. backend 里由多个模块构成，模块里的src文件夹存放java源码，db文件夹存放数据库文件

## 开发规范
### 通用
1. 必须写注释

### 后端
0. **路径约定（接口路径同时是权限键，不是随便取的）**
   1. 对外接口统一用 `/api/<域>/...`，例如 `/api/auth/users`、`/api/ops/work_order/{id}`。
   2. **服务内部路径必须与外部路径完全一致**，网关路由不加 `StripPrefix`。
      这样一份路径同时是"网关谓词""服务内路由""权限键"三样东西，改一处不会漏掉另一处。
   3. 服务之间调用的内部接口放在 `/api` 之外（现有约定是 `/auth/internal/**`），
      并标 `@IsOpen`。因为网关只匹配 `/api/**`，这些端点天然无法从外部到达 ——
      不要靠"记得关掉网关的某个开关"来保证这件事。
   4. 鉴权拦截器会把请求解析成 `METHOD:路径模式`（如 `GET:/api/ops/work_order/{id}`），
      拿它去比对用户权限。所以**路径里的参数要写成 `{id}` 而不是具体值**，
      权限数据里存的也是这个模式。
1. 如果一个端点是公开的则要添加 @com.github.gaohongf.auth.annotation.IsOpen注解
2. 响应体有自动包装器，直接返回data会自动打包成{"code": xxx, "data": xxx, "type": xxx, "msg": xxx}的格式
3. 可以通过 @com.lingyun.base.rsm.annotation.ExecutionSuccess自定义请求成功时的msg内容
4. 可以通过 @com.lingyun.base.rsm.annotation.ExecutionFailed自定义请求失败时的msg内容
5. 上面两条的内容代表一种通用响应，也就是正常请求成功默认的消息，请求失败时默认的响应，这样可以防止异常暴露给前端，也可以通过R.msg方法来替换成功时的消息，可以通过R.error替换失败时的消息。
6. 响应被包装的字符串的时候请使用RString
7. 当一个端点无需包装时使用@NotPack注解
8. 如果通用的消息不满足需求，可以自己继承RsmManager然后安装规范写消息

### 前端
1. 使用TS,尽可能少使用any
2. 用户输入不可信，都需要进行校验
3. 界面需要适配手机端
4. 绝大部分地方都需要添加过渡效果，避免生硬
