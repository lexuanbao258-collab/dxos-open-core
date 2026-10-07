# Session 1 homework — TRICORE / DX-OS Open-Core

## Task và baseline

- Human owner: **Lê Xuân Bảo** (`lexuanbao258-collab`).
- Issue: [#16 — Complete Session 1 README and Workflow POC evidence](https://github.com/lexuanbao258-collab/dxos-open-core/issues/16).
- Branch: `docs/session-1-homework`.
- Repository: `C:\Users\Admin\dxos-open-core`.
- Origin: `https://github.com/lexuanbao258-collab/dxos-open-core.git`.
- Base main commit: **`2b51a41fdb372edd9e00ac05dac8ccc9a52d738c`**.
- Ngày kiểm tra GitHub và chạy verification: **2026-10-07**.

**Publication status cập nhật:** branch `docs/session-1-homework` đã được push
lên origin. [PR #17](https://github.com/lexuanbao258-collab/dxos-open-core/pull/17)
đang **OPEN**, targets `main`, chứa `Closes #16`; Issue #16 vẫn **OPEN**.
PR #17 **chưa merge**, nên tài liệu trên task branch chưa có trên main.

Trước khi viết: cwd/root đúng repository, branch main, working tree sạch;
`git fetch origin` thành công và `main...origin/main = 0 / 0`.
Không có branch `docs/session-1-homework` local/remote; tạo branch từ main
đồng bộ rồi chuyển sang task branch. Không làm việc trực tiếp trên main.
Không tìm thấy equivalent open Issue trước khi tạo đúng một Issue #16.

Đây là evidence cho bài tập Session 1 theo yêu cầu giảng viên được cung cấp
trong task. Định hướng Internal Service Portal là của TRICORE; không bổ sung
yêu cầu/scoring chính thức của cuộc thi.

## Files changed

| File | Nội dung |
| --- | --- |
| [README.md](../../README.md) | Product problem, DX-OS explanation, architecture/responsibilities, trạng thái team, baseline công nghệ, local run và MVP roadmap |
| [workflow-core-poc.md](../poc/workflow-core-poc.md) | Phạm vi nghiên cứu/validation Workflow hiện có, commands/results, code/test evidence và limitations |
| [session-1-homework.md](session-1-homework.md) | Coverage, GitHub facts, task baseline, verification, commit checkpoints và việc còn lại |

Không sửa docs/contracts/workflow-core.md hoặc evidence lịch sử. Không sửa
source/test/resources/migrations, Gradle/Wrapper/config hay infrastructure.
Generated XML/HTML/JAR nằm trong build output local, không commit.

## Lecturer requirement coverage

PASS trong bảng là coverage của **tài liệu và kiểm chứng local** tại task này,
không có nghĩa tất cả capability đã triển khai. Tài liệu đã publish lên GitHub
trên task branch qua PR #17, nhưng chưa merge vào main.

| Requirement | Evidence | Status |
| --- | --- | --- |
| Architecture completed/documented | README: TEAM ARCHITECTURE BASELINE, ASCII dependency diagram và các ràng buộc; baseline docs/architecture được giữ nguyên | PASS |
| Component responsibilities documented | README bảng Identity/Gateway/Workflow/Data, tách application semantics khỏi Core mechanisms | PASS |
| At least one component researched/run | Workflow POC: design rationale, code/test mapping và lần chạy lại focused tests; không claim nghiên cứu/chạy external engine | PASS |
| Each team member has Issue | #12 Yến, #13 Bảo, #14 Ánh; owners/assignees đọc từ GitHub | PASS |
| Each member has commit plan | Suggested commit checkpoints trong #12/#14; #13 có plan và ba commits thực tế trong merged PR #15 | PASS |
| README product problem | Internal service requests qua kênh phân tán; định hướng Portal tập trung hồ sơ/trạng thái/history | PASS |
| README run instructions | JDK 21, Wrapper, test/build, DB_* và PostgreSQL-dependent startup với explicit limitation | PASS |
| README architecture | Modular monolith, dependency direction, Workflow-owned port và provider independence | PASS |
| README MVP roadmap | Login → Gateway → Request → Workflow → Data → Portal; DONE/CURRENT, IN PROGRESS, NEXT | PASS |

## GitHub development planning — facts hiện tại

Tại lần kiểm tra ban đầu: #12/#14 OPEN, #13 CLOSED; chưa có PR OPEN trong repository.
Sau khi publish task branch, PR #17 hiện OPEN; Issue #16 OPEN và được tham chiếu
bằng `Closes #16`. Trạng thái mới không thay đổi ngày/kết quả verification ban đầu.
Identity/Data trên main chỉ có package skeleton. Remote task branches #12/#14
vẫn ở baseline trước Workflow merge (main ahead 4 commits, task branches không
có commit riêng thêm khi so với main). Không suy diễn tiến độ ở checkout cá nhân.

### Phan Hải Yến — Identity / SSO

- [Issue #12](https://github.com/lexuanbao258-collab/dxos-open-core/issues/12), OPEN;
  assignee `phanyen06ym-ien`.
- Branch theo Issue: `feature/identity-core-contracts`.
- Checkpoints được ghi trong Issue:
  1. `feat(identity): define principal and authentication context contracts`
  2. `feat(identity): define authority decisions and normalized errors`
  3. `test(identity): verify contracts and document trust assumptions`

### Lê Xuân Bảo — Workflow Core / API Gateway

- [Issue #13](https://github.com/lexuanbao258-collab/dxos-open-core/issues/13), CLOSED;
  assignee `lexuanbao258-collab`.
- [PR #15](https://github.com/lexuanbao258-collab/dxos-open-core/pull/15), MERGED
  vào main ngày 2026-10-07 lúc `04:28:13 UTC`; merge commit là base main nêu trên.
- Branch triển khai: `feature/workflow-core-contracts`.
- Các commits thực tế (không rewrite history):

| SHA | Message |
| --- | --- |
| d6d6b8b41deda0f5fc19ab3c6ba3d8c38f7b601a | feat(workflow): define generic workflow models and transition contracts |
| 56f58374c21b28624efadf7ee408e5088e35f560 | feat(workflow): define persistence port and atomic transition contract |
| 971f6dd58f25fd673e0d4ba1c84f433e64cb496b | test(workflow): verify contract invariants and document persistence guarantees |

### Phan Ngọc Ánh — Data / Database / Storage

- [Issue #14](https://github.com/lexuanbao258-collab/dxos-open-core/issues/14), OPEN;
  assignee `ngang02012007-crypto`.
- Branch theo Issue: `feature/data-core-contracts`.
- Checkpoints được ghi trong Issue:
  1. `feat(data): define object storage and metadata contracts`
  2. `feat(data): define normalized storage errors and contract tests`
  3. `docs(data): define adapter ownership and migration constraints`

## Verification commands và kết quả thực tế

Môi trường: Windows 11, Oracle JDK 21.0.11, Gradle Wrapper 8.14.3;
Spring Boot 3.5.16 trong build baseline. Thiết lập JAVA_HOME cho JDK hiện có
trước khi chạy. Commands backend thực hiện từ `backend/`:

| Command | Actual result |
| --- | --- |
| `java -version` | PASS: 21.0.11 |
| `.\gradlew.bat --version` | PASS: 8.14.3; JVM 21.0.11 |
| `.\gradlew.bat test --tests 'com.tricore.dxos.core.workflow.*'` | PASS: 29 tests / 4 suites |
| `.\gradlew.bat test --tests 'com.tricore.dxos.request.*'` | PASS: 109 tests / 7 suites |
| `.\gradlew.bat test` | PASS: 139 tests / 12 suites |
| `.\gradlew.bat build` | PASS: các task UP-TO-DATE; không clean rebuild |
| `git diff --check` (root; lặp lại trước commits) | PASS |

Cả ba lượt tests có 0 failures/errors/skipped; task `:test` thực sự chạy ở
mỗi lượt. Đọc XML `backend/build/test-results/test/TEST-*.xml` ngay sau từng
lệnh để tránh full run ghi đè focused results. Full run có 29 Workflow,
109 Request và 1 health test. HTML report local nằm tại
`backend/build/reports/tests/test/index.html`; không commit report.
Build có executable JAR 57,566,833 bytes và plain JAR 46,130 bytes.
Lệnh Gradle chạy ngoài sandbox để truy cập cache/dependencies; không đổi config.
Cảnh báo JVM class-data-sharing xuất hiện nhưng không làm test/build thất bại.

Import review bằng `rg` trên production `core.workflow` chỉ thấy `java.*`;
không thấy Spring/JPA/JDBC/Hibernate/PostgreSQL/Request/infrastructure/Identity.
So diff với base main xác nhận chỉ ba file tài liệu trong bảng scope thay đổi.

## Architecture review — REVIEW ONLY

- Modular monolith và deployment topology giữ nguyên; không redesign microservices.
- Identity chịu authentication/identity/authority/security context; Gateway là
  entry boundary và trusted context, không sở hữu persistence/nghiệp vụ Workflow.
- Workflow sở hữu definition/instance/transition semantics và persistence port;
  không gọi Identity tại runtime. Data sẽ triển khai adapter theo contract đó.
- Request vẫn là application/business prototype, chưa được tích hợp Generic Core.
- Core Workflow độc lập provider; Java/Spring/PostgreSQL/Flyway là implementation
  baseline, không khóa provider-independent contracts.
- MVP là vertical integration. Các bước tiếp theo phụ thuộc Identity contract,
  Workflow orchestration, Data adapter/DB POC rồi nối Portal; chưa được chạy end-to-end.
- Không chọn Keycloak/APISIX/MinIO/n8n/Flowable hoặc provider mới.

## Commit checkpoints ban đầu của task Session 1

| SHA / cách tra | Message | Files | Verification |
| --- | --- | --- | --- |
| 462658ed312c2ff03437057ad60b8dd122102185 | docs(readme): describe product problem and core architecture | README.md | Diff/scope/architecture review, git diff --check; Workflow/Request/full tests và build PASS |
| 9fe85599b317dfee623fcc06944add8b945bd264 | docs(poc): document workflow core validation evidence | docs/poc/workflow-core-poc.md | Đối chiếu code/tests và actual XML results; git diff --check PASS |
| c81d84ac316bebb665fe9c7e7d5931dcb1692b80 | docs(roadmap): document session 1 MVP roadmap and verification | docs/evidence/session-1-homework.md | Coverage/GitHub facts, local links, scope và git diff --check |

Ba checkpoint ban đầu đã publish và có trong PR #17; giữ nguyên SHA/lịch sử.
Tra riêng lịch sử ban đầu bằng:

```powershell
git show -s --format='%H %s' c81d84a
git log --reverse --format='%H %s' 2b51a41fdb372edd9e00ac05dac8ccc9a52d738c..c81d84a
```

Ba commit trên đã push lên origin; PR #17 vẫn OPEN và chưa merge. Không
amend/squash, không empty commit hoặc rewrite history. Bản cập nhật publication
status này là một commit local bổ sung, chưa push; không thực hiện push/merge
trong tác vụ cập nhật. Ngày và kết quả verification ban đầu được giữ nguyên.

## Explicit limitations và việc còn lại

**REQUIRED BEFORE DEADLINE:** review PR #17; merge PR #17 vào main; pull latest
main; nộp repository link theo yêu cầu lớp. PR #17 hiện chưa merge và GitHub
main chưa chứa tài liệu Session 1 từ PR này. Không thực hiện push/merge trong
tác vụ cập nhật evidence.

**OPTIONAL AFTER DEADLINE:** hoàn thành #12/#14; Identity provider POC dựa trên
contract/decision có bằng chứng; Gateway trusted-context integration; Workflow
runtime/application orchestration; real persistence adapter và PostgreSQL
atomicity/concurrency POC; Request integration và Portal vertical slice.
Roadmap dependency nằm trong [README](../../README.md).

POC hiện không chứng minh PostgreSQL atomicity/isolation/rollback khi lỗi thật,
startup với database, crash recovery/durability, distributed execution,
production readiness, external Workflow engine, Gateway/Identity integration
hoặc Request migration. Fake chỉ kiểm chứng contract bằng in-memory state;
không thay thế POC transaction thật.
