import React, {useCallback, useEffect, useState} from "react";
import {Alert, Button, Card, Form, Modal, Spinner, Tab, Tabs, Table} from "react-bootstrap";
import {FontAwesomeIcon as Fa} from "@fortawesome/react-fontawesome";
import {useParams} from "react-router";
import {useAuth} from "../../hooks/authenticationHook.tsx";
import {fetchIpModeration, mutateIpModeration} from "../../service/ipModerationService";
import {faBell, faCheck, faChevronDown, faChevronUp, faClockRotateLeft, faEye,
    faRotate, faShieldHalved, faCircleInfo} from "@fortawesome/free-solid-svg-icons";
import "../../style/ip-moderation.css";

const labels = {PENDING: "待执行 / 中断待核查", APPLYING: "执行中 / 中断待核查", APPLIED: "本次已封禁",
    PREEXISTING: "原本已封禁（保留）", FAILED: "执行失败", FINISHED: "封禁流程完成", UNDOING: "撤销中",
    UNDONE: "已撤销", UNDO_PARTIAL: "部分撤销（需核查）", CONFLICT: "后来被修改（保留）", UNDO_FAILED: "撤销失败"};
const time = value => value ? new Date(value).toLocaleString() : "-";
const errorText = error => typeof error?.data === "string" ? error.data
    : error?.data?.message || error?.data?.error || error?.message || "操作失败，请刷新并检查审计记录";
const Status = ({status}) => {
    const tone = status === "UNDONE" ? "success" : ["FAILED", "UNDO_FAILED"].includes(status) ? "danger"
        : ["CONFLICT", "UNDO_PARTIAL", "PENDING", "APPLYING", "UNDOING"].includes(status) ? "warning" : "neutral";
    return <span className={`ip-moderation-status is-${tone}`}>{labels[status] || status}</span>;
};
const EmptyState = ({icon, title, children}) => <div className="ip-moderation-empty" role="status">
    <span className="ip-moderation-empty-icon"><Fa icon={icon}/></span>
    <strong>{title}</strong><p>{children}</p>
</div>;
const TabTitle = ({icon, label, count}) => <span className="ip-moderation-tab-title">
    <Fa icon={icon}/><span>{label}</span><span className="ip-moderation-count">{count}</span>
</span>;
const Items = ({operation}) => <div className="ip-moderation-items">
    {operation.items.map(item => <div className="ip-moderation-item" key={`${item.kind}-${item.target}`}>
        <div><span className="ip-moderation-eyebrow">{item.kind === "IP" ? "IP 地址" : "玩家账号"}</span>
            <strong className={item.kind === "IP" ? "ip-moderation-mono" : ""}>{item.target}</strong></div>
        <Status status={item.status}/>
        {item.error && <p className="ip-moderation-item-error">{item.error}</p>}
    </div>)}
</div>;

