# 部署与配置

此文档适用于本仓库的自定义 Plan JAR，不是上游官方配置说明。请在停服并备份后升级，不要删除现有 `plugins/Plan/` 目录或数据库。

## 安装

- 将 Release 附件 JAR 放到服务器插件目录，命名为 `Plan.jar`。只保留一个启用的 Plan 插件 JAR。
- 保留原有 `plugins/Plan/config.yml`、`ServerInfoFile.yml`、数据库以及其它运行时文件。跨环境复制 SQLite 数据时，必须同时保持与数据库匹配的服务器身份文件，否则可能出现重复的同名服务器。
- 先对照新 JAR 生成的默认配置检查新增键，再合并旧配置中的数据库、WebServer、权限和数据保留等个性化设置；不要把公开示例配置直接覆盖正式服配置。
- 本仓库不分发玩家数据库、服务器配置、证书、MySQL 密码和 IP 数据文件。

## IP 与地区分析

在 `plugins/Plan/config.yml` 中设置：

```yaml
Data_gathering:
  IP_intelligence:
    Enabled: true
    IPv4_database: "ip2region/ip2region_v4.xdb"
    IPv6_database: "ip2region/ip2region_v6.xdb"
```

这两个路径相对于 `plugins/Plan/`。自行获取兼容的 [ip2region](https://github.com/lionsoul2014/ip2region) `.xdb` 数据并放到对应位置；缺少数据库时插件会记录警告，地区可能显示为未知。地图依赖离线数据库的数据质量，不能保证每个 IP 都能精确到城市。功能默认关闭，启用后新玩家连接才开始采集 IP；它不会从旧 Plan 记录中凭空补全历史 IP。

为需要使用该页面的 Plan Web 用户授予 `PAGE_IP_INTELLIGENCE` 权限。菜单和 `/v1/ipIntelligence` 受此权限控制。IP 属于个人数据，请遵守适用的隐私法律与告知、留存要求。Web 登录、访问控制和传输保护由部署者负责；纯 HTTP 会以明文传输登录凭据与 IP 数据，建议仅在可信网络内访问或通过受保护的反向代理访问。

## 数据与迁移

- SQLite 时，IP 历史位于 `plugins/Plan/database.db` 内的 `plan_ip_information` 表；MySQL 时位于所配置的 MySQL 数据库的同名表。它不是独立的玩家 IP 文件。
- Plan 的 `/plan db move SQLite MySQL` 复制流程已接入此自定义表，但运行命令会清空**目标数据库**；只对专用空目标库执行，先完整备份并在停写状态下核对记录数量。这个版本没有在真实 MySQL 环境完成端到端迁移验证。
- `ServerInfoFile.yml` 和离线 `.xdb` 文件不属于数据库表，数据库迁移不会复制它们。保留它们以及旧数据备份；不要为了切换 JAR 而重新安装 Plan。

## 编译与维护

源码根目录中的 `Plan/` 才是 Gradle 项目。使用 JDK 25 和 `Plan/gradlew.bat build`；构建输出在 `Plan/builds/`。上游变化可从原始 Git 历史与 [Plan 官方仓库](https://github.com/plan-player-analytics/Plan) 对照。本仓库的 Release 是社区自定义构建，安全更新和功能修复仍需持续跟进上游。
