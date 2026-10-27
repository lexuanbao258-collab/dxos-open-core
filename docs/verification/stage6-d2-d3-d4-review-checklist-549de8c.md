# Checklist ký independent security review — Stage 6 D2/D3/D4

**Source/test HEAD:** `549de8c63442d89dd03ff71a699848c10afc9b98`
**Source worktree giữ nguyên:** `D:\olp\dxos-pr58-readonly-preflight`
**Docs worktree/branch:** `D:\olp\dxos-pr58-security-review-docs` / `docs/stage6-security-review-549de8c-20261027`
**Documentation commit HEAD:** chưa tạo commit; branch HEAD hiện vẫn là source/base SHA. Không coi đó là commit docs hoặc test-run mới.
**Báo cáo:** [findings, file/dòng/test và evidence ledger](stage6-d2-d3-d4-independent-review-549de8c.md)
**Trạng thái biểu mẫu:** CHƯA KÝ; tất cả ô để reviewer độc lập tự đánh giá.

Chỉ tick khi reviewer đã xem bằng chứng; ghi reference/digest/phạm vi hoặc finding chưa đóng. Không suy 88 tests pass thành D2/D3/D4 APPROVED. Đây là checklist review, không cho phép chạy DB writes, provisioning, GRANT/DDL, container changes, HTTP/LIVE, Supabase hay merge.

## 1. Danh tính reviewer và phạm vi quyết định

- Reviewer PostgreSQL/security, vai trò và năng lực: ____________________
- Xác nhận độc lập với người viết implementation: ____________________
- Reviewer Identity/Application và reference: giữ các design agreements PR #50 và focused P1-02 review PR #58 đã có; ghi riêng phạm vi implementation còn thiếu: ____________________
- Ngày giờ / timezone / exact HEAD đã tự đối chiếu: ____________________
- Phạm vi ký (candidate source/evidence hay gate cụ thể; loại trừ release nếu chưa đủ): ____________________
- Evidence bundle location/version/digest; không chứa credentials: ____________________

## 2. Evidence exact HEAD — F10 / B1

- [ ] Source/test HEAD đúng toàn bộ SHA; giữ nguyên worktree kiểm thử và XML. Documentation HEAD (khi có commit) được ghi riêng, không gán 88 tests cho docs commit.
- [ ] Đã parse XML: 88 testcase / 0 failure / 0 error / 0 skipped, không chỉ đọc summary. XML:2 và các nodes.
- [ ] XML digest đúng `E5167ABCCAEE1B326486C6F81228041AD79B03215076147C483BF2EE62A06FA2`; nếu artifact thay đổi, ghi version/digest mới và review lại.
- [ ] Đã đọc operator report 6432403910 có exact source SHA/command/BUILD SUCCESSFUL/Java21.0.11/PG17.11/target; ghi phần raw log/fixture identity/digest/attested XML binding còn chưa kiểm, không coi reported data là tự reproduce.
- [ ] Chấp nhận/phân biệt rõ local XML, source coverage, CI/attestation và independent security sign-off.
- [ ] Đã ghi cleanup **0 schema / 0 role** là operator-provided ở comment 6432403910; query/prefix khớp T:66–71 và cleanup source. Chỉ tick independent catalog verification nếu có raw postcheck/target/run evidence phù hợp, không dựa vào comment hay teardown absence đơn lẻ.
- [ ] Đã ghi review Yến 6394092887: independent focused 1 + 88 tests tại source HEAD, test-only acceptance; không gộp execution 25/10 với local/operator run 27/10 hoặc full PG/security approval.

Evidence/reference và gap: ____________________

Nếu cần thu thập catalog evidence bằng một kết nối đã được phê duyệt riêng, reviewer/operator chỉ dùng SELECT sau; **chưa được chạy trong phiên chuẩn bị hồ sơ**. Không gọi port function hay script test để thu cleanup. Không đưa password/JDBC credentials vào report:

```sql
SELECT current_database() AS database_name,
       current_setting('server_version_num') AS server_version_num,
       clock_timestamp() AS captured_at;
SELECT count(*) AS remaining_test_schemas
FROM pg_catalog.pg_namespace
WHERE nspname ~ '^s6_login_[0-9a-f]{32}$';
SELECT count(*) AS remaining_test_roles
FROM pg_catalog.pg_roles
WHERE rolname ~ '^s6_(rt|owner|prov|other)_[0-9a-f]{32}$';
```

