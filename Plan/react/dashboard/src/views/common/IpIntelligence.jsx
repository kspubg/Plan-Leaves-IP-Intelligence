import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {Badge, Card, Col, Form, InputGroup, Row, Table} from "react-bootstrap";
import {FontAwesomeIcon as Fa} from "@fortawesome/react-fontawesome";
import {
    faChevronDown,
    faChevronRight,
    faFingerprint,
    faMagnifyingGlass,
    faMapLocationDot,
    faUsers
} from "@fortawesome/free-solid-svg-icons";
import Highcharts from "highcharts/esm/highmaps";
import chinaTopology from "@highcharts/map-collection/countries/cn/cn-all.topo.json";
import "highcharts/esm/modules/accessibility";
import "highcharts/esm/modules/no-data-to-display";
import {useDataRequest} from "../../hooks/dataFetchHook";
import {fetchIpIntelligence} from "../../service/serverService";
import {useAuth} from "../../hooks/authenticationHook.tsx";
import {CardLoader} from "../../components/navigation/Loader.tsx";
import CardHeader from "../../components/cards/CardHeader.tsx";

const provinceKeys = {
    "北京市": "cn-bj", "天津市": "cn-tj", "河北省": "cn-he", "山西省": "cn-sx", "内蒙古自治区": "cn-nm",
    "辽宁省": "cn-ln", "吉林省": "cn-jl", "黑龙江省": "cn-hl", "上海市": "cn-sh", "江苏省": "cn-js",
    "浙江省": "cn-zj", "安徽省": "cn-ah", "福建省": "cn-fj", "江西省": "cn-jx", "山东省": "cn-sd",
    "河南省": "cn-ha", "湖北省": "cn-hb", "湖南省": "cn-hn", "广东省": "cn-gd", "广西壮族自治区": "cn-gx",
    "海南省": "cn-hi", "重庆市": "cn-cq", "四川省": "cn-sc", "贵州省": "cn-gz", "云南省": "cn-yn",
    "西藏自治区": "cn-xz", "陕西省": "cn-sn", "甘肃省": "cn-gs", "青海省": "cn-qh", "宁夏回族自治区": "cn-nx",
    "新疆维吾尔自治区": "cn-xj", "台湾省": "cn-tw", "香港特别行政区": "cn-hk", "澳门特别行政区": "cn-mo"
};

const provinceAliases = {
    // Region providers do not all use the official administrative name. Keep
    // these aliases on the frontend so map, counts, ranking and filtering use
    // the same Highcharts key even when the stored value is abbreviated.
    "内蒙古": "内蒙古自治区",
    "内蒙古自治区": "内蒙古自治区",
    "Inner Mongolia": "内蒙古自治区",
    "Nei Mongol": "内蒙古自治区",
    "广西": "广西壮族自治区",
    "广西壮族自治区": "广西壮族自治区",
    "广西自治区": "广西壮族自治区",
    "Guangxi": "广西壮族自治区",
    "Guangxi Zhuang Autonomous Region": "广西壮族自治区",
    "Guangxi Zhuang Autonomous Area": "广西壮族自治区"
};

const normalizeProvince = province => {
    const value = typeof province === "string" ? province.trim() : "";
    if (!value) return "";
    if (provinceAliases[value]) return provinceAliases[value];

    // Be tolerant of provider values that append the administrative suffix
    // without changing the province identity (for example "广西省").
    const withoutSuffix = value.replace(/省$/, "");
    return provinceAliases[withoutSuffix] || value;
};

const uniquePlayerCount = records => new Set(records.map(record => record.playerUUID)).size;

const ChinaHeatmap = ({counts, onSelectProvince}) => {
    const mapElement = useRef(null);
    const chart = useRef(null);

    useEffect(() => {
        const data = Object.entries(counts)
            .filter(([province]) => provinceKeys[province])
            .map(([province, value]) => ({"hc-key": provinceKeys[province], value, province}));

        chart.current?.destroy();
        const instance = Highcharts.mapChart(mapElement.current, {
            chart: {map: chinaTopology, backgroundColor: "transparent", spacing: [8, 8, 8, 8]},
            title: {text: ""},
            credits: {enabled: false},
            accessibility: {description: "China province player distribution map"},
            mapNavigation: {enabled: true, enableMouseWheelZoom: false},
            colorAxis: {min: 1, minColor: "#b9e4e7", maxColor: "#cf4f3f"},
            tooltip: {
                formatter: function () {
                    const province = this.point.options?.province || this.point.name;
                    return `<b>${province}</b><br/>玩家: ${this.point.value || 0}`;
                }
            },
            series: [{
                data,
                name: "玩家",
                joinBy: "hc-key",
                nullColor: "#e8edf0",
                borderColor: "#ffffff",
                borderWidth: 0.8,
                states: {hover: {color: "#f2a541"}},
                point: {events: {click: function () {
                    const province = this.options?.province || this.name;
                    if (province) onSelectProvince(province);
                }}}
            }]
        });
        chart.current = instance;
        return () => {
            if (chart.current === instance) {
                instance.destroy();
                chart.current = null;
            }
        };
    }, [counts, onSelectProvince]);

    return <div className="ip-intelligence-map" ref={mapElement}/>;
};

