# Stage 6 D2/D3/D4 — hồ sơ independent PostgreSQL/security review

- Ngày chuẩn bị: 2026-10-27.
- Source/test worktree: `D:\olp\dxos-pr58-readonly-preflight`; source HEAD **`549de8c63442d89dd03ff71a699848c10afc9b98`**, detached, không đổi trong lần cập nhật này.
- Git status đầu lần cập nhật: đúng hai Markdown hồ sơ là untracked; SQL/Java và tracked files không có diff. Trạng thái sạch mô tả trong bản đầu là trước khi tạo hai hồ sơ, không phải trạng thái hiện tại.
- Docs worktree: `D:\olp\dxos-pr58-security-review-docs`; branch `docs/stage6-security-review-549de8c-20261027`, base/HEAD hiện vẫn là source SHA trên. **Chưa tạo commit tài liệu nên chưa có documentation commit HEAD mới.** Patch không phải commit, không được gắn kết quả test cho SHA tài liệu tương lai.
- Hai Markdown gốc và XML trong worktree kiểm thử được giữ nguyên; mọi cập nhật hồ sơ nằm ở docs worktree.
- Kết quả chuẩn bị hồ sơ: **PARTIAL**. Quyết định độc lập D2/D3/D4: **BLOCKED / CHƯA KÝ**.
- Kiểm tra source/Git/XML và đọc GitHub PR #58, review liên quan PR #50, decision Issue #23. Chỉ viết hai Markdown trong docs worktree và patch cục bộ; không chạy Gradle/JUnit/SQL, không kết nối DB/Docker/Keycloak/Supabase, không đặt approval flags, không sửa SQL/Java/grants/HTTP/LIVE, không commit/push/merge hoặc đăng GitHub handoff.
- Đây là hồ sơ để reviewer độc lập quyết định; không phải chữ ký APPROVED hoặc CHANGES REQUIRED của người chuẩn bị.

## 1. Quy ước và bằng chứng gốc

**VERIFIED**: nội dung source/artifact đã đọc và đối chiếu trong phạm vi nêu rõ. Khi dựa vào XML, chỉ xác nhận artifact ghi kết quả đó; không hàm ý đã tự chạy lại hoặc chứng thực môi trường.

**PARTIAL**: có implementation/test source hoặc một phần artifact, thiếu execution/provenance/phạm vi cần thiết. **BLOCKED**: thiếu bằng chứng/quyết định bắt buộc để đóng gate; không đồng nghĩa đã chứng minh có exploit hay DB còn rác.

Các mã nguồn bên dưới đều tại exact HEAD trên; số dòng là 1-based. Đường dẫn tương đối từ repository:

| Mã | File |
| --- | --- |
| T | [Stage6RuntimeLoginPrivilegeIntegrationTest.java](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/Stage6RuntimeLoginPrivilegeIntegrationTest.java) |
| V5 | [V5_acl_physical_candidate.sql](../../backend/src/test/resources/stage6/V5_acl_physical_candidate.sql) |
| POL | [Stage6_policy_lifecycle_candidate.sql](../../backend/src/test/resources/stage6/Stage6_policy_lifecycle_candidate.sql) |
| RD | [Stage6_guarded_read_candidate.sql](../../backend/src/test/resources/stage6/Stage6_guarded_read_candidate.sql) |
| CR | [Stage6_guarded_create_candidate.sql](../../backend/src/test/resources/stage6/Stage6_guarded_create_candidate.sql) |
| ACT | [Stage6_guarded_actions_candidate.sql](../../backend/src/test/resources/stage6/Stage6_guarded_actions_candidate.sql) |
| JDBC | [PostgresWorkflowPersistence.java](../../backend/src/main/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistence.java) |
| PORT | [Stage6WorkflowPersistence.java](../../backend/src/main/java/com/tricore/dxos/infrastructure/workflow/Stage6WorkflowPersistence.java) |
| GATE | [Stage6CreateGate.java](../../backend/src/main/java/com/tricore/dxos/application/authorization/Stage6CreateGate.java) |
| ORCH | [Stage6CreateOrchestrator.java](../../backend/src/main/java/com/tricore/dxos/application/authorization/Stage6CreateOrchestrator.java) |
| PHYTEST | [Stage6V5PhysicalCandidateIntegrationTest.java](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/Stage6V5PhysicalCandidateIntegrationTest.java) |
| LIFETEST | [Stage6PolicyLifecycleIntegrationTest.java](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/Stage6PolicyLifecycleIntegrationTest.java) |
| CRTEST | [Stage6GuardedCreateCandidateIntegrationTest.java](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/Stage6GuardedCreateCandidateIntegrationTest.java) |
| GATETEST | [Stage6CreateGateTest.java](../../backend/src/test/java/com/tricore/dxos/application/authorization/Stage6CreateGateTest.java) |
| ORCHTEST | [Stage6CreateOrchestratorTest.java](../../backend/src/test/java/com/tricore/dxos/application/authorization/Stage6CreateOrchestratorTest.java) |
| ADR | [ADR-0025](../adr/ADR-0025-proposed-mvp-trusted-backend-threat-model.md) |
| D2 | [V5 physical/privilege review contract](../contracts/issue-23-stage6-s61-v5-physical-privilege-review.md) |
| MATRIX | [PG17 acceptance matrix](issue-23-stage6-pg17-acceptance-matrix.md) |
| OLD | [Candidate evidence 2026-10-20](issue-23-stage6-candidate-evidence-20261020.md) |
| XML | `D:\olp\dxos-pr58-readonly-preflight\backend\build\test-results\test\TEST-com.tricore.dxos.infrastructure.workflow.Stage6RuntimeLoginPrivilegeIntegrationTest.xml` — local ignored artifact, không nằm trong docs patch |

