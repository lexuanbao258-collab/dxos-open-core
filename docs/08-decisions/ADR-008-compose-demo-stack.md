<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# ADR-008 — R21 Docker Compose demo stack

**Trạng thái: PROPOSED — chưa được owner phê duyệt, chưa cho phép triển khai.**
Ngày khảo sát: 2026-11-04. Phạm vi: local demo, release đề xuất **v0.2.0**.
Theo dõi: [R21 / #119](https://github.com/lexuanbao258-collab/dxos-open-core/issues/119),
[release / #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40),
[R23 / #118](https://github.com/lexuanbao258-collab/dxos-open-core/issues/118).
Tài liệu này chỉ đề xuất kiến trúc và kế hoạch; không ghi nhận quyết định ACCEPTED.

## Baseline và bảo toàn công việc

Các mốc checkout/fetch/PR44 dưới đây là lịch sử khảo sát ban đầu. Lượt đối chiếu bổ sung
giữ HEAD `0e789cffa11447f3acef25d0e582bf25ed46c7b4` trên `docs/infra-r21-compose-plan`;
đầu lượt chỉ ADR này untracked, không có thay đổi staged/source runtime.

- Checkout ban đầu: branch `main`, HEAD/local main và origin/main trước fetch đều là
  `a55e06ecae233501c76f82bae9d513eb7d744ac0`; staged/unstaged/untracked đều sạch.
- Sau fetch: `origin/main = 0e789cffa11447f3acef25d0e582bf25ed46c7b4`, tag `v0.1.0`.
  Local main chậm 2 commit: `4428346` tích hợp Backend/BFF/Portal, `0e789cf` release.
- Branch tài liệu mới `docs/infra-r21-compose-plan` bắt đầu đúng remote main trên.
  Local main cũ được giữ nguyên; chưa commit, push, mở PR hoặc chỉnh GitHub.
- Hai worktree khác được liệt kê nhưng không sửa: `D:/olp/dxos-pr96-r03`
  tại `af51c8cbfa7e6ebad7dda27e3bb91c44fcade012` và
  `D:/olp/dxos-pr96-r03-rebased` tại `013ab4c18d1482fb525c56b23c5703ce9a4e0e23`.
- Đã đọc CONTRIBUTING, CODEOWNERS, README tại baseline, #119/#40/#118 và các comment #40.
  Không có approval D1–D5 trong #119 tại thời điểm khảo sát (0 comment).
  Không dùng các hướng dẫn/biên bản cũ làm phê duyệt cho R21.

## Hiện trạng có thể tái sử dụng

| Thành phần | Bằng chứng tại main baseline | Giới hạn tích hợp |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3; JWT issuer/audience/resource-client validation; Helpdesk HTTP và PostgreSQL persistence | Chưa có Dockerfile; `HelpdeskConfiguration.requireDisposable` khóa JDBC/bind loopback, DB `helpdesk_disposable_*`, runtime `helpdesk_runtime_*`; `WorkflowEventTriggerGuard.emptyLocal` cũng chỉ nhận loopback |
| BFF | Java/Spring Boot, dùng Wrapper của Backend; confidential Code + PKCE S256, server-side session, CSRF, logout, bearer forwarding và route allowlist | Không phải Node app; không có npm lockfile của BFF. Bind loopback; `checkedBackend` chỉ cho HTTPS hoặc HTTP literal `127.0.0.1`, sẽ từ chối `http://backend:8080` |
| Portal | npm lockfile, `build:integration-local`, session/CSRF transport và named-technician assignment UI | Build thường vẫn MOCK; integration transport chỉ cho HTTPS trên localhost/loopback; không tự đổi hostname Portal |
| Caddy | `portal-bff/Caddyfile.local`: TLS internal, SNI/Host, pin forwarding headers, BFF/callback routes, chặn `/api/*`, SPA fallback | Bản chạy trên host: bind/upstream loopback, đọc `frontend/dist` trên host; chưa có `/auth` hay cấu hình container |
| PostgreSQL | `infra/compose.workflow-postgres.yml` PG17.11; SQL Helpdesk và script tạo owner/runtime, actor seed, runtime privilege audit | Compose này chỉ phục vụ Workflow test; POSTGRES_USER không phải application runtime role. Setup Helpdesk khóa Docker Desktop named pipe/Windows, fresh RunId, không hỗ trợ chạy lại hoặc Linux |
| CI | `backend`, `portal-bff`, `frontend`, `repo-hygiene` | Chưa có compose-smoke; frontend job build MOCK, không chứng minh integration-local hoặc browser OIDC |

Health `/api/v1/health` chỉ trả `UP`; không kiểm tra DB, realm, grants hay Helpdesk readiness.
Backend runtime hiện không có Flyway user/password riêng trong `application.yml`.
Helpdesk dùng datasource riêng `dxos.helpdesk.*`; chỉ đặt `DB_*` là chưa đủ.
Bean Workflow vẫn cần `dxos.workflow.schema`, kể cả khi tắt auto-Flyway.

### PR #44: giữ nguồn tham khảo, không nhập nguyên branch

Đã đọc source diff `origin/main...origin/pr-44`, không chỉ PR description.
PR Draft, head `3c187d872ea6dbe1d9d258240c026bfe95060289`,
merge-base `2115de355e8df4f0567e8e8421dae8465da9cea8`, 8 file, +392/-2.
Đối chiếu thêm nội dung với main mới: Dockerfile, staging Compose và env vẫn chưa có trên main.

- Tái sử dụng có chọn lọc: multi-stage Wrapper build trong Docker, explicit COPY đầu vào,
  UID 10001, JRE runtime, `.dockerignore`, nguyên tắc giữ volume và loopback host publishing.
  `.gitattributes` main đã đặt LF cho Wrapper; xử lý CRLF trong image chỉ là phòng vệ.
- Không dùng staging Compose làm full stack: mặc định chỉ PG; Backend là profile `app`,
  thiếu OIDC và Helpdesk wiring, không bootstrap role/schema. Các biến filesystem storage
  không chứng minh provider đã có; R21 không thêm storage provider.
- Không mang nguyên README/runbook AWS cũ sang main: mô tả nhiều thành phần chưa merge
  đã lỗi thời; bằng chứng build/PG do operator báo ở SHA cũ không phải test R21.
- Không reuse tên/port/volume staging 55426, Workflow test hay tài nguyên đang được giữ lại.

## D1 — canonical issuer, networking và URL: cần Yến/Bảo/Ánh review

**Đề xuất ưu tiên:** giữ Portal `https://localhost:8443/mvp/helpdesk`, tách auth hostname
`auth.dxos.test`; canonical issuer duy nhất
`https://auth.dxos.test:8443/auth/realms/tricore-demo`.
Đây là phương án thay cho candidate issuer localhost trong #119, chưa phải quyết định.

| Luồng | URL/routing đề xuất |
| --- | --- |
| Browser → Portal/BFF | localhost:8443 → Caddy → `portal:8080` hoặc `bff:8081`; callback chính xác `/login/oauth2/code/keycloak`, post-logout `/` |
| Browser → OIDC | Host mapping rõ ràng `auth.dxos.test` → 127.0.0.1; Caddy TLS → `keycloak:8080`, prefix `/auth` nhất quán |
| Backend/BFF → discovery, token, JWKS | Cùng canonical HTTPS issuer; Docker network alias `auth.dxos.test` trỏ Caddy, listener trong container cũng là 8443; không đi vòng host gateway |
| BFF → Backend | `https://backend.demo.internal:9443` → Caddy listener nội bộ → `backend:8080`; alias trên network BFF/Caddy; 9443 không publish ra host |
| Backend/Keycloak → PostgreSQL | Service DNS `postgres:5432`, hai database/role riêng; không publish PG |

Hostname mapping trên host và trust installation là bootstrap được giải thích rõ,
không tự sửa hosts/trust store mà người vận hành không biết. Kiểm tra từ browser lẫn
Java process trong container; Docker alias không tự tạo DNS cho host.
Đề xuất cấu hình Keycloak hostname v2: `KC_HOSTNAME=https://auth.dxos.test:8443/auth`,
`KC_HTTP_RELATIVE_PATH=/auth`, `KC_HTTP_ENABLED=true`, `KC_PROXY_HEADERS=xforwarded`,
`KC_HOSTNAME_STRICT=true`, `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=false`.
Caddy chuyển nguyên `/auth/...` tới `keycloak:8080/auth/...`, không `handle_path`/strip prefix
và không thêm `/auth` lần hai. Hostname chỉ định URL quảng bá, không đổi listener Keycloak.
Caddy container phải nghe 8443 trên interface bridge, không giữ `bind 127.0.0.1` của file host-local.
Alias auth phải có trên từng network mà Backend/BFF dùng để gọi Caddy; không alias `localhost`,
không IP container cố định hay `extra_hosts=127.0.0.1`. Host chỉ map auth về IPv4 loopback;
nếu localhost chọn IPv6 nhưng host chỉ publish IPv4, preflight phải phát hiện và hướng dẫn.
Không bật hostname động hoặc rewrite token `iss`; không override JWKS để bỏ issuer validation.
Issuer được dùng nguyên văn cho actor key, nên đổi issuer cần fixture demo mới,
không cập nhật hay tái sử dụng membership của database giữ lại.
`helpdesk-local-actor-seed.ps1` khi nhận trusted realm file còn yêu cầu issuer loopback
và path `/realms/<realm>`; không dùng nguyên helper đó cho hostname/prefix mới.
Bootstrap Compose phải đối chiếu exact issuer/realm/subjects và reuse cách encode actor,
giữ validation của script host-local; bổ sung test actor seed cho canonical issuer mới.

BFF xác thực ID token theo issuer/client audience của OIDC client; Backend xác thực
access token `iss`, `aud=tricore-api`, và `resource_access.tricore-api.roles`.
Không yêu cầu ID token có audience của Backend hoặc tạo lại claims ở BFF.
BFF client riêng, confidential, `client_secret_basic`, PKCE S256, exact callback/logout;
tắt implicit/direct grant/service account; không wildcard redirect.
Ba synthetic users Employee/Coordinator/Worker có subject riêng, role mapping đúng
contract hiện tại; DB membership/ACL vẫn quyết định quyền nghiệp vụ.
Realm template versioned và user IDs provisioned phải khớp actor seed;
login thật vẫn là bước chứng minh riêng.

### D1: discovery và browser contract cần chứng minh

Đặt `I` là exact issuer ở trên, không trailing slash. Backend `OIDC_ISSUER_URI` và BFF
`PORTAL_OIDC_ISSUER_URI` cùng bằng `I`; discovery `I/.well-known/openid-configuration`
phải trả `issuer=I`. `authorization_endpoint`, `token_endpoint`, `jwks_uri` và
`end_session_endpoint` đều giữ canonical HTTPS host/port/prefix; không lộ `keycloak:8080`.
JWKS dự kiến `I/protocol/openid-connect/certs`; token/refresh dùng `.../token`.
Phải thử discovery bằng chính Spring clients hiện có, không chỉ curl; nếu cần RFC 8414
fallback thì map đúng metadata path cho realm này, không mở rewrite tùy ý.
BFF dùng `PORTAL_PUBLIC_ORIGIN=https://localhost:8443`, callback đầy đủ
`https://localhost:8443/login/oauth2/code/keycloak`; Keycloak chỉ đăng ký URI này và
post-logout `https://localhost:8443/`. `POST /bff/logout` vẫn cần session + CSRF,
sau đó browser đi end-session và quay về `/`; không hứa có backchannel logout handler.
Callback top-level GET giữ SameSite=Lax qua hai site; không đổi sang `form_post`.
Portal gọi BFF cùng origin nên không cần CORS tới Keycloak; cookie Portal không gửi sang auth host.

Caddy auth site chỉ proxy realm `tricore-demo` và assets `/auth/resources/*` cần thiết;
deny admin, master realm, health/metrics và mọi path còn lại. Không proxy management 9000.
Strip `Forwarded` và header proxy do client gửi; pin `X-Forwarded-Host=auth.dxos.test:8443`,
`X-Forwarded-Proto=https`, `X-Forwarded-Port=8443`, client IP từ peer, không cộng prefix lần hai.
Portal host chỉ có Portal/BFF routes; auth host không có BFF/API routes. Listener 8443
không được phục vụ Backend kể cả khi client giả Host/SNI của listener 9443.

**Trade-off cần duyệt:** listener TLS nội bộ của Caddy giữ nguyên HTTPS guard của BFF,
nhưng thêm một hop. Hop Caddy → Backend/Keycloak là HTTP trên private demo networks;
đây là giới hạn local demo cần owner chấp thuận, không là thiết kế production.
Không có public ingress vào Backend listener; public `/api/*` luôn bị chặn.
Tách network dữ liệu, proxy/backend và auth; không coi Docker network là thay thế ACL.
Không publish 9443 chỉ chặn host ingress; Caddy nhiều network vẫn có thể nghe 9443
trên các interface đó. Phải kiểm tra reachability và giới hạn route/listener; alias không phải ACL.

### D1: so sánh public origin

| Phương án | Khả thi và tác động source | Đánh đổi |
| --- | --- | --- |
| Hai public hostname: Portal localhost, auth.dxos.test | Alias auth → Caddy giải quyết container routing; giữ nguyên guard Portal | Thêm host mapping, hai TLS names và redirect cross-site; phải chứng minh GET callback/logout |
| Một public origin `https://demo.dxos.test:8443`, auth dưới `/auth` | Cùng host map trên host/alias trong bridge; khả thi về mạng, BFF `PortalOrigin` chấp nhận HTTPS hostname | Cần sửa opt-in allowlist + tests `sameOriginTransport.requireLocalHttps`, cập nhật callback/logout/actor fixtures; cookie Path=/ đi cả tới auth path, cần lọc cookie Portal trước Keycloak |
| Một public origin localhost như candidate #119 | Không hoạt động trực tiếp với bridge: localhost trong Java container là chính container đó | Không giải quyết bằng alias/host-gateway; host networking không đồng nhất Windows/Linux, endpoint overrides tăng wiring |

Hai hostname là ưu tiên để giữ guard integration-local hiện tại, **không phải** một public
origin tùy chỉnh là bất khả thi. Cả hai phương án routable vẫn cần Java trust ở D3 và
private HTTPS Backend hop. Chưa có proof chạy thật cho phương án nào; owner chọn trước triển khai.

### D1: container guards — thay đổi bảo mật cần review riêng

Mode `compose-demo` phải opt-in rõ ràng, mode lạ fail closed; mặc định Helpdesk vẫn tắt.
Nhánh host-local giữ nguyên `requireDisposable`; nhánh container chỉ cho exact JDBC
`jdbc:postgresql://postgres:5432/helpdesk_disposable_<validated-id>`, runtime tương ứng
`helpdesk_runtime_<validated-id>`, schema `helpdesk_demo`, bind `0.0.0.0` trong container.
Không chấp nhận query/options, nhiều host, userinfo hoặc tên gần giống allowlist.
Bind bridge đi cùng không publish Backend/BFF ports; không đổi default host-local.

**Guard thứ hai:** `PostgresWorkflowPersistence.transaction` gọi
`WorkflowEventTriggerGuard.REVIEWED.verify` trước mọi transaction, kể cả Helpdesk.
`WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE=true` một mình không cho DNS `postgres`;
schema demo cũng không khớp cloud manifest. Vì vậy sửa riêng `HelpdeskConfiguration` vẫn lỗi.
Đề xuất ngoại lệ **chỉ runtime compose-demo**, sau kiểm tra exact endpoint/database,
schema, login thực `current_user=session_user`, runtime không owner/đặc quyền/membership,
search_path an toàn, replication role origin, event_triggers on và catalog không có event trigger.
Không mở rộng `emptyLocal`: hàm này còn dùng bởi `verifyMigrations`/`WorkflowMigrations`.
Giữ nguyên cloud `verifyReadOnly`, manifest/checksum và mọi gate migration/bootstrap hiện có;
không thay bằng catch-and-ignore hoặc tắt event triggers. Đây là mở rộng trust boundary có
điều kiện, **chưa được phê duyệt**, không thể mô tả là chỉ sửa networking.

BFF giữ nguyên `checkedBackend`, dùng HTTPS alias đã nêu; không cho `http://backend:8080`.
Tests phải có mode thiếu/sai, cloud/host/DB/schema/login sai, role admin/owner/membership,
query/options, event trigger xuất hiện/bị disable, replication role sai và cloud/migration
không nhận ngoại lệ mới; giữ bộ tests host-local và HTTP-upstream denial hiện có.

## D2 — demo secrets/bootstrap: cần Yến/Bảo review

Đề xuất bootstrap sinh secret ngẫu nhiên một lần vào thư mục `.demo/` gitignored;
`.env` chỉ chứa cấu hình không bí mật. Không commit password demo cố định:
CONTRIBUTING hiện yêu cầu placeholder và không commit passwords.
Bootstrap không ghi đè secret, CA, realm hay volume đã tồn tại; thiếu/mâu thuẫn state
thì dừng có hướng dẫn, không tự rotate hoặc reset. Dùng OS CSPRNG; Linux file mode
và Windows ACL chỉ cho operator. Không yêu cầu host build Java/Node.

Mount secrets theo service, ưu tiên Compose file secrets, PostgreSQL `_FILE`,
Spring configtree; chọn cơ chế đọc file được bản Keycloak đã pin hỗ trợ.
Realm template commit không có credentials; realm render chứa demo credentials nằm
trong `.demo/`, được mount runtime, không COPY vào build context/layer.
Tách admin/provisioner, DB owner/runtime, Keycloak DB và BFF client secrets.
Frontend không nhận secrets. Mật khẩu demo user chỉ đọc từ file cục bộ có kiểm soát,
không in trong bootstrap/CI. Docker/host administrators vẫn đọc được local secrets.
Không in resolved `compose config`, environment, JWT, callback query hoặc auth payload
trong evidence; dùng `config --quiet` và log đã lọc. CA private key chỉ ở Caddy volume.

## D3 — HTTPS trust/cookies: cần Yến/Ánh review

Đề xuất Caddy local CA, persistent CA volume. Bootstrap có thể khởi tạo riêng Caddy
để xuất **public root certificate**, sau đó operator cài browser/OS trust với fingerprint
đã đối chiếu. Java truststore riêng của Backend/BFF được tạo từ cùng public root,
mount read-only; không mount toàn bộ `/data` chứa CA private key vào app.
Mount file thôi chưa cấu hình JVM: đặt `javax.net.ssl.trustStore` và `trustStoreType`
trước khi Java khởi động, kiểm tra file/fingerprint và quyền đọc của UID runtime.
OS/browser trust không tự thành Java trust; Spring SSL bundle khai báo riêng cũng không
tự áp dụng cho mọi client. Cần chứng minh cả Spring discovery/token/refresh/JWKS lẫn
JDK `HttpClient` của `PortalBackendClient` dùng truststore đó. SAN phải khớp auth hostname
và `backend.demo.internal`; certificate localhost dành cho browser. Helper keytool chạy
trong image/container phù hợp, không đòi host JDK và không đưa CA key vào build context.
Thiếu CA, CA lệch hoặc hostname sai phải fail closed; không trust-all, `-k` hay
`NODE_TLS_REJECT_UNAUTHORIZED=0`. Chứng minh trust từ container là test bắt buộc.

Giữ cookie `__Host-DXOS-Portal`: Secure, HttpOnly, SameSite=Lax, Path=/, không Domain.
Caddy kiểm tra SNI/Host, strip/pin forwarding headers; không publish admin API/management
port. Log ingress không lưu authorization-code query hay token/cookie headers.
Bootstrap CA không phụ thuộc Backend/BFF ready để tránh chu kỳ OIDC startup.
Khởi động: PG/bootstrap và Caddy TLS → Keycloak/realm ready → Backend → BFF;
Portal static có thể chạy độc lập. Caddy health là listener/TLS của chính nó;
full-stack smoke kiểm tra upstream riêng, không tạo vòng `depends_on`.

## D4 — PostgreSQL migration owner/runtime: cần Bảo/Yến review

Đề xuất một PG17, database Keycloak riêng với role chỉ sở hữu database đó;
Helpdesk database riêng, bootstrap login có quyền giới hạn trong bước provisioning,
schema owner NOLOGIN, migration/provisioning login được SET ROLE owner với quyền owner
giới hạn ở DB Helpdesk; runtime không owner, không membership vào owner/migrator.
Role/membership là cấp cluster, không được giả định SET ROLE tự bị giới hạn theo database.
Backend/BFF không nhận setup/migration/Keycloak DB credentials.
Bootstrap login cấp cao không được dùng làm Backend runtime.

Reuse nguồn SQL hiện tại: V3 + Helpdesk `policy-schema.sql`, `schema.sql`,
`definition.sql`, `runtime-grants.sql`; schema/role identifiers phải validate trước
substitution. Không sửa bytes V1–V4; không chạy V1/V2/V4 hoặc Stage6 chỉ để boot demo.
Đề xuất tắt auto-Flyway ở Backend runtime (`SPRING_FLYWAY_ENABLED=false`), giữ
`ddl-auto=none`; migration chạy bởi bước provisioning riêng, không bằng runtime role.
Không tuyên bố wiring Flyway split đã có sẵn.

Đủ cơ sở tách role ở mức thiết kế: `helpdesk-local-setup.ps1` đã tạo owner NOLOGIN,
runtime riêng, chạy SQL dưới `SET LOCAL ROLE owner` rồi kiểm tra fresh TCP runtime.
Đây là mẫu thực thi source, chưa phải bằng chứng chạy Compose. Đề xuất dùng SQL driver
offline đó với ledger riêng, không đưa subset Helpdesk vào Flyway history giả hoặc đánh
dấu V1/V2/V4 đã chạy. Owner sở hữu cả tables/functions/triggers; default privileges đặt
cho owner sau SET ROLE. Ledger ở schema provisioning riêng, runtime không được ghi/DDL.
Cấu hình cả `spring.datasource` và `dxos.helpdesk.*` về cùng DB/runtime demo đã validate,
cùng schema `helpdesk_demo`; vẫn khai báo `dxos.workflow.schema` khi Flyway tắt.
Provision `ALTER ROLE <runtime> IN DATABASE <demo-db> SET search_path` về
`helpdesk_demo,pg_catalog,pg_temp`; verify bằng login mới cho cả hai datasource,
không thêm query vào JDBC Helpdesk để lách allowlist hoặc để datasource chính ở public.
Không dùng cấu hình datasource chính trỏ shared DB để lách guard Helpdesk.
Role Keycloak có DDL trong **DB Keycloak** để Keycloak tự migrate, không phải Helpdesk
runtime; role này không có SUPERUSER/CREATEDB/CREATEROLE/BYPASSRLS hay quyền DB Helpdesk.

Chỉ init trên project/volume mới thuộc R21. PostgreSQL init scripts và Keycloak
startup import không tự nâng cấp dữ liệu đã có. Cần migration/version ledger và kiểm tra
checksum/fixture/issuer khi chạy lại: cùng version là no-op, drift/partial setup thì
fail closed, không drop/reseed/repair ngầm. Không dùng `IF NOT EXISTS` để che drift.
Tạo role/database trong pha provisioning được kiểm soát; `CREATE DATABASE` phải ngoài
transaction PostgreSQL. Sau đó connect DB đích và chạy schema/grants/definition/actor seed
trong một transaction owner; các rows definition phải cùng transaction để không bị seal.
Commit rồi verify bằng fresh runtime TCP session; chỉ ghi marker READY sau khi verify đạt.
Nếu dừng giữa commit và READY, coi là partial setup và fail closed, không tự reseed.
Revocation PUBLIC CONNECT/TEMP/CREATE và grant CONNECT phải bao phủ từng DB mới,
postgres/template1; mặc định PUBLIC CONNECT có thể phá cách ly cross-database.
Kiểm tra fresh runtime TCP login và negatives CREATE/ALTER/DROP, SET ROLE owner,
SUPERUSER/CREATEDB/CREATEROLE/BYPASSRLS, PUBLIC, cross-database và exact column grants.
Giữ DML theo runtime-grants hiện có; đây không phải Stage6 SQL policy engine acceptance.
Secret và subject IDs phải giữ ổn định qua restart, realm không tự overwrite.

## D5 — release/scope: cần Yến điều phối, Bảo/Ánh xác nhận

Đề xuất **v0.2.0**, đồng bộ #118; không tự đổi sang v0.1.1. CONTRIBUTING ghi mốc nội bộ
v0.2.0 là 2026-11-15; #119 muốn trước 2026-12-06 nếu khả thi. Đây là mốc kế hoạch,
không thay thế acceptance và không xác nhận deadline cuộc thi.
R21 có đúng sáu runtime services: postgres, keycloak, backend, bff, portal, caddy;
init/bootstrap là tác vụ hữu hạn, không thêm service runtime thứ bảy.
Chỉ Caddy publish `127.0.0.1:8443`; listener nội bộ không publish.
Pin image version/digest ở PR triển khai, xác minh tag thực tế và ghi license của
Keycloak/PG/Caddy cùng Java/Node/static-server base image, liên kết #38.

#40/ADR-007 còn nói tới MinIO và phạm vi release rộng hơn; #119 loại MinIO/APISIX/
Node-RED khỏi R21. Cần xác nhận R21 là subset prerequisite, không đóng #40/#27/#38
hay thay thế ADR-007. Không có deploy LIVE, cloud staging hoặc reset dữ liệu trong scope.

## Kế hoạch file/PR sau approval

Mọi PR triển khai bắt đầu từ main sạch mới nhất khi thực hiện, ghi exact SHA và
đối chiếu lại baseline nếu main tiến lên; không cherry-pick nguyên #44.

| Thứ tự | File dự kiến, chỉ tạo khi cần | Kiểm chứng bắt buộc |
| --- | --- | --- |
| PR A — Backend/BFF packaging và container guard | `backend/Dockerfile`, `backend/.dockerignore`, `portal-bff/Dockerfile`, `portal-bff/.dockerignore`; root `.dockerignore` nếu context BFF cần Wrapper; `HelpdeskConfiguration.java`, `WorkflowEventTriggerGuard.java` và tests liên quan | Build bằng Wrapper trong Docker, non-root; test cả hai runtime guards, host-local/cloud/migration negatives và BFF HTTPS; tách guard/packaging PR nếu quá lớn |
| PR B — Portal/Caddy HTTPS | `frontend/Dockerfile`, `frontend/.dockerignore`, `frontend/nginx.conf` nếu dùng nginx static runtime; `infra/Caddyfile.demo`; giữ `portal-bff/Caddyfile.local` cho host-local | `npm ci` theo lock, build integration-local trong image; SPA; `caddy validate`; HTTPS/cookie/Host và `/api/*` denial; test alias/private TLS listener với fixture |
| PR C — stack và provisioning | `compose.yml`, `.env.example`, `.gitignore`, `infra/keycloak/tricore-demo-realm.template.json`, `infra/postgres/` init/migration driver và ledger; `infra/` entrypoint/trust helper tối thiểu nếu cần; `scripts/demo-bootstrap.ps1`, `scripts/demo-bootstrap.sh` | Sáu services; secrets/CA; issuer/JWKS từ Java containers; fresh init + no-op rerun + drift denial; privilege negatives; runtime ready; bounded retries; không context Docker hardcoded |
| PR D — vận hành/CI/evidence | `README.md`, `infra/README.md`, `portal-bff/README.md`, `CHANGELOG.md`, `NOTICE`/inventory hiện có nếu cần; `.github/workflows/compose-smoke.yml`, `scripts/demo-smoke.*`, `docs/verification/` evidence | Config quiet, clean clone Windows/Linux, source build, readiness, restart/persistence, sanitized artifacts, reset có project/volume guard; bộ acceptance bên dưới |

Bootstrap là dependency của PR C, không để tới PR D nếu thiếu nó stack không boot.
Mỗi PR nhắm main, khoảng <=400 changed lines; nếu C quá lớn, tách C1 provisioning
và C2 orchestration. Không tạo stacked PR mặc định: chờ dependency merge rồi branch lại;
nếu cần dependency thì ghi `Depends on #N`. Các phần chưa hoàn tất chỉ `Refs #119`;
chỉ PR hoàn tất toàn bộ acceptance mới đề xuất `Closes #119`.

Review theo CONTRIBUTING/CODEOWNERS: Bảo Backend/Data/infra, Yến Identity/Gateway/
secrets/release, Ánh Portal/BFF. Helpdesk Core/BFF API thay đổi cần manual Bảo + Ánh;
tác giả không tự approve, conflict cần alternative reviewer/exception trước merge.
R21 final acceptance do Yến, kiểm chứng độc lập bởi người không viết phần tương ứng.

## Nghiệm thu và blocker

1. **Packaging:** fresh clean clone cùng integration SHA trên Windows Docker Desktop
   Linux Engine và Linux Docker Engine → bootstrap/trust → `docker compose up --build`.
   Không host jar/dist/node_modules. Chạy config quiet; xác nhận cả sáu ready, không
   chỉ running. Ghi OS/Docker/Compose, source SHA, image tags/digests, lệnh, thời gian.
2. **Identity/TLS:** browser login/logout/session expiry, Code+PKCE, exact issuer,
   ID/access-token audience đúng mục đích, JWKS và refresh; trusted Java/container TLS,
   wrong CA/hostname/missing/invalid issuer negatives; Secure cookie và CSRF 403.
3. **Helpdesk:** ba browser contexts với principal thật; Create → Triage → Assign
   named technician → Start → Resolve → Confirm. Cuối CONFIRMED, runtime version 4,
   bốn transitions; Assign tăng assignmentVersion, không thêm workflow transition.
   401/403/scoped-404/cross-actor, stale-version 409, unknown-write không retry.
4. **Data/recovery:** runtime privilege negatives, bootstrap no-op/drift checks,
   restart/recreate giữ request/history, login lại sau BFF restart vì session memory;
   lỗi DB/provider phải thấy fail closed. Không reset volume bằng chứng.
5. **Release evidence:** CI Linux smoke pinned SHA; Windows run độc lập cùng SHA;
   artifact tải được, scan secrets, skip có lý do, reviewer PASS/CONDITIONAL/BLOCKED.
   R23 chỉ PASS khi browser E2E thực sự chạy và được duyệt riêng; macOS chưa xác minh.

| Mức | Blocker hiện tại | Owner/action |
| --- | --- | --- |
| P0 | D1–D5 chưa phê duyệt; phương án hai hostname chưa có proof DNS/TLS/discovery/callback/logout từ browser và Java containers | Yến/Bảo/Ánh chọn origin, review trust boundary rồi cho phép proof triển khai riêng |
| P0 | Cả HelpdeskConfiguration và WorkflowEventTriggerGuard từ chối DB service DNS; BFF cần bind bridge và HTTPS upstream | Bảo/Ánh/Yến review ngoại lệ chỉ runtime demo, không nới emptyLocal/cloud/migration; tests giữ guard cũ |
| P0 | Chưa có sáu-service stack, realm/subjects, secrets/trust bootstrap, schema/version wiring idempotent | PR A–C, không dùng owner DB login làm runtime |
| P1 | Chưa có Windows/Linux fresh-clone evidence, readiness DB thực, compose CI hoặc browser R23 | PR D và #118, phân loại NOT_RUN riêng |
| P1 | Chưa pin toàn bộ image/digest/license; scope MinIO ở #40 cần ghi rõ subset R21 | Release owners và #38; không tuyên bố release hoàn tất |

**Approval record:** Yến D1/D2/D3/D5: PENDING; Bảo D1/D2/D4/D5: PENDING;
Ánh D1/D3/D5 và Portal/BFF: PENDING. Chưa có chữ ký thay người khác.

## Đối chiếu D1–D5 để independent review

| Quyết định | Cơ sở #119/source tại baseline | Phần còn NOT_PROVEN |
| --- | --- | --- |
| D1 | #119 cho phép ADR đổi candidate; `PortalSecurity`, `PortalOrigin`, `PortalBackendClient`, frontend `sameOriginTransport` và hai Backend guards xác định đúng phạm vi | DNS hai phía, Keycloak path/metadata, Java TLS, PKCE/JWKS/refresh, callback/logout và guard container |
| D2 | #119 cho phép bootstrap thay committed demo credentials; CONTRIBUTING cấm commit passwords | CSPRNG/ACL cross-platform, mount/read file theo image pin; `.demo/` chưa có trong .gitignore hiện tại |
| D3 | Cookie Secure/Lax và pin callback đã có trong BFF; Caddy.local có TLS internal/SNI | Export CA an toàn, truststore thực dùng bởi tất cả Java clients, negative CA/SAN và restart |
| D4 | SQL owner/runtime pattern và grants đã có; application.yml không tách Flyway credential; Helpdesk ngoài auto-Flyway | SQL driver/ledger idempotent mới, runtime privilege probes và DB readiness trên PG17 Compose |
| D5 | #119/#118 cùng target v0.2.0, sáu services và OUT scope rõ | Owner xác nhận subset #40/ADR-007; pin image/license và nghiệm thu >=2 OS |

Source để reviewer kiểm tra: [Helpdesk guard](../../backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskConfiguration.java),
[Workflow guard](../../backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerGuard.java),
[BFF OIDC](../../portal-bff/src/main/java/com/tricore/dxos/portal/PortalSecurity.java),
[frontend guard](../../frontend/src/services/mvp/http/sameOriginTransport.ts),
[SQL bootstrap hiện tại](../../scripts/helpdesk-local-setup.ps1),
[runtime grants](../../backend/src/main/resources/db/helpdesk/runtime-grants.sql).
Đủ cơ sở **review đề xuất** không có nghĩa D1–D5 APPROVED hay sẵn sàng chạy stack;
P0 ở trên chặn triển khai/nghiệm thu, không được che bằng trạng thái tài liệu.

## Kiểm tra thực hiện

Lượt hoàn thiện ADR: đọc toàn bộ ADR, Git branch/HEAD/status, #119 và comments (vẫn 0,
không approval), #40/#118, source/config/SQL/callers/tests liên quan và tài liệu chính thức.
Chỉ chỉnh ADR; không fetch lại refs, chạy app, DB hoặc Docker ở lượt này.
PASS: `git diff --check`, `git diff --cached --check`; cấu trúc PROPOSED/D1–D5,
UTF-8 và cả sáu source links tương đối hợp lệ. Chỉ ADR được stage cho commit tài liệu.
Bằng chứng khảo sát ban đầu dưới đây được giữ như lịch sử, không coi là test chạy lại:

PASS: Git status/refs/worktrees, fetch main/PR44, exact source/diff và callers/tests
của container guards; rà soát cấu hình/SQL/scripts/CI và tài liệu upstream.
Host báo Windows NT 10.0.26200.0; Docker CLI 29.7.2, Compose v5.4.0.
Lệnh version cảnh báo sandbox không đọc được Docker config; không kiểm tra daemon.
Không có memory connector để truy xuất ngữ cảnh; kết luận dựa vào Git/GitHub/source.
NOT_RUN: Docker build/up/config của stack mới, DB mutation, unit/integration tests,
Linux/macOS install, trust installation và browser E2E. Chưa có implementation để
kiểm thử; không tiếp xúc hoặc chỉnh tài nguyên Docker đang được giữ lại.

Tài liệu chính thức đã đối chiếu (2026-11-04; cần kiểm lại theo image pin khi triển khai):
[Keycloak hostname](https://www.keycloak.org/server/hostname),
[reverse proxy/context path](https://www.keycloak.org/server/reverseproxy),
[realm import](https://www.keycloak.org/server/importExport),
[Caddy Docker/local trust](https://caddyserver.com/docs/running#local-https-with-docker),
[Compose networking](https://docs.docker.com/compose/how-tos/networking/),
[startup readiness](https://docs.docker.com/compose/how-tos/startup-order/),
[file secrets](https://docs.docker.com/compose/how-tos/use-secrets/).
Đối chiếu bổ sung: [Keycloak OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers),
[Java 21 JSSE truststore](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html),
[PG17 CREATE DATABASE ngoài transaction](https://www.postgresql.org/docs/17/sql-createdatabase.html),
[PG17 SET ROLE/membership](https://www.postgresql.org/docs/17/role-membership.html),
[PG17 role defaults khi login](https://www.postgresql.org/docs/17/sql-alterrole.html).