const IpIntelligence = () => {
    const {hasPermission} = useAuth();
    const canAccess = hasPermission("page.ip.intelligence");
    const {data, loadingError} = useDataRequest(fetchIpIntelligence, [], canAccess);
    const [selectedProvince, setSelectedProvince] = useState(undefined);
    const [expandedProvince, setExpandedProvince] = useState(undefined);
    const [search, setSearch] = useState("");
    const [expandedPlayers, setExpandedPlayers] = useState(() => new Set());

    const records = data?.records || [];
    const recordsByPlayer = useMemo(() => {
        const grouped = new Map();
        records.forEach(record => {
            if (!grouped.has(record.playerUUID)) grouped.set(record.playerUUID, []);
            grouped.get(record.playerUUID).push(record);
        });
        return grouped;
    }, [records]);
    const provinceCounts = useMemo(() => {
        const groups = records.map(record => ({...record, province: normalizeProvince(record.province)}))
            .filter(record => provinceKeys[record.province]).reduce((result, record) => {
            (result[record.province] ||= []).push(record);
            return result;
        }, {});
        return Object.fromEntries(Object.entries(groups).map(([province, entries]) => [province, uniquePlayerCount(entries)]));
    }, [records]);
    const provinceRecords = useMemo(() => selectedProvince
        ? records.filter(record => normalizeProvince(record.province) === selectedProvince)
        : [], [records, selectedProvince]);
    const searchedRecords = useMemo(() => {
        const value = search.trim().toLowerCase();
        if (!value) return [];

        const targetRecords = records.filter(record => record.playerName?.toLowerCase().includes(value));
        const targetIps = new Set(targetRecords.map(record => record.ipAddress));
        const relatedPlayerIds = new Set(
            records.filter(record => targetIps.has(record.ipAddress)).map(record => record.playerUUID)
        );
        return records.filter(record => relatedPlayerIds.has(record.playerUUID));
    }, [records, search]);
    const visibleRecords = search.trim() ? searchedRecords : provinceRecords;
    const relatedPlayers = useMemo(() => {
        const unique = new Map();
        visibleRecords.forEach(record => unique.set(record.playerUUID, record));
        return [...unique.values()];
    }, [visibleRecords]);
    const provinceRanking = useMemo(() => Object.entries(provinceCounts)
        .sort(([, countA], [, countB]) => countB - countA)
        .map(([province, count], index) => ({province, count, rank: index + 1})), [provinceCounts]);
    const cityRankings = useMemo(() => {
        const provinces = new Map();
        records.forEach(record => {
            const province = normalizeProvince(record.province);
            if (!provinceKeys[province]) return;

            const city = typeof record.city === "string" && record.city.trim() ? record.city.trim() : "未知城市";
            if (!provinces.has(province)) provinces.set(province, new Map());
            const cities = provinces.get(province);
            if (!cities.has(city)) cities.set(city, new Set());
            cities.get(city).add(record.playerUUID);
        });

        return new Map([...provinces].map(([province, cities]) => [province, [...cities.entries()]
            .map(([city, players]) => ({city, count: players.size}))
            .sort((a, b) => b.count - a.count || a.city.localeCompare(b.city, "zh-CN"))]));
    }, [records]);
    const maxProvinceCount = provinceRanking[0]?.count || 1;

    const getRelatedAccounts = playerUUID => {
        const playerRecords = recordsByPlayer.get(playerUUID) || [];
        const playerIps = new Set(playerRecords.map(record => record.ipAddress).filter(Boolean));
        const relatedIds = new Set();
        records.forEach(record => {
            if (record.playerUUID !== playerUUID && playerIps.has(record.ipAddress)) relatedIds.add(record.playerUUID);
        });
        return [...relatedIds].map(relatedId => {
            const history = recordsByPlayer.get(relatedId) || [];
            const sharedIps = [...new Set(history.map(record => record.ipAddress).filter(ip => playerIps.has(ip)))];
            return {playerUUID: relatedId, records: history, sharedIps};
        }).sort((a, b) => Math.max(...b.records.map(record => record.lastSeen || 0), 0)
            - Math.max(...a.records.map(record => record.lastSeen || 0), 0));
    };

    const toggleExpanded = playerUUID => setExpandedPlayers(previous => {
        const next = new Set(previous);
        if (next.has(playerUUID)) next.delete(playerUUID);
        else next.add(playerUUID);
        return next;
    });

    const selectProvince = useCallback(province => {
        setSelectedProvince(normalizeProvince(province));
        setSearch("");
    }, []);

    const toggleProvinceRanking = province => {
        const normalized = normalizeProvince(province);
        selectProvince(normalized);
        setExpandedProvince(current => current === normalized ? undefined : normalized);
    };

    if (!canAccess) return null;
    if (loadingError) return <div className="alert alert-danger">无法加载 IP 分析数据。</div>;
    if (!data) return <CardLoader/>;

    return <section className="ip-intelligence">
        {!data.enabled && <div className="alert alert-warning">IP 记录功能尚未启用。启用后，新的玩家登录才会开始写入数据。</div>}
        <Row>
            <Col xl={8}>
                <Card>
                    <CardHeader icon={faMapLocationDot} color="geolocation" label="中国地区玩家分布">
                        <span className="ip-intelligence-header-note">点击省份查看玩家</span>
                    </CardHeader>
                    <Card.Body>
                        <ChinaHeatmap counts={provinceCounts} onSelectProvince={selectProvince}/>
                    </Card.Body>
                </Card>
            </Col>
            <Col xl={4}>
                <Card className="ip-intelligence-summary">
                    <CardHeader icon={faFingerprint} color="primary" label="账号关联查询"/>
                    <Card.Body>
                        <Form.Label htmlFor="player-name-search">按玩家名查找关联账号及历史 IP</Form.Label>
                        <InputGroup>
                            <InputGroup.Text><Fa icon={faMagnifyingGlass}/></InputGroup.Text>
                            <Form.Control id="player-name-search" value={search} onChange={event => setSearch(event.target.value)}
                                          placeholder="例如 Steve" autoComplete="off"/>
                        </InputGroup>
                        <div className="ip-intelligence-stat-grid">
                            <div><strong>{uniquePlayerCount(records)}</strong><span>已记录玩家</span></div>
                            <div><strong>{new Set(records.map(record => record.ipAddress)).size}</strong><span>唯一 IP</span></div>
                            <div><strong>{Object.keys(provinceCounts).length}</strong><span>覆盖省份</span></div>
                        </div>
                        <p className="ip-intelligence-muted mb-0">原始 IP 和关联关系仅向拥有网页权限的管理员提供。</p>
                    </Card.Body>
                </Card>
                <Card className="ip-intelligence-ranking-card">
                    <CardHeader icon={faUsers} color="players" label="省份玩家排行">
                        <span className="ip-intelligence-header-note">按玩家数</span>
                    </CardHeader>
                    <Card.Body className="p-0">
                        {provinceRanking.length === 0
                            ? <div className="ip-intelligence-empty ip-intelligence-ranking-empty">暂无省份数据。</div>
                            : <div className="ip-intelligence-ranking-list">
                                {provinceRanking.map(item => {
                                    const cities = cityRankings.get(item.province) || [];
                                    const isExpanded = expandedProvince === item.province;
                                    return <div className={`ip-intelligence-ranking-item${isExpanded ? " is-expanded" : ""}`} key={item.province}>
                                        <button type="button"
                                            className={`ip-intelligence-ranking-row${selectedProvince === item.province ? " is-selected" : ""}`}
                                            onClick={() => toggleProvinceRanking(item.province)} aria-expanded={isExpanded}
                                            aria-label={`${isExpanded ? "收起" : "展开"}${item.province}的城市排行`}>
                                            <span className="ip-intelligence-ranking-rank">{item.rank}</span>
                                            <span className="ip-intelligence-ranking-name">{item.province}</span>
                                            <span className="ip-intelligence-ranking-bar"><span style={{width: `${Math.max(8, item.count / maxProvinceCount * 100)}%`}}/></span>
                                            <strong>{item.count}</strong>
                                            <Fa className="ip-intelligence-ranking-toggle" icon={isExpanded ? faChevronDown : faChevronRight}/>
                                        </button>
                                        {isExpanded && <div className="ip-intelligence-city-ranking" aria-label={`${item.province}城市玩家排行`}>
                                            <div className="ip-intelligence-city-ranking-label">城市玩家排行</div>
                                            {cities.map((city, index) => <div className="ip-intelligence-city-ranking-row" key={city.city}>
                                                <span>{index + 1}</span><span>{city.city}</span><strong>{city.count}</strong>
                                            </div>)}
                                        </div>}
                                    </div>;
                                })}
                            </div>}
                    </Card.Body>
                </Card>
            </Col>
        </Row>
        <Card>
            <CardHeader icon={faUsers} color="servers" label={search.trim() ? "玩家关联账号与历史 IP" : selectedProvince ? `${selectedProvince} 的玩家` : "地区玩家列表"}/>
            <Card.Body className="p-0">
                {!search.trim() && !selectedProvince && <div className="ip-intelligence-empty">点击地图中的省份，或输入玩家名开始查询。</div>}
                {(search.trim() || selectedProvince) && relatedPlayers.length === 0 && <div className="ip-intelligence-empty">没有匹配的记录。</div>}
                {relatedPlayers.length > 0 && <div className="table-responsive">
                    <Table hover className="mb-0 ip-intelligence-table">
                        <thead><tr><th>玩家</th><th>IP 地址</th><th>地区</th><th>网络</th><th>最近出现</th></tr></thead>
                        <tbody>{relatedPlayers.map(record => {
                            const playerRecords = recordsByPlayer.get(record.playerUUID) || [];
                            const relatedAccounts = getRelatedAccounts(record.playerUUID);
                            const latestRecord = [...playerRecords].sort((a, b) => (b.lastSeen || 0) - (a.lastSeen || 0))[0] || record;
                            const isExpanded = expandedPlayers.has(record.playerUUID);
                            return <React.Fragment key={record.playerUUID}>
                                <tr className={isExpanded ? "is-expanded" : ""}>
                                    <td>
                                        <div className="ip-intelligence-player-cell">
                                            <button type="button" className="ip-intelligence-expand-button" disabled={relatedAccounts.length === 0}
                                                onClick={() => toggleExpanded(record.playerUUID)} aria-expanded={isExpanded}
                                                aria-label={isExpanded ? `收起 ${record.playerName} 的关联账号` : `展开 ${record.playerName} 的关联账号`}
                                                title={relatedAccounts.length === 0 ? "没有发现同 IP 关联账号" : (isExpanded ? "收起关联账号" : "展开关联账号")}>
                                                <Fa icon={isExpanded ? faChevronDown : faChevronRight}/>
                                            </button>
                                            <span><strong>{record.playerName}</strong><br/><small>{playerRecords.length} 条地址记录 · {relatedAccounts.length} 个关联账号</small></span>
                                        </div>
                                    </td>
                                    <td>{[...new Set(playerRecords.map(item => item.ipAddress).filter(Boolean))].join(", ")}</td>
                                    <td><Badge bg="light" text="dark">{latestRecord.province || "未知"}</Badge> {latestRecord.city || ""}</td>
                                    <td>{latestRecord.isp || "未知"}</td>
                                    <td>{latestRecord.lastSeen ? new Date(latestRecord.lastSeen).toLocaleString() : "-"}</td>
                                </tr>
                                {isExpanded && <tr className="ip-intelligence-expansion-row">
                                    <td colSpan={5}>
                                        <div className="ip-intelligence-related-header">共享历史 IP 的关联账号</div>
                                        <div className="ip-intelligence-related-list">
                                            {relatedAccounts.map(account => <div className="ip-intelligence-related-account" key={account.playerUUID}>
                                                <div className="ip-intelligence-related-account-title"><strong>{account.records[0]?.playerName || account.playerUUID}</strong><span>共享 {account.sharedIps.join(", ")}</span></div>
                                                <div className="ip-intelligence-history-list">
                                                    {account.records.map(history => <span key={`${account.playerUUID}-${history.ipAddress}-${history.firstSeen}`}>
                                                        {history.ipAddress} · {history.province || "未知地区"}{history.city ? ` ${history.city}` : ""}
                                                    </span>)}
                                                </div>
                                            </div>)}
                                        </div>
                                    </td>
                                </tr>}
                            </React.Fragment>;
                        })}</tbody>
                    </Table>
                </div>}
            </Card.Body>
        </Card>
    </section>;
};

export default IpIntelligence;
