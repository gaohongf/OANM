# OANM

**Operations and Network Management — 企业运维与网络管理平台**

OANM 是面向企业设备与网络管理的运维平台，供运维工程师和网络工程师使用。平台将工单流转、AI 辅助提单、站内消息、文件管理和软件分发集中在一个入口下，菜单、角色、权限和网关路由均可在线配置。

后端采用 Spring Cloud Alibaba 微服务架构，前端基于 React、TypeScript 与 Ant Design 构建。

## 功能特性

- **工单管理**：工单的创建、流转、处理与结单，提供「我的工单」视图和取消申请流程。
- **智能提单**：对接大模型，根据自然语言描述推断意图并生成工单，支持上传图片辅助识别。
- **消息通知**：站内消息的模板渲染、落库与 SSE 实时推送。
- **文件服务**：图片（登录即可，10MB）与大文件（需「上传大文件」权限，1GB）分接口上传，文件通过 `/file/**` 静态访问。
- **软件下载**：按分类、适用平台和关键字检索软件，支持最新版与历史版本下载，并统计下载次数。
- **知识库**：沉淀运维经验，Markdown 正文、分类与修订历史（可查看、可回滚），搜索语义优先、关键字补充，并对外提供供 AI 调用的检索接口。
- **统一授权**：用户、角色、权限、菜单与网关路由由 `service-auth` 统一管理，支持在线增删改，无需修改代码或重新发布。

## 快速开始

### 一键启动中间件（Docker Compose）

PostgreSQL、Redis、Kafka、Nacos 可以用 Docker Compose 一起启动，后端与前端仍按下方的手工流程在本机运行。

前提：已安装 Docker Desktop（含 Docker Compose v2）。

宿主端口沿用本地开发的一套（8848、5432、6379、9092），启动前请先停掉本机正在运行的 Nacos、PostgreSQL、Redis、Kafka，否则会端口冲突。

```bash
cp .env.example .env      # 可选：修改数据库密码，默认 postgres
docker compose up -d
```

首次启动会自动执行 `db/` 下的建表与种子脚本，并由 `nacos-init` 把 `docker/nacos/configs` 下的配置发布到 Nacos；之后按「启动后端」在本机启动各服务，再按「启动前端」启动前端。

| 地址 | 服务 |
| --- | --- |
| <http://localhost:8848/nacos> | Nacos 控制台 |
| localhost:5432 / 6379 / 9092 | PostgreSQL / Redis / Kafka |

```bash
docker compose ps                 # 查看状态，确认中间件 healthy、nacos-init 已退出（Exited (0)）
docker compose logs -f postgres
docker compose down               # 停止，保留数据
docker compose down -v            # 停止并清空全部数据卷（数据库、Nacos/Kafka/Redis 数据）
```

数据库脚本在 PostgreSQL 数据卷首次创建时自动执行，Nacos 配置在每次启动时重新发布，均无需手工操作。

大模型的 key 不在仓库与配置里：启动 `service-ai` 前在本机设置环境变量 `AI_API_KEY`（Nacos 配置里是占位符），否则 AI 相关接口会调用失败，其余功能不受影响。

> 内置管理员 `admin` / `admin123` 由 `docker/initdb/90-init-admin.sql` 创建，仅用于本地与演示环境，登录后请立即修改密码。生产环境不要使用该账号：去掉 `docker-compose.yml` 里 `90-init-admin.sql` 的挂载，按下方的手工流程创建管理员。

### 手动启动（本地开发）

#### 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 运行后端服务 |
| PostgreSQL | 18.x（需带 pgvector） | 业务数据存储，各服务共用一个数据库 `oanm`；知识库需要 `vector` 扩展 |
| Redis | 6.0 及以上 | 缓存与会话存储，sa-token 要求 6.0 及以上 |
| Nacos | 2.3.1 | 服务注册、服务发现与配置中心 |
| Kafka | 4.3.1 及以上 | 消息队列 |
| Ollama | 可选 | 知识库语义检索用 `qwen3-embedding:4b`；未安装或未启动时自动降级为关键字搜索 |
| Node.js | 20.19.x，或 22.12.x 及以上 | 构建前端，Vite 8 的要求 |

> 知识库的建表脚本会执行 `CREATE EXTENSION IF NOT EXISTS vector`，因此 PostgreSQL 必须装有 pgvector。本仓库的 `docker-compose.yml` 已使用 `pgvector/pgvector:pg18` 镜像；自建数据库时请按官方文档安装扩展，否则 `service-knowledge` 的建表会失败。

中间件推荐直接用上文的 `docker compose up -d` 启动；需要单独起某一个时，例如 Kafka：

```bash
docker run -d --name kafka-1-9092 -p 9092:9092 apache/kafka:latest
```

#### 初始化数据库