const IpModerationPanel = ({records, selection, onSelection}) => {
    const {hasPermission, loggedIn} = useAuth();
    const {identifier} = useParams();
    const canManage = loggedIn && hasPermission("manage.ip.moderation");
    const canUndo = loggedIn && hasPermission("manage.ip.moderation.undo");
    const [data, setData] = useState(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [ip, setIp] = useState("");
    const [preview, setPreview] = useState(null);
    const [reason, setReason] = useState("");
    const [days, setDays] = useState(30);
    const [step, setStep] = useState("select");
    const [result, setResult] = useState(null);
    const [undo, setUndo] = useState(null);
    const [expanded, setExpanded] = useState(null);
    const [limit, setLimit] = useState(20);
    const [activeTab, setActiveTab] = useState("alerts");
    const refresh = useCallback(async () => {
        if (!canManage && !canUndo) return;
        const response = await fetchIpModeration();
        if (response.error) setError(errorText(response.error));
        else setData(response.data);
    }, [canManage, canUndo]);
    useEffect(() => { refresh(); }, [refresh]);
    useEffect(() => {
        if (!canManage && !canUndo) return;
        const timer = setInterval(() => { if (!busy && !selection && !undo) refresh(); }, 30000);
        return () => clearInterval(timer);
    }, [canManage, canUndo, refresh, busy, selection, undo]);
    useEffect(() => {
        if (!selection) return;
        const history = records.filter(record => record.playerUUID === selection.record.playerUUID)
            .sort((a, b) => (b.lastSeen || 0) - (a.lastSeen || 0));
        setIp(selection.ip || history[0]?.ipAddress || "");
        setReason(""); setDays(30); setPreview(null); setResult(null); setError(""); setStep("select");
    }, [selection]);
    const supported = data?.supported && (!identifier || identifier === data.server || identifier === data.serverName);
    const mutate = async body => {
        if (busy || !data?.csrf) return null;
        setBusy(true); setError("");
        try {
            const response = await mutateIpModeration(data.csrf, {server: data.server, ...body});
            if (response.error) { setError(errorText(response.error)); return null; }
            await refresh();
            return response.data;
        } finally { setBusy(false); }
    };
    const close = () => { if (!busy) { onSelection(null); setUndo(null); setResult(null); setError(""); } };
    const act = async action => {
        const value = await mutate(action === "preview" ? {action, ip} : {action: "ban", previewId: preview.id, reason});
        if (!value) return;
        if (action === "preview") { setPreview(value); setStep("preview"); }
        else { setResult(value); setStep("result"); setActiveTab("operations"); }
    };
    if (!canManage && !canUndo) return <Alert variant="info" className="mt-3">
        关联账号处置与观察尚未授权。请在 Plan 网页「用户组」中向受信任管理员授予
        <code> manage.ip.moderation </code>，然后重新登录。LuckPerms 游戏权限不会赋予 Plan 网页管理权限。
    </Alert>;
    const addresses = [...new Set([selection?.ip, ...records.filter(record => record.playerUUID === selection?.record.playerUUID)
        .map(record => record.ipAddress)].filter(Boolean))];
    const isBan = selection?.kind === "ban";
    return <Card className="ip-moderation-card mt-3">
        <Card.Header className="ip-moderation-header">
            <div className="ip-moderation-heading">
                <span className="ip-moderation-heading-icon"><Fa icon={faShieldHalved}/></span>
                <div><h6>关联账号处置与观察</h6><p>核查线索 · 管理观察 · 追溯操作</p></div>
            </div>
            <Button variant="outline-secondary" size="sm" disabled={busy} onClick={refresh} className="ip-moderation-refresh">
                <Fa icon={faRotate}/> 刷新
            </Button>
        </Card.Header><Card.Body className="ip-moderation-body">
            {error && !selection && !undo && <Alert variant="danger">{error}</Alert>}
            {!data && <div className="ip-moderation-loading" role="status"><Spinner animation="border" size="sm"/> 正在加载管理记录…</div>}
            {data && !supported && <Alert variant="warning">封禁仅支持当前 Leaves/Paper 实例：{data.serverName}。请进入目标服务器自己的 Plan 面板。</Alert>}
            <div className="ip-moderation-notice"><Fa icon={faCircleInfo}/><p><strong>所有处置均需人工确认</strong>
                共享 IP 不代表同一人，IP 封禁还会影响该地址下未记录的账号。请先核实，再执行。</p></div>
            {data && <Tabs id="ip-moderation-tabs" activeKey={activeTab} onSelect={setActiveTab} transition={false} className="ip-moderation-tabs">
                <Tab eventKey="alerts" title={<TabTitle icon={faBell} label="待核查提示" count={data.alerts.length}/> }>
                    <div className="ip-moderation-section-intro"><h6>需要人工核查的线索</h6><p>仅展示观察对象的新 IP 或关联活动，不会自动封禁。</p></div>
                    {data.subnetAlerts && <Alert variant="warning">IPv4 /24 网段提示已开启，仅为低可信线索，不会自动封禁。</Alert>}
                    {data.alerts.length ? <div className="ip-moderation-record-list">{data.alerts.map(alert => <article className="ip-moderation-record is-alert" key={alert.key}>
                        <div className="ip-moderation-record-top"><div><strong className="ip-moderation-record-title">{alert.name}</strong>
                            <span className="ip-moderation-address ip-moderation-mono">{alert.ip}</span></div>
                            <span className="ip-moderation-status is-warning">待核查</span></div>
                        <dl className="ip-moderation-meta"><div><dt>核查依据</dt><dd>{alert.evidence}</dd></div>
                            <div><dt>观察对象</dt><dd>{alert.watchedName}</dd></div><div><dt>最近出现</dt><dd>{time(alert.lastSeen)}</dd></div></dl>
                        {alert.note && <p className="ip-moderation-note"><span>观察备注</span>{alert.note}</p>}
                        {canManage && <div className="ip-moderation-record-actions"><Button size="sm" variant="outline-danger" disabled={!supported || busy}
                            onClick={() => onSelection({kind: "ban", ip: alert.ip, record: {playerUUID: alert.uuid, playerName: alert.name}})}>核查并封禁</Button>
                            <Button size="sm" variant="outline-secondary" disabled={busy} onClick={() => mutate({action: "dismiss", alertKey: alert.key})}>忽略本次</Button></div>}
                    </article>)}</div> : <EmptyState icon={faCheck} title="暂无待核查提示">后续出现新的 IP 或关联活动时，线索会展示在这里。</EmptyState>}
                </Tab>
                <Tab eventKey="watches" title={<TabTitle icon={faEye} label="观察名单" count={data.watches.length}/> }>
                    <div className="ip-moderation-section-intro"><h6>正在关注的账号</h6><p>在上方玩家列表中点击「加入观察」，设置备注和期限。</p></div>
                    {data.watches.length ? <div className="ip-moderation-watch-grid">{data.watches.map(watch => <article className="ip-moderation-record" key={watch.uuid}>
                        <div className="ip-moderation-record-top"><strong className="ip-moderation-record-title">{watch.name}</strong>
                            <span className={`ip-moderation-status ${watch.expires < Date.now() ? "is-neutral" : "is-success"}`}>{watch.expires < Date.now() ? "已过期" : "观察中"}</span></div>
                        <p className="ip-moderation-note"><span>观察备注</span>{watch.note}</p>
                        <dl className="ip-moderation-meta"><div><dt>截止时间</dt><dd>{time(watch.expires)}</dd></div></dl>
                        {canManage && <div className="ip-moderation-record-actions"><Button size="sm" variant="outline-secondary" disabled={busy}
                            onClick={() => mutate({action: "unwatch", uuid: watch.uuid})}>解除观察</Button></div>}
                    </article>)}</div> : <EmptyState icon={faEye} title="尚未添加观察账号">观察只记录线索，不会对玩家自动执行处罚。</EmptyState>}
                </Tab>
                <Tab eventKey="operations" title={<TabTitle icon={faClockRotateLeft} label="处置记录" count={data.operations.length}/> }>
                    <div className="ip-moderation-section-intro"><h6>封禁与撤销记录</h6><p>展开详情查看逐项结果；撤销只解除本次新增且未被修改的封禁。</p></div>
                    {data.operations.length ? <div className="ip-moderation-record-list">{data.operations.slice(0, limit).map(operation => <article className="ip-moderation-record" key={operation.id}>
                        <div className="ip-moderation-record-top"><div><span className="ip-moderation-eyebrow">处置 IP</span>
                            <strong className="ip-moderation-record-title ip-moderation-mono">{operation.ip}</strong></div><Status status={operation.status}/></div>
                        <dl className="ip-moderation-meta"><div><dt>操作时间</dt><dd>{time(operation.created)}</dd></div>
                            <div><dt>执行管理员</dt><dd>{operation.actor}</dd></div><div><dt>涉及对象</dt><dd>{operation.items.length} 项</dd></div></dl>
                        <p className="ip-moderation-note"><span>封禁说明</span>{operation.reason}</p>
                        <div className="ip-moderation-record-actions"><Button size="sm" variant="outline-primary" aria-expanded={expanded === operation.id}
                            onClick={() => setExpanded(expanded === operation.id ? null : operation.id)}><Fa icon={expanded === operation.id ? faChevronUp : faChevronDown}/> {expanded === operation.id ? "收起详情" : "详情"}</Button>
                            {canUndo && operation.status !== "UNDONE" && <Button size="sm" variant="outline-warning" disabled={!supported || busy}
                                onClick={() => {setError(""); setUndo(operation); setResult(null);}}>撤销</Button>}</div>
                        {expanded === operation.id && <div className="ip-moderation-details"><p className="ip-moderation-operation-id">操作 ID：{operation.id}</p>
                            {operation.undoneBy && <p className="ip-moderation-operation-id">撤销人：{operation.undoneBy} · {time(operation.undoneAt)}</p>}
                            <Items operation={operation}/></div>}
                    </article>)}</div> : <EmptyState icon={faClockRotateLeft} title="暂无处置记录">封禁操作及其执行结果会留存于此，便于核查与撤销。</EmptyState>}
                    {data.operations.length > limit && <div className="ip-moderation-more"><Button size="sm" variant="outline-secondary" onClick={() => setLimit(limit + 20)}>显示更多记录</Button></div>}
                </Tab>
            </Tabs>}
        </Card.Body>
        <Card.Footer className="ip-moderation-footer"><span><Fa icon={faRotate}/> 每 30 秒自动刷新 · 操作窗口打开时暂停</span>
            <span>仅处理当前服务器实例</span></Card.Footer>
        <Modal className="ip-moderation-modal" show={!!selection} onHide={close} size="lg" backdrop={busy ? "static" : true} keyboard={!busy}>
            <Modal.Header closeButton={!busy}><Modal.Title>{isBan ? "按 IP 整组封禁关联账号" : "加入观察名单"}</Modal.Title></Modal.Header>
            <Modal.Body>{error && <Alert variant="danger">{error}</Alert>}
                <p>目标服务器：<strong>{data?.serverName}</strong> · 玩家：<strong>{selection?.record.playerName}</strong></p>
                {isBan && step === "select" && <><Form.Label>本次封禁的精确 IP</Form.Label>
                    <Form.Select aria-label="选择封禁 IP" value={ip} onChange={event => setIp(event.target.value)}>{addresses.map(address => <option key={address}>{address}</option>)}</Form.Select>
                    <p className="ip-intelligence-muted mt-2">默认选择最近记录的 IP。历史地址可能已被重新分配，请核实后继续。</p></>}
                {isBan && preview && step !== "result" && <><Alert variant="warning">IP：{preview.ip} · 以下 {preview.accounts.length} 个账号将全部封禁，不可排除单个账号。该 IP 下其它用户也会被限制。</Alert>
                    <Table responsive size="sm"><thead><tr><th>账号</th><th>UUID</th><th>该 IP 最近出现</th></tr></thead><tbody>{preview.accounts.map(account => <tr key={account.uuid}>
                        <td>{account.name}</td><td><small>{account.uuid}</small></td><td>{time(account.lastSeen)}</td></tr>)}</tbody></Table></>}
                {(isBan && step === "reason" || !isBan) && <><Form.Label htmlFor="moderation-reason">{isBan ? "封禁说明（必填）" : "观察备注（必填）"}</Form.Label>
                    <Form.Control id="moderation-reason" value={reason} maxLength={200} autoComplete="off" onChange={event => setReason(event.target.value)}/>
                    {!isBan && <><Form.Label htmlFor="moderation-days" className="mt-2">观察天数（1–365）</Form.Label><Form.Control id="moderation-days" type="number" min={1} max={365} value={days} onChange={event => setDays(event.target.value)}/></>}</>}
                {isBan && step === "confirm" && <Alert variant="danger">最终确认：在 {data?.serverName} 封禁上述全部账号和 IP {preview.ip}。<br/>理由：{reason}<br/>原有封禁保持原样；可在记录中撤销本次新增封禁。</Alert>}
                {isBan && step === "result" && result && <><Alert variant="info">请逐项查看执行结果。流程完成不代表每一项均成功。</Alert><Items operation={result}/></>}
            </Modal.Body><Modal.Footer><Button variant="secondary" disabled={busy} onClick={close}>{step === "result" ? "关闭" : "取消"}</Button>
                {isBan && step === "select" && <Button disabled={!supported || busy || !ip} onClick={() => act("preview")}>预览全部账号</Button>}
                {isBan && step === "preview" && <Button onClick={() => setStep("reason")}>下一步：填写说明</Button>}
                {isBan && step === "reason" && <Button disabled={!reason.trim()} onClick={() => setStep("confirm")}>查看最终确认</Button>}
                {isBan && step === "confirm" && <Button variant="danger" disabled={busy} onClick={() => act("ban")}>{busy ? "执行中…" : "确认执行整组封禁"}</Button>}
                {!isBan && <Button disabled={busy || !reason.trim() || Number(days) < 1 || Number(days) > 365} onClick={async () => {
                    if (await mutate({action: "watch", uuid: selection.record.playerUUID, note: reason, days: Number(days)})) {
                        setActiveTab("watches"); onSelection(null);
                    }
                }}>确认观察</Button>}
            </Modal.Footer>
        </Modal>
        <Modal className="ip-moderation-modal" show={!!undo || !!result && !selection} onHide={close} size="lg" backdrop={busy ? "static" : true} keyboard={!busy}>
            <Modal.Header closeButton={!busy}><Modal.Title>{undo ? "确认撤销本次新增封禁" : "撤销结果"}</Modal.Title></Modal.Header>
            <Modal.Body>{error && <Alert variant="danger">{error}</Alert>}
                {undo && <><Alert variant="warning">原有封禁不解除，后来被修改的处罚保留并报告冲突。</Alert><Items operation={undo}/></>}
                {!undo && result && <Items operation={result}/>}</Modal.Body>
            <Modal.Footer><Button variant="secondary" disabled={busy} onClick={close}>关闭</Button>{undo && <Button variant="warning" disabled={busy} onClick={async () => {
                const value = await mutate({action: "undo", operationId: undo.id});
                if (value) { setResult(value); setUndo(null); }
            }}>{busy ? "撤销中…" : "确认撤销"}</Button>}</Modal.Footer>
        </Modal>
    </Card>;
};
export default IpModerationPanel;
