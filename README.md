# Plan Leaves IP Intelligence

基于 [Player Analytics (Plan)](https://github.com/plan-player-analytics/Plan) 的社区自定义构建，增加玩家 IP 历史、关联账号与中国省市分布分析。**本仓库及其 JAR 不是 Plan 官方发布版本。**

本次发布基于上游 `bc9412c2d`（Plan 5.9 开发分支），自定义版本为 `5.9 build 3664`。请从本仓库的 [Releases](https://github.com/kspubg/Plan-Leaves-IP-Intelligence/releases) 下载 JAR。

- 「玩家地理与分析」页面提供省份热力图、省市排行、地区玩家列表、游戏名搜索及历史 IP / 同 IP 关联账号。
- 使用本地 ip2region `.xdb` 解析地区；记录保存在 Plan 数据库的 `plan_ip_information` 表中。
- IP 收集默认关闭；页面和 `/v1/ipIntelligence` 接口需要 Plan Web 的 `PAGE_IP_INTELLIGENCE` 权限。

安装时先停止服务器、备份 `plugins/Plan/`，再用 Release JAR 替换 `plugins/Plan.jar`。保留原有数据库、`ServerInfoFile.yml` 和配置。启用 IP 功能、配置离线 IP 库、迁移数据库及隐私注意事项见 [部署文档](docs/CONFIGURATION.zh-CN.md)。不要将正式服数据或密钥上传到本仓库。

**English:** This is an unofficial Plan fork based on upstream commit `bc9412c2d`, adding an opt-in, permission-protected IP history and China region dashboard. The release JAR contains no player data or ip2region `.xdb` files. See the [deployment guide](docs/CONFIGURATION.zh-CN.md) for configuration and privacy notes.

本仓库保留原始 Git 历史及 [LGPL-3.0-or-later 许可](LICENSE)。修改不代表上游认可或维护。前端包含 Highcharts，使用时还需遵守其许可条款。

## 上游项目说明 / Upstream README

以下徽章、文档与发布链接指向 **Plan 官方项目**，不表示本仓库的构建或测试状态。

![Player Analytics](https://raw.githubusercontent.com/plan-player-analytics/drawio-diagrams-storage/master/image/header/main-header-Plan.jpg)

[![CI](https://github.com/plan-player-analytics/Plan/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/plan-player-analytics/Plan/actions/workflows/ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.djrapitops%3APlan&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.djrapitops%3APlan)  
![Java](https://custom-icon-badges.demolab.com/badge/⮞-red?logo=java-white)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=com.djrapitops%3APlan&metric=ncloc)](https://sonarcloud.io/dashboard?id=com.djrapitops%3APlan)
[![Code coverage](https://sonarcloud.io/api/project_badges/measure?project=com.djrapitops%3APlan&metric=coverage)](https://sonarcloud.io/dashboard?id=com.djrapitops%3APlan)  
![React](https://custom-icon-badges.demolab.com/badge/⮞-blue?logo=react-white)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=plan-player-analytics_Plan&metric=ncloc)](https://sonarcloud.io/summary/overall?id=plan-player-analytics_Plan)

[Documentation & Tutorials](https://github.com/plan-player-analytics/Plan/wiki) | [Releases](https://github.com/plan-player-analytics/Plan/releases) | [DEV builds](https://github.com/plan-player-analytics/Plan/actions/workflows/ci.yml?query=branch%3Amaster) | [Issues & Suggestions](https://github.com/plan-player-analytics/Plan/issues)

Player Analytics is a fully-fledged solution for the analytics needs of your Minecraft server. A built-in webserver displays insights into different aspects of the server such as Online activity, Playerbase, and how these change over time.

Plan supports multiple different platforms and versions; Spigot, Paper, Fabric, Sponge, SpongeForge, Bungeecord, Velocity, Folia & all derivatives - One jar for all platforms.

- [Version 5 Release trailer on Youtube](https://www.youtube.com/watch?v=BS_Ti9zkoRc)

## API

- [Javadocs](https://plan-player-analytics.github.io/Plan/)
- [API documentation](https://github.com/plan-player-analytics/Plan/wiki/APIv5)

[![](https://jitpack.io/v/plan-player-analytics/Plan.svg)](https://jitpack.io/#plan-player-analytics/Plan)

```xml
<repository>
    <id>jitpack</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.plan-player-analytics</groupId>
    <artifactId>Plan</artifactId>
    <version>{jitpack version}</version>
    <scope>provided</scope>
</dependency>
```

## Building

You can build the project by running the following in the repository root:
```
cd Plan
./gradlew build
```

- [More information about setting up the project](https://github.com/plan-player-analytics/Plan/wiki/Project-Setup)

## Used Libraries

- **[SBAdmin 2 Template](https://github.com/BlackrockDigital/startbootstrap-sb-admin-2)** | [MIT License](https://opensource.org/licenses/MIT)
- **[Bootstrap](https://getbootstrap.com/)** | [MIT License](https://v4-alpha.getbootstrap.com/about/license/)
- **[HighCharts](https://www.highcharts.com/)** | [Free for non-commercial](https://www.highcharts.com/products/highcharts/#non-commercial)
- **[FullCalendar](https://github.com/fullcalendar/fullcalendar)** | [MIT License](https://datatables.net/license/mit)
- **[Font Awesome Icons](http://fontawesome.io/icons/)** | [SIL Open Font License](http://scripts.sil.org/cms/scripts/page.php?site_id=nrsi&id=OFL)
- **[MaxMind GeoIP2 Country](https://www.maxmind.com/en/geoip2-country-database)** | [MaxMind End User License Agreement (EULA)](https://www.maxmind.com/en/end-user-license-agreement)
- **[Dagger](https://dagger.dev/)** | [Apache-2.0 License](https://github.com/google/dagger/blob/master/LICENSE.txt)
- **[DependencyDownload](https://github.com/Vankka/DependencyDownload)** | [MIT License](https://github.com/Vankka/DependencyDownload/blob/main/LICENSE)

## License

Player Analytics is licensed under LGPL-3 (GNU Lesser General Public License)

- [License](https://github.com/plan-player-analytics/Plan/blob/master/LICENSE)
