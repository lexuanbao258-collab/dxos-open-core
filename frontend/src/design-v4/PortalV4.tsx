// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0
/**
 * DX-OS Service Workspace visual-design candidate.
 * ISOLATED MOCK-ONLY preview. No RequestService, no HttpApiClient, no credentials,
 * no network side effects and no approval actions.
 * Integrate under a dedicated preview route, not over the operational portal.
 */
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Home, List, Inbox, History, Layers, UserRound, Menu, Search, Plus, ArrowRight, ShieldCheck, X } from 'lucide-react';
import {
  DEMO_ROWS, AUDIT_DX1046, ISSUE_ROWS, STEPS, SERVICE_LABELS, progressIndex,
  type RequestRow, type UserView, type UiScenario,
} from './model';
import './portal-v4.css';

type Page = 'overview'|'requests'|'mine'|'queue'|'history'|'create'|'integration'|'session'|'detail';
const PAGE_LABELS: Record<Page,string> = {
  overview:'Tổng quan', requests:'Danh sách yêu cầu', mine:'Yêu cầu của bạn',
  queue:'Bàn xử lý', history:'Lịch sử & dấu vết', create:'Tạo yêu cầu',
  integration:'Trạng thái tích hợp',session:'Phiên demo', detail:'Chi tiết yêu cầu',
};
const NAV = [
  {page:'overview',label:'Tổng quan',Icon:Home}, {page:'requests',label:'Danh sách yêu cầu',Icon:List},
  {page:'queue',label:'Bàn xử lý',Icon:Inbox}, {page:'history',label:'Lịch sử & dấu vết',Icon:History},
  {page:'integration',label:'Trạng thái tích hợp',Icon:Layers}, {page:'session',label:'Phiên demo',Icon:UserRound},
] as const;
const STAGE_NAMES=['Đã gửi','Đã tiếp nhận','Đang xử lý','Đã giải quyết','Đã xác nhận'];
const ISSUE_TAG=(issue:string)=>`BLOCKED · ${issue}`;

function Progress({row}:{row:RequestRow}) {
  const index=progressIndex(row.status);
  return <div className="progress" aria-label={`${STAGE_NAMES[index-1]}, bậc ${index} trên 5`}>
    <div className="bar-track" aria-hidden="true">{STEPS.map((s,i)=><span key={s} className={i<index?'filled':''}/>)}</div>
    <span className="state-code">{row.status}</span>
  </div>;
}
function Heading({eyebrow,title,description,action}:{eyebrow:string;title:string;description?:string;action?:ReactNode}) {
  return <div className="page-head"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1>{description&&<p className="page-lede">{description}</p>}</div>{action&&<div className="page-actions">{action}</div>}</div>;
}
function QAStates({value,onChange}:{value:UiScenario;onChange:(s:UiScenario)=>void}) {
  return <div className="page-actions"><label className="quiet-label" htmlFor="v4-qa-state">TRẠNG THÁI UI</label>
    <select id="v4-qa-state" className="state-select" value={value} onChange={e=>onChange(e.currentTarget.value as UiScenario)}>
      <option value="normal">Bình thường</option><option value="loading">Đang tải</option><option value="empty">Rỗng</option>
      <option value="read-error">Lỗi đọc</option><option value="success">Gửi thành công</option><option value="unknown">Kết quả chưa xác định</option>
    </select>
  </div>;
}
function EmptyNotice({scenario,onReset}:{scenario:UiScenario;onReset:()=>void}) {
  const cases:Record<Exclude<UiScenario,'normal'>,[string,string]>={
    loading:['Đang tải dữ liệu mô phỏng','Không phát sinh yêu cầu mạng trong thiết kế này.'],
    empty:['Chưa có dữ liệu','Hãy xóa bộ lọc hoặc quay lại tất cả yêu cầu.'],
    'read-error':['Không thể tải yêu cầu','Đây là tình huống lỗi đọc, không phải lỗi API thực.'],
    success:['Đã kiểm tra biểu mẫu','Đây là trạng thái giao diện thử nghiệm, không có lệnh ghi server.'],
    unknown:['Kết quả ghi chưa xác định','Khóa thao tác ghi. Kiểm tra lịch sử, đối chiếu phiên bản và không gửi lại tự động.'],
  };
  const [title,desc]=cases[scenario==='normal'?'empty':scenario];
  return <div className={`notice-state ${scenario==='unknown'?'warn':''}`} role={scenario==='loading'?'status':'alert'}>
    <h2>{title}</h2><p>{desc}</p>{scenario==='unknown'&&<code>COMMIT_OUTCOME_UNKNOWN · WRITE LOCKED</code>}
    <div><button type="button" className="button" onClick={onReset}>Trở về dữ liệu mẫu</button></div>
  </div>;
}