Đối chiếu database/target đã cho phép, thời điểm, run ID và suite prefixes ở T:66–71. Role count là cluster-wide; schema count chỉ database hiện tại. Snapshot mới chỉ chứng minh thời điểm mới; không được ghi lùi thành hậu kiểm của lần chạy cũ. Giữ cả kết quả và digest artifact. Không tự cleanup khi count khác 0.

## 3. D2 physical, provenance, offline provisioning — F04–F06 / B2–B3

- [ ] Review V5:11–94: năm business ACL tables + một audit table, global resource identity, owner/member tenant FK, exact definition binding và V3 FK/collation compatibility.
- [ ] Review V5:100–181 / POL:219–245: durable guard/immutable identity, chống DELETE/TRUNCATE/recreate, UPSERT remap, tombstone reversal và invalid final owner/member/grant.
- [ ] Có execution artifact đúng HEAD của PHYTEST:82–250, LIFETEST:125–387; không gộp vào XML 88 của suite T.
- [ ] Đã phân biệt r4 conditional owner approval 6383441325/ABC operator evidence với independent mapping/artifact validation; không nói chưa có approval nào, không tự kế thừa sang r5/target khác.
- [ ] Latest required fixture/version (release decision nêu r5) có exact approved bytes/target/operator, restricted canonical A/B/C mapping và authority evidence; không lấy Java fixture hash hay test-only operator làm provisioning proof.
- [ ] Có external approval gắn exact bytes/digest/environment; digest chứng minh integrity, không tự chứng minh source authority. D2:70–73.
- [ ] Có empty V3/history/global-key inventory trước và exact after inventory; mọi legacy key/mapping mơ hồ vẫn HIDDEN.
- [ ] Có continuous writer fence/drain backend/pool/jobs/direct V3 suốt inventory → provisioning → post-check/cutover; r4 decision tự ghi fence NOT_YET_VERIFIED và COMMIT/smoke NOT_RUN, không thay bằng cleanup 0/0. D2:79–87.
- [ ] Hai offline mutator được giới hạn exact LOGIN/database/schema/action/target/revision/event và expiry; không có broad runtime admin helper. POL:30–70,143–183.
- [ ] Approval lấy từ exact pg_proc proconfig; caller GUC không giả được; metadata công khai không lộ target/member/event. T:297–341,568–575.
- [ ] Negative approval/expiry/replacement/isolation/quoted payload, cross-tenant denial; check cả không mutation/revision/audit. T:311–477.
- [ ] Deactivate reassign owners + revoke mọi positive grant + revision/audit atomic; reactivate không khôi phục quyền/ownership. T:275–294,406–414,480–496.
- [ ] Review giới hạn audit 64 KiB, exact before/after entries, append-only, offline inspection, retention/backup/recovery. POL:8–15,83–128; ADR:50.

Disposition D2: ____________________
Evidence/exception/finding còn mở: ____________________

## 4. D3 privilege / SECURITY DEFINER / concurrency — F01–F03/F05/F08–F09 / B4

- [ ] Chốt cả sáu exact signatures ở mục 2 báo cáo; effective runtime EXECUTE chỉ sáu, không overload/helper dư.
- [ ] Actual LOGIN evidence: runtime=6, provisioner=2, outsider=0; owner NOLOGIN, protected objects/schema đúng owner. T:161–188,219–270.
- [ ] Không suy provisioner thành separate policy-admin evidence; nếu deployment có admin role thì có catalog/negative evidence cho role đó.
- [ ] SELECT/INSERT/UPDATE/DELETE/TRUNCATE/ALTER/CREATE trong protected schema bị 42501; role flags/memberships không cho SET ROLE/owner/migrator escalation, session authorization hay replication bypass. T:192–256,903–906.
- [ ] Kiểm kê effective PUBLIC/inherited/default ACL và database/public-schema CREATE theo actual target; phân biệt TEMP permission với protected schema CREATE.
- [ ] Default privileges của mọi actual creator/installer được review; owner probe không thay thế installer default ACL. T:127,265–270.
- [ ] Six DEFINER paths `pg_catalog, pg_temp`, protected relations schema-qualified, no arbitrary SQL; Java gọi parameterized SQL. RD/CR/ACT; PORT:198–206.
- [ ] Đánh giá coverage temp/search_path shadowing: T:366–379 chạy offline ACL; bổ sung/chấp nhận có lý do phạm vi dynamic chưa có cho sáu runtime ports.
- [ ] Runtime FOR SHARE và policy FOR UPDATE cùng permanent tenant guard; ACL được đọc lại sau lock; workflow state/history và audit/revision atomic.
- [ ] Hai race CREATE/revoke có distinct writer/waiter/observer PIDs + deterministic blocking, fresh observed revision=1/audit=1. T:522–554 / XML:42–43,92–94.
- [ ] Chốt gap TX-03–06: READ/HISTORY, ACTIVATE/TRANSITION, owner/binding/tombstone mutations, savepoint/caught-exception guard retention. Không dùng T:903–906 để thay TX-06.
- [ ] P1-02 đã review ACT:221–228 và raw SQL regression T:709–746 / XML:74; không báo lại thành open code defect nếu source/evidence giữ nguyên.
- [ ] Fault injection statement/rollback/commit-before/commit-after giữ unknown outcome, attempts=1, reconcile fresh state/history. T:501–517,799–828.
- [ ] Deployment/privilege cutover, rollback/forward recovery và backup/restore có review riêng; không giả lập proxy fault thành crash/network proof thật.

