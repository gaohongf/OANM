# 工程规范

## 文件位置

1. backend 存放后端源码
2. web 存放前端源码
3. backend 里由多个模块构成，模块里的src文件夹存放java源码，db文件夹存放数据库文件
4. 数据库脚本不引入 Flyway，需按 README 的顺序手工执行。脚本必须可重复执行（种子数据用
   `WHERE NOT EXISTS` 判重）；变更表结构时同步修改 `schema.sql` 并在数据库中执行，不能只改代码。

## 开发规范

### 通用

1. 必须写注释

### Agent 阅读

1. 验证前端效果让人类工程师验证，一定不要自己去请求前端页面。
2. 一次解决一个问题，如果在过程中遇到其他问题请询问人类工程师，不要自作主张。
3. 在代码文件和前端界面显示的只能是可用结果，不要把思考过程和踩过的坑都写在实际代码中，踩的坑可以写在注释里。

### 后端通用

0. **路径约定（接口路径同时是权限键，不是随便取的）**
   1. 对外接口统一用 `/api/<域>/...`，例如 `/api/auth/users`、`/api/ops/work_order/{id}`。
   2. **服务内部路径必须与外部路径完全一致**，网关路由不加 `StripPrefix`。
      这样一份路径同时是"网关谓词""服务内路由""权限键"三样东西，改一处不会漏掉另一处。
   3. 服务之间调用的内部接口放在 `/api` 之外（现有约定是 `/auth/internal/**`），
      并标 `@IsOpen`。网关只转发 `/api/**` 与 `/file/**`，这些端点天然无法从外部到达 ——
      不要靠"记得关掉网关的某个开关"来保证这件事。
   4. 鉴权拦截器会把请求解析成 `METHOD:路径模式`（如 `GET:/api/ops/work_order/{id}`），
      拿它去比对用户权限。所以**路径里的参数要写成 `{id}` 而不是具体值**，
      权限数据里存的也是这个模式。
1. 如果一个端点是公开的则要添加 @com.github.gaohongf.auth.annotation.IsOpen注解
2. 向所有登录的用户公开标记 @com.github.gaohongf.auth.annotation.LoginOnly注解
3. **响应模型里的 id 一律是 `String`，不能是 `Long`。** 主键是 19 位雪花 id，
   JavaScript 的 `Number` 只能精确表示到 `2^53 - 1`，以数字出网会被静默舍入成另一个值，且不报错。
   前端侧对应地不要写 `Number(id)`。
4. **通用组件放在 `common`，但网关不依赖它**，需要时各自显式引入。
   `common` 里的 `@Component` 必须通过 `AutoConfiguration.imports` 注册，否则在其他服务里静默失效。

# Servlet服务

1. 响应体有自动包装器，直接返回data会自动打包成{"code": xxx, "data": xxx, "type": xxx, "msg": xxx}的格式
2. 可以通过 @com.lingyun.base.rsm.annotation.ExecutionSuccess自定义请求成功时的msg内容
3. 可以通过 @com.lingyun.base.rsm.annotation.ExecutionFailed自定义请求失败时的msg内容
4. 上面两条的内容代表一种通用响应，也就是正常请求成功默认的消息，请求失败时默认的响应，这样可以防止异常暴露给前端，也可以通过R.msg方法来替换成功时的消息，可以通过R.error替换失败时的消息。
5. 响应被包装的字符串的时候请使用RString
6. 当一个端点无需包装时使用@NotPack注解
7. 如果通用的消息不满足需求，可以自己继承RsmManager然后按照规范写消息

# Reactive服务


### 前端

1. 使用TS,尽可能少使用any
2. 用户输入不可信，都需要进行校验
3. 界面需要适配手机端
4. 绝大部分地方都需要添加过渡效果，避免生硬
