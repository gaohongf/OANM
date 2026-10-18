# OANM

本项目（Operations and Network Management）适用于运维工程师和网络工程师对企业的设备与网络进行管理。

后端是基于 Spring Cloud Alibaba 的微服务，前端是 React 单页应用。菜单、权限、网关路由都由
`service-auth` 统一管理并支持在线增删改，不需要改代码重新发版。

## 技术栈

### 后端

|名称|版本|说明|
|---|---|---|
|Spring Cloud Alibaba|2023.0.3.4|本项目基于 Spring Cloud Alibaba|
|Java|17|开发环境使用 Java 17，满足 Spring Boot 3.0+ 最低要求|
|PostgreSQL|18.x|数据库，各服务共用一个库 `oanm`|
|Redis|6.x.x.x+|NoSQL，用作缓存，版本必须 6 以上否则 sa-token 无法正常使用|
|Nacos|v2.3.1|服务注册、服务发现、配置中心|
|Kafka|4.3.1+|MQ（开发环境用 Docker 镜像 `apache/kafka:latest`，broker 版本 4.3.1）|

### 前端

|名称|版本|说明|
|---|---|---|
|React|19.x|UI 框架|
|TypeScript|6.x|开发语言|
|antd|6.x|组件库|
|Vite|8.x|构建工具|
|zustand|5.x|状态管理|
|oxlint|1.x|代码检查（不是 ESLint）|

## 核心模块

|模块名|中文名|端口|对外前缀|说明|
|---|---|---|---|---|
|common|通用|—|—|各业务模块之间的通用组件（工具类、鉴权注解、消息契约等）。**不是**所有模块都依赖它|
|api-gateway|路由|8080|—|后端服务核心路由模块，只转发 `/api/**`|
|service-auth|授权与鉴权管理|8084|`/api/auth`|管理用户-角色-权限-路由，提供授权服务|
|service-work-order|工单|8090|`/api/ops`|工单的创建、流转与处理|
|service-ai|AI|8085|`/api/ai`|对接大模型，提供意图推断与工单生成能力|
|service-message|消息|8093|`/api/message`|站内消息通知的基础设施（模板渲染、落库、SSE 推送）|

外部请求一律先到网关，由网关按 `/api/**` 前缀转发到对应服务；服务之间调用的内部接口放在
`/api` 之外（约定是 `/auth/internal/**`），因此天然无法从网外到达。

## 目录结构

```
backend/
  pom.xml              聚合父 POM，统一管理版本与依赖
  common/              通用模块（被其他模块依赖，自身只有 src）
  api-gateway/         网关
  service-auth/        鉴权 + 系统管理   ┐
  service-work-order/  工单             ├ 每个模块下都有 db/ 目录存数据库脚本
  service-ai/          AI               │
  service-message/     消息             ┘
web/
  oanm-web/            前端源码（React + Vite）
standard.md            工程规范，动手前先读
gateway-routes.json    网关路由表的导出快照（实际数据在库里）
```

## 环境准备

需要先准备好 PostgreSQL、Redis、Nacos、Kafka 四样。

Kafka 开发环境容器（Nacos、Redis 同理，此处只列 Kafka）：

```bash
docker run -d --name kafka-1-9092 -p 9092:9092 apache/kafka:latest
```

## 数据库初始化

**没有引入 Flyway，`db/` 下的脚本需要手工执行**，各服务的脚本要按顺序跑：

|脚本|内容|
|---|---|
|`backend/service-auth/db/schema.sql`|用户、角色、权限、菜单、网关路由等系统表|
|`backend/service-auth/db/seed.sql`|内置角色、菜单、路由的种子数据|
|`backend/service-work-order/db/schema.sql`|工单表|
|`backend/service-message/db/schema.sql`|消息表、消息模板表|
|`backend/service-message/db/seed.sql`|消息模板的种子数据|

脚本都写成可重复执行的（种子数据用 `WHERE NOT EXISTS` 兜底），重跑不会产生重复行。
新增表结构时**改 `schema.sql` 并在库里手工执行一遍**，不要只改代码。

## 启动

四个中间件就位后，按依赖顺序启动：

```
api-gateway(8080) → service-auth(8084) → service-work-order(8090) / service-ai(8085) / service-message(8093)
```

前端：

```bash
cd web/oanm-web
npm install
npm run dev        # 开发
npm run build      # 构建（tsc -b && vite build）
npm run lint       # oxlint
```

前端开发服务器会把 `/api` 代理到网关，所以必须先起后端。

## 开发约定

写代码前请先读 [standard.md](standard.md)，那里是完整的工程规范。几条最容易踩的：

1. **路径即权限键**。一个接口路径同时是三样东西：网关谓词、服务内路由、权限键，
   所以服务内部路径必须与对外路径完全一致（网关不加 `StripPrefix`）。路径里的参数写成
   `{id}` 而不是具体值，权限数据里存的也是这个模式（如 `GET:/api/ops/work_order/{id}`）。
2. **接口权限用注解声明**。完全公开的加 `@IsOpen`，登录即可访问的加 `@LoginOnly`。
   自注册的权限扫描器会跳过这两类，因此不用给它们建权限数据、也不用授权。
3. **响应模型里的 id 一律是 `String`，不能是 `Long`**。主键是 19 位雪花 id，
   JS 的 `Number` 只能精确到 `2^53 - 1`，以数字出网会被**静默取整成另一个值** ——
   前端看起来一切正常，直到发现改的是别的行。前端侧对应地不要写 `Number(id)`。
4. **通用组件放在 `common`，但网关不依赖它**，需要时各自显式引入。
   `common` 里的 `@Component` 走 `AutoConfiguration.imports` 注册，否则在其他服务里会静默失效。

## 许可

见 [LICENSE](LICENSE)。