按以下顺序手工执行 `db/` 目录下的脚本：

| 顺序 | 脚本 | 内容 |
| --- | --- | --- |
| 1 | `backend/service-auth/db/schema.sql` | 用户、角色、权限、菜单、网关路由等系统表 |
| 2 | `backend/service-auth/db/seed.sql` | 内置角色、菜单、路由的种子数据 |
| 3 | `backend/service-work-order/db/schema.sql` | 工单表 |
| 4 | `backend/service-message/db/schema.sql` | 消息表、消息模板表 |
| 5 | `backend/service-message/db/seed.sql` | 消息模板的种子数据 |
| 6 | `backend/service-files/db/schema.sql` | 文件上传记录表 |
| 7 | `backend/service-software/db/schema.sql` | 软件分类、软件、软件版本表 |
| 8 | `backend/service-software/db/seed.sql` | 内置软件分类 |
| 9 | `backend/service-knowledge/db/schema.sql` | 知识库分类、文章、修订与向量分块表 |
| 10 | `backend/service-knowledge/db/seed.sql` | 内置知识库分类 |

脚本均为幂等脚本，可重复执行。种子数据不包含用户，系统也没有注册入口：需要先在 `users` 表插入一个用户（密码为 BCrypt 哈希），再按 `backend/service-auth/db/seed.sql` 第 5 段的说明把该用户关联到 `admin` 角色，重新登录后即可完成后续配置。使用 Docker Compose 时这一步已由 `docker/initdb/90-init-admin.sql` 自动完成。

#### 启动后端

中间件启动完成后，按以下顺序启动后端服务：

```
api-gateway (8080) → service-auth (8084) → service-work-order (8090) / service-ai (8085) / service-message (8093) / service-files (8099) / service-software (8094) / service-knowledge (8098)
```

#### 启动前端

```bash
cd web/oanm-web
npm install
npm run dev
```

开发服务器会将 `/api` 请求代理至网关，因此需先启动后端。构建与检查命令：

```bash
npm run build        # tsc -b && vite build
npm run lint         # oxlint
npm run check:menus  # 校验菜单 component 与页面路径是否一致
```

## 服务模块

| 模块 | 端口 | 对外前缀 | 职责 |
| --- | --- | --- | --- |
| common | — | — | 各模块共用的基础设施（工具类、鉴权注解、消息契约等），由业务模块按需依赖 |
| api-gateway | 8080 | `/api/**`、`/file/**` | 统一入口，转发业务接口与上传文件的静态访问 |
| service-auth | 8084 | `/api/auth` | 用户、角色、权限、菜单与网关路由的统一管理及授权 |
| service-work-order | 8090 | `/api/ops` | 工单的创建、流转与处理 |
| service-ai | 8085 | `/api/ai` | 大模型对接、意图推断与工单生成 |
| service-message | 8093 | `/api/message` | 站内消息模板渲染、落库与 SSE 推送 |
| service-files | 8099 | `/api/files` | 文件上传与记录，以及上传文件的 `/file/**` 静态访问 |
| service-software | 8094 | `/api/software` | 软件分类、软件与版本管理、下载计数 |
| service-knowledge | 8098 | `/api/knowledge` | 知识库文章、分类、修订历史与语义检索 |

## 项目结构

```
backend/
  pom.xml              聚合父 POM，统一管理版本与依赖
  common/              通用模块（被其他模块依赖，自身仅含 src）
  api-gateway/         网关
  service-auth/        鉴权与系统管理   ┐
  service-work-order/  工单             │
  service-ai/          AI               ├ 各业务模块均含 db/ 目录，存放数据库脚本
  service-message/     消息             │
  service-files/       文件             │
  service-software/    软件下载         │
  service-knowledge/   知识库           ┘
web/
  oanm-web/            前端源码（React + Vite）
docker/
  nacos/               Nacos 引导配置与发布脚本
  initdb/              Compose 专用的数据库初始化脚本
docker-compose.yml     中间件一键启动编排
standard.md            工程规范文档
gateway-routes.json    网关路由表导出快照（实际数据以数据库为准）
```

## 技术栈

- 后端：Spring Cloud Alibaba 2023.0.3.4、Spring Boot 3、Java 17、MyBatis-Plus、sa-token、Spring AI 1.1.8（知识库嵌入）
- 前端：React 19、TypeScript 6、Ant Design 6、Vite 8、zustand 5
- 中间件：PostgreSQL 18、Redis 6+、Nacos 2.3.1、Kafka 4.3.1+

## 参与开发

开发前请阅读 [standard.md](standard.md)，其中包含目录约定、接口与权限、响应模型及前端规范。提交前请确保后端编译通过，前端 `npm run lint` 与 `npm run build` 通过。

## 许可

见 [LICENSE](LICENSE)。
