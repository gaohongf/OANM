# OANM
本项目（Operations and Network Management）适用于运维工程师和网络工程师对企业的设备与网络进行管理


## 技术栈
### 后端
|名称|版本|说明|
|---|---|---|
|Spring Cloud Alibaba|2023.0.3.4|本项目基于Spring Cloud Alibaba|
|Java|17|开发环境使用Java17满足Spring Boot 3.0+最低要求|
|MySQL|8.x.x|数据库|
|Redis|6.x.x.x+|NoSQL，用作缓存， 版本必须6以上否则sa-token无法正常使用|
|Nacos|v2.3.1|服务注册 服务发现 配置中心|
|Kafka|4.3.1+|MQ|
## 核心模块
|模块名|中文名|说明|
|---|---|---|
|common|通用|各业务模块之间的通用组件|
|service-auth|授权与鉴权管理|管理用户-角色-权限-路由， 提供授权服务|
|api-gateway|路由|后端服务核心路由模块|