Disposition D3: ____________________
Evidence/exception/finding còn mở: ____________________

## 5. D4 CREATE / Identity / ADR-0025 Option B — F07/F11 / B5

- [ ] A/B/C distinct canonical mappings từ verified issuer+subject và exact least-privilege authorities có restricted evidence references; giữ bí mật private IDs/tokens/manifest. A owner+READ không CREATE; B có explicit scope; C cross-tenant HIDDEN. D2:54–66 và ABC matrix trong báo cáo.
- [ ] Actor/authority chỉ từ CurrentPrincipal.requireVerified; exact authority string, operation/target binding. PORT:40–68; Authority.java:11–12; WorkflowOperation.java:6–9.
- [ ] Identity → reliable source → visibility → explicit CREATE grant + exact JWT authority → exact active binding/definition. Hidden trước authority denial; owner không tự có CREATE. GATE:52–88; RD:26–56; CR:100–165.
- [ ] Có interaction/negative execution artifacts GATETEST:28–140, ORCHTEST:57–104,168–176, CRTEST:97–132,159–225; source alone chưa là run evidence.
- [ ] Server-owned ID, không hidden duplicate probe; không materialize binding/definition trước phép. ORCH:45–63.
- [ ] Không gọi mock CurrentPrincipal T:888–892 là OIDC/JWKS proof. Giữ DEC-D2-04 contract/Option B Identity design agreements đã có, yêu cầu disposition cho implementation/CREATE preauthorization còn mở tại current integration gate; không mở lại design review vô điều kiện.
- [ ] Ghi rõ **runtime DB credentials + DB access có thể spoof actor/authority**; narrow EXECUTE/DEFINER không chặn threat này.
- [ ] Owner risk acceptance và reviewer acceptance Option B/phạm vi MVP là hai quyết định phân biệt; đã xác nhận private network, credential/process controls và deployment assumptions.
- [ ] HMAC/PID/XID/nonce/replay/key rotation/cross-language MAC ghi DEFERRED / NOT PASS; stolen-credential actor resistance ghi NOT SUPPORTED / OWNER-ACCEPTED RISK (SECURITY REVIEW PENDING cho tới khi ký).
- [ ] Sau required reviews, decision records ADR-0024/0025 được đồng bộ đúng phạm vi; không tự tuyên bố superseded trước đó. ADR:48–52.
- [ ] Nếu reviewer từ chối Option B, đưa risk decision về owner; không tự khôi phục HMAC implementation hay bypass gate.

Disposition D4: ____________________
Evidence/exception/finding còn mở: ____________________

## 6. Quyết định độc lập — reviewer tự chọn, không được điền sẵn

B1–B5 liên quan phạm vi ký phải được đóng bằng evidence hoặc disposition rõ có thẩm quyền; blocker khác không được che dưới một chữ APPROVED chung. Candidate-scoped approval không tự đóng Stage 6.1/6.2 hay release gates B6.

- [ ] **APPROVED** cho exact HEAD và phạm vi ghi ở mục 1, với evidence references/dispositions đính kèm.
- [ ] **CHANGES REQUIRED**; liệt kê finding IDs, evidence/changes cần bổ sung và điều kiện re-review.

Phạm vi được APPROVED / phạm vi vẫn BLOCKED: ____________________
Findings/blockers còn lại, người phụ trách, acceptance condition: ____________________
Reviewer PostgreSQL/security — tên/chữ ký/ngày giờ: ____________________
Reviewer Identity/Application — quyết định/reference/ngày giờ: ____________________
Owner risk acceptance reference (không thay chữ ký độc lập): ____________________

**Không có chữ ký tự động. Không cho phép merge, Flyway/DDL/grants, provision DB, thay container hoặc bật Workflow HTTP/LIVE bằng biểu mẫu này.** Việc kiểm tra DB có ghi, rollout hoặc activation cần authorization riêng sau khi các gate áp dụng đã được đóng.