export function PortalV4() {
  const [page,setPage]=useState<Page>('overview');
  const [role,setRole]=useState<UserView>('employee');
  const [activeId,setActiveId]=useState('DX-1046');
  const [selected,setSelected]=useState('DX-1048');
  const [query,setQuery]=useState('');
  const [service,setService]=useState('all');
  const [status,setStatus]=useState('all');
  const [scenario,setScenario]=useState<UiScenario>('normal');
  const [drawer,setDrawer]=useState(false);
  const [mobile,setMobile]=useState(()=>typeof window!=='undefined'&&window.matchMedia('(max-width: 800px)').matches);
  const [notice,setNotice]=useState('');
  const previousFocus=useRef<HTMLElement|null>(null);
  const noteClose=useRef<HTMLButtonElement>(null);
  const modalRef=useRef<HTMLDialogElement>(null);
  const searchRef=useRef<HTMLInputElement>(null);
  const mainRef=useRef<HTMLElement>(null);
  const navRef=useRef<HTMLElement>(null);
  const current=DEMO_ROWS.find(row=>row.id===activeId) ?? DEMO_ROWS[0];

  const filtered=(section:'all'|'mine'|'queue')=>DEMO_ROWS.filter(row=>{
    if(section==='mine'&&row.requester!=='Ngọc Ánh')return false;
    if(section==='queue'&&row.status==='CONFIRMED')return false;
    if(service!=='all'&&row.service!==service)return false;
    if(status!=='all'&&row.status!==status)return false;
    const q=query.trim().toLocaleLowerCase('vi-VN');
    return !q||[row.id,row.title,row.requester,SERVICE_LABELS[row.service]].some(s=>s.toLocaleLowerCase('vi-VN').includes(q));
  });
  const go=(p:Page,id?:string)=>{
    if(id)setActiveId(id);
    setPage(p);setScenario('normal');setQuery('');setService('all');setStatus('all');setDrawer(false);
    window.requestAnimationFrame(()=>mainRef.current?.focus());
  };
  const openNotice=(message:string)=>{previousFocus.current=document.activeElement as HTMLElement;setNotice(message);};
  useEffect(()=>{if(notice){if(modalRef.current&&!modalRef.current.open)modalRef.current.showModal();noteClose.current?.focus();}else if(previousFocus.current?.isConnected){previousFocus.current.focus();}},[notice]);
  useEffect(()=>{const query=window.matchMedia('(max-width: 800px)');const update=()=>{setMobile(query.matches);if(!query.matches)setDrawer(false);};update();query.addEventListener('change',update);return()=>query.removeEventListener('change',update);},[]);
  useEffect(()=>{
    function onKey(e:KeyboardEvent){
      const target=e.target as HTMLElement|null;
      const edit=Boolean(target?.closest('input,select,textarea,[contenteditable="true"]'));
      if(e.key==='Escape'){if(notice){setNotice('');return;}if(drawer){setDrawer(false);return;}}
      if(e.key==='/'&&!edit&&!notice){e.preventDefault();if(searchRef.current&&searchRef.current.getClientRects().length>0)searchRef.current.focus();else{go('requests');window.requestAnimationFrame(()=>document.querySelector<HTMLInputElement>('input[aria-label="Tìm yêu cầu"]')?.focus());}return;}
      if(page!=='queue'||edit||notice||e.ctrlKey||e.altKey||e.metaKey)return;
      const data=filtered('queue');const i=Math.max(0,data.findIndex(r=>r.id===selected));
      if(e.key.toLowerCase()==='j'||e.key.toLowerCase()==='k'){
        e.preventDefault();const next=data[Math.min(data.length-1,Math.max(0,i+(e.key.toLowerCase()==='j'?1:-1)))];
        if(next){setSelected(next.id);window.requestAnimationFrame(()=>document.getElementById('v4-'+next.id)?.focus());}return;
      }
      if(e.key==='Enter'&&data.length){e.preventDefault();go('detail',selected);return;}
      if(e.key.toLowerCase()==='e'){e.preventDefault();openNotice('Không có allowedActions từ API trong dữ liệu bàn giao. Không được suy ra quyền từ vai trò hoặc trạng thái.');}
    }
    document.addEventListener('keydown',onKey);return()=>document.removeEventListener('keydown',onKey);
  // Keyboard shortcuts follow the current demonstration state, never perform writes.
  });
  useEffect(()=>{if(drawer){navRef.current?.querySelector<HTMLButtonElement>('.nav-link')?.focus();}},[drawer]);

  const requestAction=<button className="button primary" onClick={()=>go('create')}><Plus size={16} aria-hidden="true"/>Tạo yêu cầu</button>;
  const table=(data:readonly RequestRow[],countLabel:string)=><section className="panel">
    <div className="panel-head"><h2>{countLabel} <span className="sub-count">{String(data.length).padStart(2,'0')}</span></h2>
      {page==='overview'&&<button className="mini-link" onClick={()=>go('requests')}>Xem tất cả →</button>}</div>
    {(page==='requests'||page==='mine'||page==='queue')&&<div className="filter-bar"><label className="field-search"><Search size={15} aria-hidden="true"/><input aria-label="Tìm yêu cầu" placeholder="Tìm mã, tiêu đề, người gửi…" value={query} onChange={e=>setQuery(e.currentTarget.value)}/></label>
      <label><span className="sr-only">Dịch vụ</span><select className="select-box" value={service} onChange={e=>setService(e.currentTarget.value)}><option value="all">Tất cả dịch vụ</option><option value="helpdesk.v1">Hỗ trợ CNTT</option><option value="facility.v1">Cơ sở vật chất</option></select></label>
      <label><span className="sr-only">Trạng thái</span><select className="select-box" value={status} onChange={e=>setStatus(e.currentTarget.value)}><option value="all">Tất cả trạng thái</option>{STEPS.map(s=><option key={s} value={s}>{s}</option>)}</select></label></div>}
    {data.length?<table className="request-table"><caption className="sr-only">Danh sách yêu cầu mô phỏng</caption><thead><tr><th>YÊU CẦU</th><th>DỊCH VỤ</th><th>TIẾN ĐỘ</th><th>ƯU TIÊN</th><th>CẬP NHẬT</th><th><span className="sr-only">Chi tiết</span></th></tr></thead><tbody>{data.map(r=><tr key={r.id} className={page==='queue'&&selected===r.id?'selected':''}>
      <td><div className="req-main"><span className="req-id">{r.id}</span><button id={'v4-'+r.id} className="req-title" onClick={()=>go('detail',r.id)}>{r.title}</button><span className="mobile-sub">{SERVICE_LABELS[r.service]} · {r.requester}</span></div><div className="mobile-meta">{r.updated} · {r.priority}</div></td>
      <td><span className="service-name">{SERVICE_LABELS[r.service]}</span></td><td><Progress row={r}/></td><td><span className={r.priority==='Cao'?'priority-high':'priority-normal'}>{r.priority}</span></td><td><small>{r.updated}</small></td><td><button className="row-open" onClick={()=>go('detail',r.id)} aria-label={`Xem chi tiết ${r.id}`}>→</button></td>
    </tr>)}</tbody></table>:<div className="empty-state"><h2>Không có yêu cầu phù hợp</h2><p>Thử thay đổi từ khóa hoặc bộ lọc.</p></div>}
    <div className="table-foot"><span>Chỉ dữ liệu MOCK · không có quyền ghi LIVE</span><span>{data.length} yêu cầu</span></div>
  </section>;
  const coreCard=<section className="panel context-panel"><div className="context-label">KIẾN TRÚC ỨNG DỤNG</div><h2>Một lõi, hai quy trình.</h2><p>Helpdesk và Facility dùng chung mô hình trạng thái trong bản trình diễn; chưa phải bằng chứng E2E.</p><div className="core-visual"><div className="core-node">DX-OS Workflow Core<small>SHARED CAPABILITY</small></div><div className="core-stem"/><div className="core-services"><div>Hỗ trợ CNTT<small>helpdesk.v1</small></div><div>Cơ sở vật chất<small>facility.v1</small></div></div></div><div className="context-foot">Sơ đồ logic · không khẳng định backend LIVE</div></section>;
  const riskCard=<section className="panel context-panel risk-panel"><div className="context-label">TRẠNG THÁI TRIỂN KHAI</div><h2>LIVE còn bị chặn</h2><p>SSO, Gateway, Workflow, Storage và API contract còn điều kiện nghiệm thu.</p><div className="risk-links"><span>#22 / #23</span><span>#24</span><span>#25 / #26</span><span>#37</span></div><button onClick={()=>go('integration')}>Chi tiết tích hợp →</button></section>;

  const overview=<><Heading eyebrow="WORKSPACE / TỔNG QUAN" title="Không gian dịch vụ" description="Hai nhóm hỗ trợ, một nơi để theo dõi yêu cầu." action={requestAction}/>
    <div className="inline-metrics"><span><strong>08</strong> yêu cầu mẫu</span><span className="metric-separator"/><span><strong>03</strong> đã gửi</span><span className="metric-separator"/><span><strong>02</strong> đang xử lý</span><span className="strip-tail">2 dịch vụ / 1 Workflow Core (mô hình)</span></div>
    <div className="content-split">{table(role==='employee'?filtered('mine'):filtered('all'),'Yêu cầu trong phiên demo')}<div className="aside-stack">{coreCard}{riskCard}</div></div></>;
  const listSection=page==='queue'?'queue':page==='mine'?'mine':'all';
  const listing=<><Heading eyebrow={`SERVICE REQUESTS / ${listSection.toUpperCase()}`} title={PAGE_LABELS[page]} description={page==='queue'?'Các yêu cầu chưa đến bước xác nhận cuối. Chỉ cho phép hành động do API cấp rõ ràng.':'Tìm kiếm, lọc và mở yêu cầu để xem tiến độ.'} action={requestAction}/>
    <QAStates value={scenario} onChange={setScenario}/><div className="section-gap">{scenario==='normal'?table(filtered(listSection),'Tất cả yêu cầu'):<EmptyNotice scenario={scenario} onReset={()=>setScenario('normal')}/>}</div>
    {page==='queue'&&<p className="key-help">Phím tắt: <kbd>J</kbd>/<kbd>K</kbd> chọn dòng · <kbd>Enter</kbd> mở chi tiết · <kbd>E</kbd> xem quyền · <kbd>Esc</kbd> đóng thông báo.</p>}</>;

  const detail=<><button className="back-link" onClick={()=>go('requests')}>← Trở về danh sách yêu cầu</button>
    <div className="detail-top"><div><div className="detail-id">{current.id} / {current.service}</div><h1 className="detail-title">{current.title}</h1><p className="detail-sub">{SERVICE_LABELS[current.service]} · Người gửi {current.requester} · cập nhật {current.updatedText}</p></div><span className="env-chip">MOCK / DEV</span></div>
    <section className="panel"><div className="panel-head"><h2>Tiến độ xử lý</h2><span className="state-code">{current.status}</span></div><div className="stepper" role="list" aria-label="Tiến độ gồm 5 bậc">{STEPS.map((s,i)=><div className={`step ${i<progressIndex(current.status)?'done':''}`} key={s} role="listitem" aria-current={i===progressIndex(current.status)-1?'step':undefined}><div className="step-dot" aria-hidden="true"/><strong>{STAGE_NAMES[i]}</strong><small>{i+1} / 5</small></div>)}</div></section>
    <div className="detail-layout section-gap"><div className="panel"><section className="detail-block"><h2>Nội dung yêu cầu</h2><p>{current.title}. Đây là bản ghi mô phỏng dùng để kiểm tra bố cục và luồng xem chi tiết.</p></section><section className="detail-block"><h2>Dấu vết thay đổi</h2>{current.id==='DX-1046'?<ol className="ledger-list">{AUDIT_DX1046.map((event,i)=><li className="ledger-item" key={i}><div className="ledger-time">{event.timestamp}</div><div className="ledger-line"/><div className="ledger-desc"><strong>{event.description}</strong><span>{event.actor} · dữ liệu mô phỏng</span></div></li>)}</ol>:<p>Chưa được cung cấp dấu vết cụ thể cho {current.id}.</p>}</section><section className="detail-block"><h2>Tệp đính kèm</h2><div className="block-line"><div><strong>Chưa khả dụng</strong><p>Storage API có kiểm quyền chưa nghiệm thu.</p></div><span className="blocked">BLOCKED · #26</span></div></section></div>
      <aside className="aside-stack"><section className="panel context-panel"><div className="context-label">THÔNG TIN YÊU CẦU</div><h2>{current.id}</h2><dl className="detail-facts"><dt>Dịch vụ</dt><dd>{SERVICE_LABELS[current.service]}</dd><dt>Ưu tiên</dt><dd>{current.priority}</dd><dt>Người gửi</dt><dd>{current.requester}</dd><dt>Trạng thái</dt><dd>{current.status}</dd><dt>Phiên bản</dt><dd>{current.expectedVersion===undefined?'Không có trong mẫu':`v${current.expectedVersion}`}</dd><dt>Cập nhật</dt><dd>{current.updatedText}</dd></dl></section>
      <section className="panel context-panel risk-panel"><div className="context-label">THAO TÁC TRÊN YÊU CẦU</div><h2>Chưa có quyền xác thực</h2><p>Dữ liệu bàn giao không chứa <code>allowedActions</code>. Không tự suy ra quyền từ vai trò hoặc trạng thái.</p><button onClick={()=>openNotice('Để ghi an toàn cần allowedActions do server cấp, expectedVersion và xử lý CONFLICT / COMMIT_OUTCOME_UNKNOWN. Preview này không gửi lệnh.')}>Vì sao không có nút xử lý? →</button></section></aside></div></>;

  const integration=<><Heading eyebrow="PLATFORM / BOUNDARIES" title="Trạng thái tích hợp" description="Phân biệt mã đã có, MOCK UI và điều kiện nghiệm thu LIVE." action={<QAStates value={scenario} onChange={setScenario}/>}/>
    <div className="integration-intro"><div className="shield"><ShieldCheck size={20}/></div><div><strong>Frontend hiện ở chế độ MOCK / DEVELOPMENT.</strong><p>Issue được dẫn theo tài liệu bàn giao thiết kế. Owner cần đối chiếu với HEAD mới trước khi công bố.</p></div></div>
    {scenario!=='normal'?<EmptyNotice scenario={scenario} onReset={()=>setScenario('normal')}/>:<section className="panel integration-list"><div className="panel-head"><h2>Các ranh giới hệ thống</h2><span className="quiet-label">RELEASE GATES</span></div>{ISSUE_ROWS.map(row=><div className="block-line" key={row.title}><div><strong>{row.title}</strong><p>{row.detail}</p></div><span className="blocked">{ISSUE_TAG(row.issue)}</span></div>)}</section>}
  </>;
  const history=<><Heading eyebrow="AUDIT / HISTORY" title="Lịch sử & dấu vết" description="Dấu vết mẫu của DX-1046; không phải lịch sử từ Backend." action={<button className="button" onClick={()=>go('detail','DX-1046')}>Mở DX-1046</button>}/><div className="detail-layout"><section className="panel detail-block"><h2>DX-1046 · Cài đặt môi trường phát triển cho laptop mới</h2><ol className="ledger-list">{AUDIT_DX1046.map((ev,i)=><li className="ledger-item" key={i}><div className="ledger-time">{ev.timestamp}</div><div className="ledger-line"/><div className="ledger-desc"><strong>{ev.description}</strong><span>{ev.actor}</span></div></li>)}</ol></section><aside className="panel context-panel"><div className="context-label">TÍNH TOÀN VẸN</div><h2>Không phải audit thật</h2><p>Không khẳng định các sự kiện đã commit trong Workflow Core.</p></aside></div></>;
  const create=<><Heading eyebrow="CREATE / SERVICE REQUEST" title="Tạo yêu cầu mới" description="Kiểm tra biểu mẫu ở tầng trình bày; không gửi dữ liệu tới Core."/>
    <div className="form-layout"><section className="panel"><form onSubmit={(e:FormEvent<HTMLFormElement>)=>{e.preventDefault();const form=e.currentTarget;if(!form.reportValidity())return;openNotice('Biểu mẫu hợp lệ. Preview không tạo bản ghi mới và không gửi dữ liệu tới API.');}}>
      <section className="form-section"><h2>01 / Chọn loại dịch vụ</h2><div className="choice-grid"><label className="choice"><input type="radio" name="service" value="helpdesk.v1" defaultChecked/><div><strong>Hỗ trợ CNTT</strong><span>Phần mềm, thiết bị, truy cập</span></div></label><label className="choice"><input type="radio" name="service" value="facility.v1"/><div><strong>Cơ sở vật chất</strong><span>Phòng họp, điện, thiết bị</span></div></label></div></section>
      <section className="form-section"><h2>02 / Thông tin yêu cầu</h2><div className="field"><label htmlFor="v4-title">Tiêu đề *</label><input id="v4-title" required minLength={8} maxLength={120} placeholder="Ví dụ: Máy chiếu phòng họp chưa hoạt động"/></div><div className="field"><label htmlFor="v4-desc">Mô tả vấn đề *</label><textarea id="v4-desc" required minLength={20} maxLength={2000} placeholder="Mô tả vấn đề, thời điểm phát sinh và ảnh hưởng…"/></div><div className="form-columns"><div className="field"><label htmlFor="v4-location">Vị trí *</label><input id="v4-location" required maxLength={100} placeholder="Ví dụ: Tầng 3, khu làm việc"/></div><div className="field"><label htmlFor="v4-priority">Mức ưu tiên</label><select id="v4-priority"><option>Bình thường</option><option>Cao</option></select></div></div></section><div className="form-footer"><p>MOCK / DEV · Không ghi backend</p><button type="submit" className="button primary">Kiểm tra bản nháp <ArrowRight size={15}/></button></div>
      </form></section><aside className="aside-stack"><section className="form-help"><h2>Trước khi gửi</h2><p>Ghi rõ vấn đề và vị trí để nhóm hỗ trợ xác định đúng yêu cầu.</p><div className="help-divider"/><p>Phiên này không đăng nhập thực hay tạo phiếu trên server.</p></section><section className="form-help"><div className="context-label">TỆP ĐÍNH KÈM</div><h2>Chưa khả dụng</h2><p>Storage API có kiểm quyền chưa nghiệm thu.</p><span className="blocked">BLOCKED · #26</span></section></aside></div></>;
  const session=<><Heading eyebrow="ACCESS / MOCK SESSION" title="Phiên demo" description="Đổi góc nhìn mà không đăng nhập SSO thực."/><div className="detail-layout"><section className="panel detail-block"><h2>Chọn vai trò trải nghiệm</h2><div className="session-choice"><button className={role==='employee'?'active':''} onClick={()=>setRole('employee')}><strong>Ngọc Ánh · Nhân viên</strong><span>Xem yêu cầu của mình và mở biểu mẫu</span></button><button className={role==='processor'?'active':''} onClick={()=>setRole('processor')}><strong>Lê Xuân Bảo · Người xử lý</strong><span>Xem hàng đợi; không có quyền ghi LIVE</span></button></div><p className="small-note section-gap">Đổi vai trò chỉ ảnh hưởng giao diện demo.</p></section><aside className="panel context-panel risk-panel"><div className="context-label">IDENTITY BOUNDARY</div><h2>SSO / OIDC · BLOCKED</h2><p>Chưa có bằng chứng Authorization Code + PKCE trong bản preview.</p><span className="blocked">#24</span></aside></div></>;

  let content:ReactNode;
  if(page==='overview')content=overview;
  else if(['requests','mine','queue'].includes(page))content=listing;
  else if(page==='detail')content=detail;
  else if(page==='create')content=create;
  else if(page==='integration')content=integration;
  else if(page==='history')content=history;
  else content=session;

  return <div className="dxos-v4"><a className="skip-link" href="#v4-content">Đến nội dung chính</a><div className="app-shell">
    <aside id="v4-main-nav" ref={navRef} className={`sidebar ${drawer?'drawer-open':''}`} aria-label="Điều hướng chính" inert={mobile&&!drawer}>
      <div className="sidebar-head"><div className="brand-mark" aria-hidden="true"><span/><span/></div><div><div className="brand-name">DX<span>—</span>OS</div><div className="brand-caption">SERVICE WORKSPACE</div></div></div>
      <div className="space-switch"><div className="space-symbol">T</div><div className="space-copy"><strong>TriCore Workspace</strong><span>Môi trường mô phỏng</span></div><span className="space-chevron">⌄</span></div>
      <nav className="side-nav" aria-label="Các trang"><div className="nav-group-label">KHÔNG GIAN LÀM VIỆC</div>{NAV.slice(0,4).map(({page:p,label,Icon})=><button key={p} className={`nav-link ${page===p?'active':''}`} aria-current={page===p?'page':undefined} onClick={()=>go(p)}><Icon size={18} aria-hidden="true"/>{label}{p==='queue'&&<span className="nav-count">6</span>}</button>)}
        <div className="nav-group-label nav-group-gap">HỆ THỐNG</div>{NAV.slice(4).map(({page:p,label,Icon})=><button key={p} className={`nav-link ${page===p?'active':''}`} onClick={()=>go(p)}><Icon size={18} aria-hidden="true"/>{label}</button>)}</nav>
      <div className="sidebar-bottom"><div className="release-note"><span className="status-light"/><strong>LIVE chưa nghiệm thu</strong><p>Các hành động demo không gửi tới API thật.</p><button onClick={()=>go('integration')}>Xem giới hạn →</button></div><div className="sidebar-credit"><strong>TRICORE</strong><span>DX-OS Open-Core · OLP 2026</span></div></div>
    </aside><div className="app-body" inert={drawer&&mobile}><header className="topbar"><div className="top-left"><button className="header-icon mobile-menu" aria-label={drawer?'Đóng menu':'Mở menu'} aria-controls="v4-main-nav" aria-expanded={drawer} onClick={()=>setDrawer(!drawer)}>{drawer?<X size={20}/>:<Menu size={20}/>}</button><div className="top-logo-mobile">DX—OS</div><span className="crumb-root">Service Workspace</span><span className="crumb-divider">/</span><strong>{PAGE_LABELS[page]}</strong></div><div className="top-right"><label className="global-search"><Search size={16}/><input ref={searchRef} aria-label="Tìm yêu cầu toàn cục" placeholder="Tìm mã, nội dung…" value={query} onChange={e=>{if(page!=='requests')setPage('requests');setQuery(e.currentTarget.value);}}/><kbd>/</kbd></label><span className="env-chip"><span className="chip-square"/>MOCK / DEV</span><button className="account-switch" onClick={()=>go('session')} aria-label="Đổi vai trò demo"><span className="account-avatar">{role==='employee'?'NÁ':'XB'}</span><span>{role==='employee'?'Ngọc Ánh':'Lê Xuân Bảo'}</span></button></div></header><main id="v4-content" tabIndex={-1} ref={mainRef}>{content}</main><footer className="site-foot"><span>DX-OS / TRICORE</span><span>Thiết kế v4 · Prototype, không phải production</span></footer></div>
  </div>{drawer&&<button className="drawer-scrim" aria-label="Đóng điều hướng" onClick={()=>setDrawer(false)}/>}<nav className="mobile-bottom" aria-label="Điều hướng di động" inert={drawer&&mobile}><button onClick={()=>go('overview')} className={page==='overview'?'active':''}><Home size={18}/>Tổng quan</button><button onClick={()=>go('requests')} className={page==='requests'?'active':''}><List size={18}/>Yêu cầu</button><button onClick={()=>go('queue')} className={page==='queue'?'active':''}><Inbox size={18}/>Bàn xử lý</button><button onClick={()=>go('integration')} className={page==='integration'?'active':''}><Layers size={18}/>Tích hợp</button></nav>
    {notice&&<dialog ref={modalRef} className="notice-dialog" role="alertdialog" aria-modal="true" aria-labelledby="v4-notice-title" aria-describedby="v4-notice-message" onCancel={e=>{e.preventDefault();setNotice('');}}><div className="dialog-head"><h2 id="v4-notice-title">Thông tin bản demo</h2><button className="header-icon" aria-label="Đóng" onClick={()=>setNotice('')}><X size={18}/></button></div><p id="v4-notice-message">{notice}</p><div className="dialog-actions"><button ref={noteClose} className="button primary" onClick={()=>setNotice('')}>Đã hiểu</button></div></dialog>}
  </div>;
}
export default PortalV4;
