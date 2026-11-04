// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0
/** Presentation contract ONLY. Not a backend DTO. Source: signed-off design handoff. */
export const STEPS = ['SUBMITTED', 'TRIAGED', 'IN_PROGRESS', 'RESOLVED', 'CONFIRMED'] as const;
export type WorkflowStep = (typeof STEPS)[number];
export type ServiceKind = 'helpdesk.v1' | 'facility.v1';
export type Priority = 'Cao' | 'Bình thường';
export type UserView = 'employee' | 'processor';
export type UiScenario = 'normal' | 'loading' | 'empty' | 'read-error' | 'success' | 'unknown';
export type Action = { id: string; label: string };

export interface RequestRow {
  id: string;
  service: ServiceKind;
  title: string;
  status: WorkflowStep;
  priority: Priority;
  requester: string;
  updatedText: string;
  /** No server response was supplied for allowedActions. Empty means don't permit writes. */
  allowedActions: readonly Action[];
  /** Only supply a version when documented in the input; NEVER derive it from status. */
  expectedVersion?: number;
}
export interface AuditRow { timestamp: string; description: string; actor: string }
export const DEMO_ROWS: readonly RequestRow[] = [
  {id:'DX-1048', service:'helpdesk.v1', title:'Cấp quyền truy cập kho mã nguồn dự án', status:'SUBMITTED', priority:'Cao', requester:'Ngọc Ánh', updatedText:'09:15 20/10/2026', allowedActions:[]},
  {id:'DX-1047', service:'facility.v1', title:'Điều hòa phòng họp Lotus không làm mát', status:'IN_PROGRESS', priority:'Cao', requester:'Minh Anh', updatedText:'11:16 20/10/2026', allowedActions:[]},
  {id:'DX-1046', service:'helpdesk.v1', title:'Cài đặt môi trường phát triển cho laptop mới', status:'IN_PROGRESS', priority:'Bình thường', requester:'Ngọc Ánh', updatedText:'11:17 19/10/2026', expectedVersion:2, allowedActions:[]},
  {id:'DX-1045', service:'facility.v1', title:'Chuẩn bị phòng họp cho buổi demo OLP', status:'TRIAGED', priority:'Bình thường', requester:'Ngọc Ánh', updatedText:'10:18 19/10/2026', allowedActions:[]},
  {id:'DX-1044', service:'helpdesk.v1', title:'Không kết nối được mạng Wi-Fi nội bộ', status:'CONFIRMED', priority:'Bình thường', requester:'Hoàng Nam', updatedText:'12:19 18/10/2026', allowedActions:[]},
  {id:'DX-1043', service:'facility.v1', title:'Thay đèn khu vực làm việc tầng 3', status:'SUBMITTED', priority:'Bình thường', requester:'Ngọc Ánh', updatedText:'09:20 18/10/2026', allowedActions:[]},
  {id:'DX-1042', service:'helpdesk.v1', title:'Thiết lập tài khoản công cụ thiết kế', status:'CONFIRMED', priority:'Bình thường', requester:'Ngọc Ánh', updatedText:'17/10/2026', allowedActions:[]},
  {id:'DX-1040', service:'helpdesk.v1', title:'Kiểm tra máy in phòng hành chính', status:'SUBMITTED', priority:'Bình thường', requester:'Thu Hà', updatedText:'16/10/2026', allowedActions:[]},
];
export const AUDIT_DX1046: readonly AuditRow[] = [
  {timestamp:'09:17 19/10/2026', description:'Khởi tạo v0', actor:'Ngọc Ánh'},
  {timestamp:'10:17 19/10/2026', description:'SUBMITTED → TRIAGED v1', actor:'Lê Xuân Bảo'},
  {timestamp:'11:17 19/10/2026', description:'TRIAGED → IN_PROGRESS v2', actor:'Nhóm IT Support'},
];
export const SERVICE_LABELS: Record<ServiceKind,string> = {'helpdesk.v1':'Hỗ trợ CNTT','facility.v1':'Cơ sở vật chất'};
export const ISSUE_ROWS: readonly {title:string;issue:string;detail:string}[] = [
 {title:'Identity / SSO',issue:'#24',detail:'Chờ OIDC Authorization Code + PKCE'},
 {title:'API Gateway',issue:'#22 / #23',detail:'Chưa xác nhận đường đi API đáng tin cậy'},
 {title:'Workflow Engine',issue:'#22 / #23 / #24',detail:'Chưa có bằng chứng LIVE cho toàn bộ đường đi'},
 {title:'Data Management',issue:'#25 / #26',detail:'PostgreSQL 17 và Storage: chờ xác minh kết nối'},
 {title:'Tệp đính kèm',issue:'#26',detail:'Storage API có kiểm quyền chưa nghiệm thu'},
 {title:'OpenAPI / DTO mapping',issue:'#37',detail:'Hợp đồng DTO chưa duyệt cho tích hợp Portal'},
 {title:'HTTP adapter (9 routes)',issue:'PR #63',detail:'Trong baseline thiết kế: fake transport, không tuyên bố LIVE'},
];
export const progressIndex = (status:WorkflowStep) => STEPS.indexOf(status)+1;
export const isEditable = (element:Element|null) => Boolean(element?.closest('input, textarea, select, [contenteditable="true"], [role="combobox"]'));
/** Safeguard used by integration adapters, never infer an action from status or role. */
export function canAttemptAction(row:RequestRow, actionId:string):boolean {
  return Number.isInteger(row.expectedVersion) && row.allowedActions.some(a=>a.id===actionId);
}
export function visibleRows(kind:'all'|'mine'|'queue', query='') {
 const base=DEMO_ROWS.filter(row=> kind==='mine' ? row.requester==='Ngọc Ánh' : kind==='queue' ? row.status!=='CONFIRMED' : true);
 const needle=query.trim().toLocaleLowerCase('vi-VN');
 return needle ? base.filter(r=>[r.id,r.title,SERVICE_LABELS[r.service],r.requester].some(s=>s.toLocaleLowerCase('vi-VN').includes(needle))) : base;
}