## 7. Nội dung handoff GitHub — NHÁP, CHƯA ĐĂNG

Có thể sao chép phần dưới sau khi người phụ trách kiểm tra patch; hồ sơ này chưa gửi comment, request reviewer, push hoặc đổi PR state.

---

### Stage 6 D2/D3/D4 — independent PostgreSQL/security review handoff

**Pinned source/test HEAD:** `549de8c63442d89dd03ff71a699848c10afc9b98` (PR #58; stacked base `db6b1a8007d2cf4720e9dff6996572a1e819bc49`).
**Docs branch:** `docs/stage6-security-review-549de8c-20261027`; **documentation commit chưa tạo**; worktree HEAD hiện vẫn là base/source SHA. Không ghi kết quả test dưới SHA docs tương lai.

Hai tài liệu trong patch docs-only:

- `docs/verification/stage6-d2-d3-d4-independent-review-549de8c.md`: source/test citations, F01–F11, A/B/C matrix và B1–B6.
- `docs/verification/stage6-d2-d3-d4-review-checklist-549de8c.md`: checklist, scope/signature trống và handoff này.

**Evidence đã có, không gộp các lần chạy:**
1. Local JUnit XML suite runtime-LOGIN: **88 tests / 0 failures / 0 errors / 0 skipped**; đã parse bytes chỉ đọc. SHA-256 đo cục bộ: `E5167ABCCAEE1B326486C6F81228041AD79B03215076147C483BF2EE62A06FA2`. XML ignored/local, chưa nằm trong patch hay attestation CI.
2. [Yến independent focused review 25/10](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58#issuecomment-6394092887): exact source HEAD, tự báo chạy targeted **1 + suite 88 PASS**; **test-only candidate accepted with limitations**. Không phải production PostgreSQL/security, merge hoặc LIVE approval.
3. [Operator report 27/10](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58#issuecomment-6432403910): exact SHA/command/PG17.11/Java21.0.11 và cleanup **0 schema / 0 role**. Đã đối chiếu comment với local XML counts; phiên chuẩn bị không truy vấn DB và không chứng thực raw cleanup transcript/target-instance binding.
4. [DEC-D2-04 design agreement](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289513010) và [Identity/Application Option B agreement](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289921547) đã có ở exact design HEAD cũ. Không yêu cầu lại architectural approval nếu boundary không đổi.
5. [Conditional owner fixture r4 decision](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6383441325): operator-reported ABC verification/integrity, fencing chưa verified, COMMIT/smoke NOT_RUN trong decision đó. Không tự suy thành r5 approval, fixture execution hoặc production rollout.

**Nhờ qualified reviewer độc lập kết luận theo đúng phạm vi:**
- D2: physical FK/immutability/audit, restricted A/B/C verified actor + least-privilege authority references, exact approved fixture/version/target/digest, operator provenance, empty inventory và continuous writer fence.
- D3: exact sáu `wf_port_*` EXECUTE-only; real deployed LOGIN/owner/PUBLIC/default/inherited/SET ROLE negatives; DEFINER/search_path; guard/ACL/audit/concurrency/unknown COMMIT và cutover/rollback. Tách source/test candidate evidence khỏi deployed catalog acceptance.
- D4/CREATE: implementation ordering visibility → explicit CREATE ACL + exact authority → binding/definition, query/interaction non-materialization; P1-01 ADR-0025 Option B risk/compensating controls. **Stolen runtime DB credentials + DB access có thể forge actor/authority**; HMAC/PID/XID/nonce/replay/rotation DEFERRED / NOT PASS.
- Trả **APPROVED** chỉ cho phạm vi và evidence được reviewer chấp nhận, hoặc **CHANGES REQUIRED** kèm finding/file/dòng/test và điều kiện đóng. Không diễn giải lại acceptance P1-02 thành full D2/D3/D4 acceptance.

[Owner release decision 27/10](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6431715754) vẫn **NO-GO**: qualified PG/security sign-off, current fixture r5/ABC/operator/fence, D4 risk và integration CREATE preauthorization chưa đóng.

**DRAFT / DO NOT MERGE / LIVE_BLOCKED.** Patch không đổi SQL/Java, applied V1–V4, grants, Docker/container/Supabase hoặc approval flags. Không chạy test ghi DB, migrate/provision/cutover hay bật Workflow HTTP. Người chuẩn bị không tự ký APPROVED.

---

Tham chiếu diff-check và patch digest lấy từ kết quả kiểm tra cục bộ cuối phiên, không điền một commit/hash chưa tồn tại vào nháp.