### Đối chiếu JUnit

**VERIFIED — nội dung XML:** XML:2 khai báo suite `Stage6RuntimeLoginPrivilegeIntegrationTest`, `tests=88 failures=0 errors=0 skipped=0`, timestamp `2026-10-27T02:37:55.461Z`, time `28.247s`. Parse chỉ đọc trong phiên này đếm đúng **88 testcase**, không có node `failure`, `error`, `skipped`; không chỉ đọc summary banner. File dài 16.091 bytes, LastWriteTimeUtc `2026-10-27T02:38:23Z`.

SHA-256 XML: `E5167ABCCAEE1B326486C6F81228041AD79B03215076147C483BF2EE62A06FA2`.

**PARTIAL — provenance execution cục bộ:** XML thuộc `backend/build/`, Git ignore và không track; XML không tự chứa SHA/command/target/attestation. Đã có [operator report 27/10](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58#issuecomment-6432403910) ràng buộc source SHA, clean detached worktree trước test, Java 21.0.11, postgres:17.11, loopback 55427/database `stage6_disposable_test`, lệnh Gradle targeted và BUILD SUCCESSFUL. Đây là operator-reported envelope, không còn là “không có thông tin provenance”. Nội dung XML khớp số đếm report; chưa có raw log/fixture digest được xác thực độc lập và chưa có liên kết attested giữa bytes XML cục bộ với đúng execution. T:55–65 kiểm approval/user/database/PG17, không assert host/port. Không đọc env/password hoặc tự tái chạy để bù.

XML:92–94 ghi hai race quan sát được trong artifact:

| Thứ tự | first_pid | waiter_pid | observer_pid | revision / audit |
| --- | --- | --- | --- | --- |
| revoke trước (`write_first=false`) | 402 | 401 | 403 | 1 / 1 |
| write trước (`write_first=true`) | 406 | 407 | 408 | 1 / 1 |

Mỗi hàng có ba PID khác nhau. T:522–554 kiểm tra blocker bằng `pg_blocking_pids`, fresh observer và trạng thái cuối; không dựa vào sleep đơn thuần.

**PARTIAL — cleanup 0 schema / 0 role, operator-reported:** [comment operator 6432403910](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58#issuecomment-6432403910) ghi fresh read-only postcheck trên cùng named disposable container: `SELECT nspname FROM pg_namespace WHERE nspname LIKE 's6_login_%'` → **0 rows**; `SELECT rolname FROM pg_roles WHERE rolname ~ '^s6_(rt|owner|prov|other)_'` → **0 rows**. Đã xác minh comment chứa kết quả này và phù hợp prefixes T:66–71, cleanup T:145–158; XML không báo teardown error. Không tự truy vấn DB, không có raw catalog transcript/target-instance attestation nên **không xác nhận độc lập hai số 0**. XML:92–100 không chứa postcheck. Review độc lập 25/10 mô tả dọn container/port là execution khác, không thay thế catalog postcheck 27/10. Không suy diễn có schema/role rác; gap còn lại là evidence chất lượng/đối chiếu, không phải “operator chưa cung cấp cleanup”.

### Review GitHub hiện có — tách đúng người, thời điểm và scope

Đã đọc metadata, toàn timeline PR #58, review submissions và review threads bằng connector GitHub ngày 27/10/2026:

| Nguồn | Nội dung đã đối chiếu | Phạm vi và trạng thái |
| --- | --- | --- |
| [PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58) | head `549de8c63442d89dd03ff71a699848c10afc9b98`; base `db6b1a8007d2cf4720e9dff6996572a1e819bc49`; 2 files +50/−0; open/Draft, chưa merged | VERIFIED metadata tại lúc đọc, không phải approve |
| [Review 5611747462, 22/10](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58#pullrequestreview-5611747462) | lexuanbao258-collab: COMMENTED, test-only source acceptance, ghi nhận operator output | Không phải qualified independent human PG/security sign-off; patch hash trong review chỉ là giá trị review báo, không được dùng làm hash XML/docs |
| [Yến 6394092887, 25/10](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58#issuecomment-6394092887) | Phan Hải Yến/phanyen06ym-ien: exact HEAD, focused verification PASS; báo tự chạy targeted 1 + suite 88, Java21/Gradle8.14.3/PG17.11; test-only candidate accepted with limitations | VERIFIED sự tồn tại/nội dung kết luận reviewer; không phải phiên này tự reproduce hoặc production PG sign-off. Đây là issue comment, không phải formal GitHub APPROVED review |
| [Operator 6432403910, 27/10](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58#issuecomment-6432403910) | Exact SHA + command + 88/0/0/0 + cleanup 0/0, operator-reported | PARTIAL execution/catalog provenance; local XML bytes đã đọc riêng, không có attested binding giữa hai nguồn |

PR #58 có một review submission state COMMENTED, không có inline review threads trong kết quả API đã đọc. Không được ghi “chưa có independent review nào”: **đã có independent focused P1-02 review**, nhưng reviewer nói rõ không phải production security/merge/LIVE acceptance.

[DEC-D2-04 design-contract agreement](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289513010) của Yến tại `aec2bf2bf6a1719a2c2e964b0f78c7576dabf818` và [Identity/Application Option B agreement](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289921547) tại `ec948102f04e791ee77eb44284f0f79a45f879ff` đã tồn tại. Không mở lại hai design agreements như chưa từng review; chúng không tự approve implementation HEAD 549de8c hoặc physical PostgreSQL/deployment.

[Owner release-gate decision 27/10](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6431715754): D2/D3 qualified PG/security reviewer chưa được ghi nhận assigned/approved; D2-A cần phân biệt operator ABC/fixture với independent provenance/fencing; D4 risk sign-off và integration CREATE preauthorization còn mở; **NO-GO Stage6.2/Workflow HTTP/LIVE**. Đây là trạng thái gate hiện có, không do hồ sơ này tự ký hay tạo gate mới.

## 2. Sáu SQL port và allowlist EXECUTE

Tất cả trả `TABLE(outcome text, body jsonb)`, `SECURITY DEFINER`, `search_path=pg_catalog, pg_temp`. Static self-check chỉ đọc xác nhận đúng sáu tên duy nhất, sáu khai báo path/definer, sáu REVOKE PUBLIC. Schema được thay bằng schema fixture riêng, không phải `public`.

| Port / exact input signature | Function / REVOKE | Caller / mục đích |
| --- | --- | --- |
| `wf_port_load_definition(text,text,text,boolean,text,text,text,text,bigint)` | RD:4–7,104 | Runtime; CLASSIFY/MATERIALIZE, CREATE/ACTIVATE/TRANSITION scope; không có listing API |
| `wf_port_load_instance(text,text,boolean,text)` | RD:105–107,158 | Runtime; READ/ACTIVATE/TRANSITION đúng instance |
| `wf_port_load_history(text,boolean,text)` | RD:159–161,202 | Runtime; READ đúng instance |
| `wf_port_create_instance(text,boolean,jsonb)` | CR:10–17,187 | Runtime; CREATE đúng resource/definition, initial snapshot |
| `wf_port_commit_activation(text,boolean,jsonb,jsonb)` | ACT:4–6,106 | Runtime; expected snapshot → ACTIVE |
| `wf_port_commit_transition(text,boolean,jsonb,jsonb,jsonb)` | ACT:108–110,243 | Runtime; previous/next/record, state + history |

**VERIFIED, phạm vi fixture:** T:79–138 tạo owner NOLOGIN, đổi ownership bảng/hàm/schema, revoke PUBLIC trước COMMIT, runtime nhận USAGE và EXECUTE sáu port. T:161–188 `actualLoginHasExactlySixReviewedSignaturesAndNoOwnerPublicOrInheritedBypass` kiểm tra effective grants/owner/path/count; XML:4 có testcase pass. Hai signature commit được assert trực tiếp qua `oidvectortypes`; bốn signature còn lại đối chiếu source ở bảng trên. Allowlist setup/test nhận diện port bằng prefix `wf_port_` và count=6, không phải một catalog snapshot ký chứa toàn bộ sáu exact signatures.

T:219–270 `allThreeLoginsHaveOnlyTheirExactFunctionAllowlistAndNoEscalation` (XML:46) kiểm tra runtime=6, provisioner=2, outsider=0. Hai hàm offline là:

- `stage6_policy_membership_candidate(text,text,boolean,text,bigint,uuid,text,text)`;
- `stage6_policy_acl_candidate(text,text,text,text,text,boolean,bigint,uuid,text,text)`.

Chúng không phải runtime port thứ bảy/thứ tám. POL:20,136 mặc định INVOKER; T:574–575 mới nâng hai hàm thành DEFINER và cấp EXECUTE cho provisioner. Không có separate policy-admin LOGIN được kiểm tra trong bộ 88: không suy rộng provisioner test thành bằng chứng policy-admin production.

## 3. Findings và giới hạn bằng chứng

### F01 — VERIFIED: runtime LOGIN, owner và chống escalation trong fixture

T:72–105 dùng password ngẫu nhiên riêng cho runtime/provisioner/outsider; owner NOLOGIN và không SUPERUSER/CREATEDB/CREATEROLE/REPLICATION/BYPASSRLS/INHERIT. T:163–166,219–256 kiểm tra session_user thực, zero membership, owner/schema/table ownership, role flags, các negative `SET ROLE` sang owner/admin/role khác, `SET SESSION AUTHORIZATION`, `session_replication_role`, ALTER ROLE/FUNCTION/TABLE và direct SELECT/INSERT/UPDATE/DELETE/TRUNCATE. T:903–906 yêu cầu SQLSTATE 42501, dùng savepoint rồi rollback.

Tests: `independentLoginCannotReadWriteOwnOrEscalateProtectedDatabase` T:192–205 / XML:5; `allThreeLoginsHaveOnlyTheirExactFunctionAllowlistAndNoEscalation` T:219–270 / XML:46; password mismatch T:208–216 / XML:47–48. Đã tách LOGIN thật khỏi test cũ chỉ SET ROLE.

**Giới hạn:** chỉ chứng minh các role/schema fixture được tạo trong T. Không có catalog audit toàn database/cluster, database CREATE, public schema CREATE, membership/default ACL của mọi installer hoặc role deployment. Owner/superuser offline vẫn nằm ngoài containment claim. Không được coi NOINHERIT đơn lẻ là chặn SET ROLE; ở đây có zero membership và negative execution. PostgreSQL 17 phân biệt membership SET/INHERIT: [Role Membership](https://www.postgresql.org/docs/17/role-membership.html).

### F02 — VERIFIED trong fixture / PARTIAL ngoài fixture: PUBLIC và default privileges

CR/RD/ACT có REVOKE exact signatures ở mục 2; POL:247–249 và V5:183–191 revoke helper/table PUBLIC. T:106–107 revoke schema/all functions; T:127 đặt default function EXECUTE của owner; T:265–270 tạo probe dưới owner, chứng minh hàm mới không có PUBLIC grant. Test tương ứng T:219–270 / XML:46.

Setup nằm trong transaction T:63–138, tránh cửa sổ commit hàm DEFINER còn PUBLIC. Tuy nhiên objects đầu tiên được tạo bởi installer, sau đó chuyển owner; default probe chỉ cover owner, không chứng minh future installer/creator hoặc schema-specific defaults khác. PostgreSQL chỉ áp default của role tạo object, không retroactive: [ALTER DEFAULT PRIVILEGES](https://www.postgresql.org/docs/17/sql-alterdefaultprivileges.html). Cần deployment catalog/creator inventory riêng trước cutover; không đề xuất chạy GRANT/REVOKE trong phiên này.

### F03 — VERIFIED static / PARTIAL dynamic: SECURITY DEFINER, search_path, injection

Sáu hàm dùng fixed `pg_catalog, pg_temp`; protected relations được schema-qualify. PORT:198–206 dùng PreparedStatement/bind values; tên hàm do private call sites chọn, schema đi qua WorkflowSchema tại JDBC:82–87. POL:39–52,152–165 đọc approval từ pg_proc theo exact regprocedure, không tin caller GUC. Không thấy dynamic SQL executor trong sáu SQL port/hai offline mutator đã đọc.

T:332–341 `callerSettingsCannotForgeMissingInstalledApproval`, T:345–349 `quotedSqlPayloadCannotChangeAnApprovedTarget`, T:366–379 `callerSearchPathAndTempTablesCannotRedirectOfflineMutation` có cases trong XML:13–15,22–23,72–76 (các display name membership trùng nhau; cần source/binary report để phân biệt chính xác từng cặp). Temp-shadow execution trực tiếp chỉ cover offline ACL mutator; chưa có artifact riêng thử shadowing cả sáu runtime ports. Không đánh dấu phần đó VERIFIED dynamic.

Path có pg_temp cuối và revoke/grant trong cùng transaction phù hợp [PostgreSQL 17: Writing SECURITY DEFINER Functions Safely](https://www.postgresql.org/docs/17/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY). Không suy từ safe path thành actor authenticity.

### F04 — VERIFIED candidate / BLOCKED provenance provisioning: approval manifest offline

POL:30–70,143–183 ràng buộc owner context; khác session_user thì bắt READ COMMITTED, installer-pinned approval, operator_ref test-only, fixture digest, thời hạn, SHA-256 của exact login/database/schema/action/target/revision/event/reason/correlation và mutation fields. Sau tenant FOR UPDATE kiểm tra lại expiry bằng clock_timestamp và metadata còn đúng. Không chấp nhận GUC do caller tự đặt.

T:557–575 sinh approval/digest và cài metadata; catalog chỉ lưu operator_ref, fixture_digest, not_before, expires_at, manifest_digest, tránh công khai tenant/member/resource/event. Tests: `publicFunctionCatalogDoesNotExposeApprovedTenantResourceMemberOrEvent` T:297–307 / XML:50; `missingWrongExpiredOrStaleApprovalFailsClosed` T:311–328 / XML:51–71; `offlineMutationRejectsIsolationThatCouldKeepAnOldApprovalSnapshot` T:352–363 / XML:19–20; expiry/replacement wait T:418–477 / XML:16–17 và các membership cases.

**Blocker D2-A, tách candidate và artifact ngoài source:** operator_ref trong T là `test-only/synthetic-operator`; fixture_digest là hash file Java T:563–564; target/member strings synthetic không chứng minh approved provisioning artifact. Tuy nhiên không còn đúng nếu nói toàn dự án chưa có owner approval/Identity evidence: [owner decision v1-r4](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6383441325) đã ghi conditional approval một disposable fixture attempt và `IDENTITY_ABC=VERIFIED` theo operator evidence; chính decision đó ghi `V3_WRITER_FENCE=NOT_YET_VERIFIED`, fixture COMMIT/smoke NOT_RUN. Hồ sơ này không mở private manifest/resolved SQL, không kiểm lại bytes/digest hoặc canonical actors, không coi hashes công bố là tự đo. [Owner release gate mới](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6431715754) yêu cầu xác nhận approved r5 owner/operator/fence trước production acceptance; chưa có r5 artifact/independent acceptance trong tập evidence đã kiểm tra. Không áp r4 approval cho r5, target 55427 hoặc execution khác. Đây là integrity/call-binding test, không chứng minh quyền sở hữu/mapping từ nguồn có thẩm quyền. D2:70–73 đòi artifact versioned, canonical actors, V1–V4 hashes, approval bên ngoài digest; D2:79–87 đòi empty inventory, fencing mọi writer và post-COMMIT evidence. T:89–99 seed trực tiếp bằng administrator, chưa phải đường provisioning fresh-install hoàn chỉnh đã được review. Hai offline mutator hiện có xử lý membership/ACL; chúng không tự cung cấp bootstrap/resource/binding provisioning đã phê duyệt. Không cần và không được mở enterprise legacy adoption để đóng MVP gate.

### F05 — VERIFIED candidate subset / PARTIAL toàn matrix: tenant ACL, guard, audit, concurrency

CR:100–165 giữ thứ tự locator → tenant FOR SHARE → fresh resource/membership/visibility → explicit CREATE grant + authority → exact active binding/definition. RD:26–56,118–155,171–199 và ACT:65–98,184–229 cũng recheck dưới guard. POL:56–70,169–183 dùng FOR UPDATE cùng tenant guard; POL:83–128 reassign active owners, revoke mọi positive grant, cập nhật membership/revision và INSERT audit trong một transaction. POL:219–245 deferred lifecycle checks; V5:75–94 và100–181 bảo vệ audit/identity.

Tests trong 88: `offlineMutationCommitsExactLoginManifestRevisionAndAudit` T:275–294; cross-tenant negative T:383–401; audit rollback T:406–414; no-op T:444–451 / XML:18; reactivation T:480–496 / XML:49; race hai thứ tự T:522–554 / XML:42–43,92–94. Observer mới kiểm tra revision/audit/state, không chỉ success outcome.

**Giới hạn cụ thể:** race T:527 gọi CREATE, không phải chứng minh mọi READ/HISTORY/ACTIVATE/TRANSITION và mọi owner/binding/tombstone mutator interleaving. MATRIX:43–49 còn yêu cầu TX-03–06, gồm savepoint/caught exception sau guard. T:903–906 dùng savepoint cho privilege negative không phải bằng chứng TX-06. Các suite LIFETEST:125–185,193–275,281–387 có thêm test source nhưng không có XML tương ứng trong test-results hiện có. Không gọi toàn bộ concurrency matrix PASS.

### F06 — PARTIAL D2 physical: implementation có, thiếu execution artifact tương ứng

V5:11–71 có permanent tenant key, global resource PK, tenant FK, owner/member FK và exact definition binding; V5:100–181 chặn identity rewrite, DELETE/TRUNCATE, tombstone reversal, audit UPDATE/DELETE. Đây là năm business ACL tables + một audit table, không có migration V5 trong main resources (tracked migrations hiện V1–V4).

PHYTEST:82–139 cover six tables/twelve triggers, immutable keys/FK/audit; PHYTEST:144–250 cover global key, delete/recreate, cross-tenant UPDATE/UPSERT, empty-child identity, tombstone timestamp. Bộ 88 của T không chạy các testcase thuộc PHYTEST; direct-DML 42501 của runtime không tự chứng minh trigger chặn owner-level DML. Không tìm thấy XML PHYTEST/LIFETEST trong thư mục test-results hiện tại. Cần artifact đúng HEAD cho các negative physical tests và review FK/collation/identity compatibility theo D2:21. Không được biến sự thiếu artifact thành khẳng định code physical không tồn tại.

### F07 — VERIFIED candidate subset / PARTIAL D4: CREATE authority và non-materialization

PORT:40–68 lấy actor và exact operation authority từ CurrentPrincipal.requireVerified, bind immutable operation/target; `backend/src/main/java/com/tricore/dxos/core/identity/Authority.java:11–12` so khớp chuỗi bằng equals, `backend/src/main/java/com/tricore/dxos/application/authorization/WorkflowOperation.java:6–9` định nghĩa đúng authority từng operation. GATE:52–88 giữ Identity → source → visibility → explicit CREATE grant + exact authority → binding; ORCH:45–63 sinh server ID sau precheck, không duplicate-ID SELECT. RD:42–56 trả FACTS trước binding khi CLASSIFY; CR:140–165 chỉ đọc binding/definition sau grant+authority. Owner không tự có CREATE quyền.

GATETEST:28–140 có identity-failure, hidden-before-authority, owner-without-grant, wrong authority-before-binding, source/binding uncertainty. ORCHTEST:57–104,168–176 có server ID và forbidden/hidden không mở scoped port/materialize. CRTEST:97–132,159–225 có owner/other-grant/missing-authority/cross-tenant/mixed-failure cases. **Đây là test source; không có XML của các suite này trong evidence 88.**

T:605–639 / XML:78 chứng minh positive CLASSIFY/MATERIALIZE/CREATE theo runtime LOGIN. T:674–687 / XML:91 chứng minh ACTIVATE/TRANSITION không đòi thêm READ authority/grant. Nhưng principal trong T:888–892 là mock, không phải OIDC/JWKS execution. Cần Yến re-review trust boundary/CREATE ordering tại exact HEAD và evidence các suite còn thiếu; không dùng 88 SQL/login tests để ký JWT/HTTP security.

### CREATE / Identity A/B/C — PARTIAL evidence, acceptance còn BLOCKED

D2:54–66 quy định **A/B/C là fixture actor aliases**, không phải ba issuer/subject hoặc actor IDs có thể tự điền:

| Alias/scenario | Acceptance cần giữ | Evidence/disposition hiện có |
| --- | --- | --- |
| A: active tenant-a, owner R-A, READ grant only | READ với exact read authority; CREATE FORBIDDEN dù là owner; denial trước binding/definition lookup | GATETEST:71–92, CRTEST:97–124 có synthetic source assertions; operator ABC verified được owner ghi nhận, chưa tự xem private mapping/authority evidence. PARTIAL |
| B: active tenant-a, explicit grants trên R-A | CREATE chỉ khi exact workflow:create + explicit CREATE ACL + exact active binding; ACTIVATE/TRANSITION dùng đúng own operation grant; server ID, no collision probe | PORT/GATE/ORCH và T positive cases; B với canonical OIDC profile + approved fixture chưa được hồ sơ này kiểm độc lập. PARTIAL |
| C: active tenant-b, R-B; không tenant-a membership | R-A HIDDEN đối với C, R-B HIDDEN đối với A/B; không leak definition/history, không mutation/revision/audit | CRTEST:159–225 và T:383–401 là synthetic tests; chưa có approved ABC cross-tenant execution artifact gắn fixture r5 trong hồ sơ. PARTIAL |

Conditional r4 owner decision xác nhận operator-reported ABC input, không chứng minh fixture COMMIT/smoke hoặc independent PG provenance. Gate thực tế: Identity operator cung cấp **restricted references** cho verified issuer+subject → canonical actor, distinct mappings và exact least-privilege authorities; reviewer đối chiếu approved artifact/version/target/fence và acceptance interactions. Không công bố actor IDs, raw issuer/subject, tokens hay private manifest. DEC-D2-04 contract agreement đã có, implementation/SQL trace/non-materialization acceptance và latest release CREATE preauthorization vẫn cần đóng. Không tự tạo tài khoản hay chạm Keycloak để lấp gap.

### F08 — VERIFIED static + XML regression: P1-02 direct SQL transition integrity đã có sửa và test

Commit HEAD sửa ACT:221–228: SQL tự kiểm tra transition_id/source/target theo definition/version đã pin và lifecycle COMPLETED đúng trạng thái terminal. ACT:207–220 còn kiểm version, full previous snapshot, immutable fields, actor/history record và monotonic time; ACT:230–239 ghi instance + history cùng transaction.

T:709–728 `directRuntimeSqlEnforcesPinnedTransitionAndLifecycle` gọi raw runtime SQL (helper T:731–746), bác transition target sai, transition không tồn tại và terminal lifecycle sai; mỗi negative kiểm snapshot/history không đổi, rồi có positive control. XML:74 ghi testcase này pass. T:690–706 / XML:8 thêm forged graph/actor/previous-snapshot ở adapter.

**Disposition P1-02:** source và local XML có regression evidence; thêm review độc lập có giới hạn của Yến tại comment 6394092887, exact HEAD. Không liệt kê lại P1-02 như open code defect hay nói chưa từng có independent focused acceptance. Local execution 27/10 có provenance riêng F10, không gộp làm cùng lần chạy với review 25/10. Test forged actor ở adapter không phải chống actor spoofing bằng password DB theo Option B.

### F09 — VERIFIED candidate fault behavior / PARTIAL recovery deployment

JDBC:463–509 lấy connection riêng, READ COMMITTED, guard timeouts, synchronous_commit và fsync/full_page_writes checks, chỉ trả success sau commit. JDBC:510–522 giữ COMMIT_OUTCOME_UNKNOWN khi commit/rollback không chắc chắn. PORT:129–184 canonicalize proposal trước final port call; final SQL recheck lại ACL/CAS.

T:799–828 `guardedAdapterFaultsKeepAtomicOutcomeClassificationAndNeverRetry` / XML:79–90 cover 3 writes × statement/rollback/commit-before/commit-after, attempts=1 và trạng thái/history. T:501–517 / XML:44–45 cover offline unknown commit và fresh observer. Đây là JDBC proxy fault injection, không chứng minh network disconnect thật, crash recovery hay backup/restore.

D2:85–87 và ADR:50 yêu cầu controlled deployment/recovery/audit backup review. Không có artifact fencing/cutover/rollback/restore trong hồ sơ được kiểm tra; không mở traffic hoặc regrant direct V3 để bù. `WorkflowController.java:27–36` còn cố ý không đăng ký Spring controller; JDBC:81–87 gọi forStage6 là isolated candidate. Static source không chứng thực trạng thái của server/container đang chạy.

### F10 — PARTIAL/BLOCKED: evidence exact HEAD và cleanup chưa khép kín

Bằng chứng: XML:2,92–100, T:55–65,145–158 và ba PR #58 review/operator records ở mục 1. XML 88/0/0/0 là VERIFIED nội dung; command/target/SHA và cleanup 0/0 đã được operator cung cấp, independent focused test-only acceptance 25/10 đã tồn tại. Phần còn PARTIAL là raw execution/catalog artifacts, exact fixture identity/digest và binding với bytes XML của lần 27/10; qualified environment-specific PG/security acceptance vẫn BLOCKED. Không yêu cầu tự chạy lại DB hoặc tái phủ nhận review 25/10 để bù evidence.

OLD:9–22 chỉ ghi các commit/suite cũ, gồm runtime LOGIN 2 cases tại 045e717; OLD:24–43 có OPEN/CANDIDATE lịch sử. Không lấy ghi chú cũ làm evidence latest HEAD hoặc phủ nhận implementation mới đã đọc. Cần supplement đúng HEAD, giữ phân biệt source, local run, independent sign-off.

### F11 — BLOCKED D4 sign-off; VERIFIED rằng Option B mất actor-authenticity guarantee

ADR:19–21,40–44 ghi rõ SQL tin backend actor/authority assertions; người có runtime DB credentials + connectivity có thể tự truyền actor và boolean authority. CR:10–13, RD:4–5, ACT:108 xác nhận SQL interface nhận assertions; T:739–742 minh họa raw runtime call tự truyền `member-a,true`. EXECUTE-only, NOLOGIN owner và safe search_path không biến actor thành DB-authenticated OIDC principal.

T:888–892 dùng mock verified principal, vì vậy 88 tests không chứng minh OIDC/JWT hoặc stolen-credential impersonation resistance. Phân loại latter: **NOT SUPPORTED / OWNER-ACCEPTED RISK (SECURITY REVIEW PENDING)**. HMAC/PID/XID, nonce/replay, rotation, cross-language MAC: **DEFERRED / NOT PASS**, không là việc phải khôi phục cho MVP nếu reviewer chấp nhận Option B.

ADR:48–52 yêu cầu reviewer PostgreSQL/security riêng, deployment assumptions và supersession ADR-0024 sau required reviews. Yến đã có Identity/Application threat-model AGREEMENT tại PR #50 comment 6289921547; đây là design scope ở HEAD cũ, không phải D4 implementation PASS. ADR:3,9,54 tại source HEAD vẫn ghi sign-off pending; release decision 6431715754 xác nhận qualified PG/security risk sign-off chưa đóng. Không biến “pending PG sign-off” thành “chưa có Identity design review”, cũng không ghi ADR-0024 đã formally superseded. Owner chọn Option B và P1-02 test-only acceptance không cho phép SQL grants/HTTP/LIVE.

## 4. Blocker còn lại — điều kiện đóng chính xác

| ID | Gate / trạng thái | Bằng chứng còn thiếu hoặc quyết định cần có |
| --- | --- | --- |
| B1 | Evidence PARTIAL; broader acceptance BLOCKED | Đã có local XML 88/0/0/0, operator exact-SHA/command/target/cleanup 0/0 và independent P1-02 focused acceptance. Còn thiếu raw execution/catalog/fixture identity binding được reviewer xác thực cho lần 27/10; không báo thiếu toàn bộ provenance hay cleanup. F10 |
| B2 | D2-A — PARTIAL → acceptance BLOCKED | R4 conditional owner approval/ABC operator evidence đã có; r5 exact artifact, restricted canonical identity/authority provenance, approval/target/digest binding, empty inventory, continuous writer fencing, COMMIT/postcheck/recovery chưa được qualified reviewer đối chiếu. Không kế thừa approval/execution giữa revisions/targets. F04/F09 và ABC matrix |
| B3 | D2 physical — PARTIAL → sign-off BLOCKED | Execution artifacts đúng HEAD cho PHYTEST/LIFETEST và independent DDL/FK/collation/immutability/audit review; không lấy 88 thay thế. F05/F06 |
| B4 | D3 — PARTIAL → sign-off BLOCKED | Independent review exact-signature effective privileges/defaults/definers; bổ sung catalog target/creator/admin roles và gap concurrency/savepoint/shadowing cần thiết theo matrix. Fixture runtime=6/provisioner=2/outsider=0 đã có evidence, không còn là “chỉ SET ROLE”. F01–F03/F05 |
| B5 | D4 — PARTIAL design / acceptance BLOCKED | DEC-D2-04 và Identity/Application Option B design agreements đã có; cần implementation/ABC/CREATE interactions và current integration preauthorization disposition, qualified PG reviewer risk/controls acceptance, ADR-0024/0025 synchronization. Không yêu cầu review lại thiết kế đã đồng ý nếu boundary không đổi. F07/F11 |
| B6 | Stage 6.1/6.2, HTTP/LIVE — BLOCKED | Chữ ký độc lập D2/D3/D4 và các gate triển khai còn lại; candidate proof không cấp quyền deploy/cutover/migrate/HTTP/merge. F09/F11, ADR:50–54 |

Không phát hiện thêm lỗi code có exploit được chứng minh trong phạm vi static read-only này. Không diễn giải điều đó thành chứng nhận an toàn toàn hệ thống. **P1-02 không còn là blocker code mở** theo source/regression artifact hiện có; các blocker trên là evidence, provisioning/deployment boundaries và quyết định security còn thiếu. HMAC proof-only tests deferred không tự trở thành blocker MVP mới.

Reviewer dùng [checklist và GitHub handoff nháp](stage6-d2-d3-d4-review-checklist-549de8c.md). Có thể giới hạn APPROVED vào candidate design/evidence nếu nêu rõ phạm vi; không được dùng chữ ký phạm vi hẹp để đánh dấu release/HTTP được phê duyệt. Hiện hồ sơ vẫn chưa có chữ ký.

## 5. Dấu vết kiểm tra chỉ đọc và fingerprint

Đã thực hiện: `git -c safe.directory=<repo> rev-parse HEAD`, `status --short`, scoped `diff --exit-code HEAD -- backend/src/main backend/src/test docs/adr/ADR-0025-...`; targeted rg/Get-Content; parse XML/count nodes; Get-FileHash; static asserts sáu port/search_path/REVOKE. Chỉ dùng safe.directory ở từng lệnh Git, không sửa global Git config.

| Artifact source | SHA-256 |
| --- | --- |
| T | `9E17BF302FFFC52721DB1F768BA07C0E692F93978B2CCA685AF34C7957EEB9D3` |
| V5 | `2208751C1551ACA10B8F905DC36F1E24EB1D191F0D59DD74669860E6873491AA` |
| POL | `D8B095022260083D471023D0CF2BA1632ED2566EF1CA2DF61A4DF0D6D2126A68` |
| RD | `07458020D397CAC3C1BDA0653D9CF0BEAEF2CD0AF969F8B7E728423030754421` |
| CR | `5114CF9CA00AADC6135D94FA51840021C9D23F79B27F3418865496622F3157F0` |
| ACT | `BCF1ACDDE5F0A5DB572CD3E22DB95C396023027D3DBA47D712CAB8B6D0C3AEF0` |

Hashes trên là bytes cục bộ tại lúc chuẩn bị, không phải attestation rằng các bytes này đã được nạp vào DB lúc chạy XML. SQL/Java không thay đổi. Hai Markdown trong docs worktree chỉ là review artifacts. Original test worktree/Markdown/XML không bị sửa; chưa staging, commit hoặc tạo documentation commit SHA mới. Handoff nằm ở checklist, chưa gửi lên GitHub.

Không chạy lại test, không kiểm tra DB/container/live hoặc credentials. Rủi ro cần giữ nguyên trong quyết định: actor spoofing bằng runtime DB credential + DB access vẫn không được Option B ngăn chặn.
