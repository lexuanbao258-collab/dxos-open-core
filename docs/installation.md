# Cài đặt và kiểm tra DX-OS (Windows / phát triển cục bộ)

> **Phạm vi tài liệu — 2026-10-25:** Trên `main` có Backend modular monolith và các tài liệu Core. **Frontend React/Vite đang nằm trong Draft [PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51), chưa được merge**; Helpdesk API tích hợp và B06 E2E đang ở Draft [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63), còn phụ thuộc Draft [PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58). Các bước dưới đây **không** khẳng định tích hợp LIVE hay demo end-to-end đã PASS.

## Yêu cầu công cụ

- **Git**, **PowerShell** trên Windows.
- **Java JDK 21** cho Backend; có `JAVA_HOME` hợp lệ. Dùng Gradle Wrapper trong `backend/`, không cần Gradle toàn cục.
- **Node.js >=22.12.0** và npm **chỉ khi thử Portal ở PR #51** (xem `frontend/package.json` trên branch PR).
- **Docker** chỉ khi cần kiểm thử PostgreSQL local; Docker Compose được sử dụng cho cấu hình hạ tầng local đã có.
- Với test **B06**: Keycloak local thực ở `http://localhost:8180/realms/tricore`, access token hợp lệ từ các tài khoản thử nghiệm được cấp phát riêng, PostgreSQL 18 disposable, và xác nhận của người vận hành. Không dùng token giả để thay thế E2E này.

Không cần cung cấp mật khẩu để đọc mã nguồn hay chạy phần lớn unit tests. Không gửi secret, `.env` thật, JWT hoặc CA/private key lên Git.

## 1. Lấy mã nguồn và kiểm tra Backend trên `main`

Trong PowerShell, có thể clone dưới ổ D: như ví dụ sau, hoặc chọn thư mục riêng:

```powershell
Set-Location D:\olp
git clone https://github.com/lexuanbao258-collab/dxos-open-core.git
Set-Location .\dxos-open-core\backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11' # đổi nếu JDK 21 đặt ở nơi khác
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat --version
.\gradlew.bat test
.\gradlew.bat build
```

Các lệnh trên dùng **commit bạn thực sự checkout**; chưa chạy ở thời điểm viết tài liệu này, vì vậy không có PASS mới được công bố. Một số integration tests chỉ chạy khi cung cấp đúng môi trường hoặc có thể skip theo gate. Xem [backend/README.md](../backend/README.md) để biết lệnh kiểm chứng cụ thể và giới hạn mỗi suite.

**Không** tự bật `dxos.helpdesk.enabled` trên `main` để thử các endpoint PR #63. `GET /api/v1/health` chỉ kiểm tra sức khỏe cơ bản, không chứng minh phân quyền, Workflow hoặc persistence đã nghiệm thu.

## 2. Cơ sở dữ liệu local: hai mục đích khác nhau

### A. Kiểm thử Workflow có thể tái lập (nên ưu tiên)

Từ `backend/`, sau khi đã cài **Docker, JDK 21 và OpenSSL đi cùng Git for Windows**:

```powershell
powershell.exe -NoProfile -File ..\scripts\workflow-verify-local.ps1
if ($LASTEXITCODE -ne 0) { throw 'Local verification failed' }
```

Script hiện có được tài liệu Backend mô tả là tạo **PostgreSQL 17.11 disposable trên loopback**, thực thi bộ kiểm thử được phê duyệt và dọn các tài nguyên riêng sau đó. **Đọc mã script trước khi chạy** và chỉ chạy khi Docker local thuộc quyền quản lý của bạn. Đây **không phải** B06 real Keycloak.

### B. Muốn giữ database để phát triển và restart

`infra/compose.workflow-postgres.yml` định nghĩa **PostgreSQL 17.11**, container bind `127.0.0.1:55425`, tên database và login `workflow_test`, cùng **named volume lưu dữ liệu**.

Từ thư mục `backend/`, **chỉ trên Docker local do bạn sở hữu**:

```powershell
$localDb = Get-Credential -UserName workflow_test -Message 'Local PostgreSQL credentials'
$env:WORKFLOW_LOCAL_DB_PASSWORD = $localDb.GetNetworkCredential().Password
docker compose -f ..\infra\compose.workflow-postgres.yml up -d --wait
```

**Chỉ chạy Compose chưa đủ** để cài đầy đủ schema, role runtime, migration, seed, Request business tables và Portal. Làm theo phần [“Chạy backend local từ clone mới”](../backend/README.md#chạy-backend-local-từ-clone-mới) cho volume **mới**, quyền tối thiểu và đúng thứ tự. Dừng Compose thông thường giữ dữ liệu; không sử dụng `down -v` khi còn cần volume, không tự chạy lại migrations/smoke với run ID đã dùng.

Tất cả lệnh provisioning, Flyway, role/ACL, sửa schema có dữ liệu, hoặc smoke ghi vào shared/staging/production đều **nằm ngoài hướng dẫn nhanh này** và cần phê duyệt riêng. Không dùng mặc định Supabase cloud.

## 3. Chạy thử Portal của Ánh — **chỉ mock/development**

Frontend nằm trong **nhánh `feature/issue39-portal-ui` / Draft PR #51**, không tồn tại trên `main` tại checkpoint này. **Clone ra thư mục riêng** để tránh thay đổi Backend đang làm:

```powershell
Set-Location D:\olp
git clone --branch feature/issue39-portal-ui --single-branch https://github.com/lexuanbao258-collab/dxos-open-core.git dxos-portal-preview
Set-Location .\dxos-portal-preview\frontend
node --version
npm.cmd ci
npm.cmd run dev
```

Truy cập `http://127.0.0.1:5173/session` nếu Vite hiển thị cổng mặc định đó; nếu không, dùng địa chỉ mà terminal in ra.

Build và test tùy chọn:

```powershell
npm.cmd run build
npm.cmd run test
npx.cmd playwright install chromium
npm.cmd run test:smoke
npm.cmd run check:security
```

Xem [Frontend README trên nhánh của Ánh](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/README.md) và [mock demo guide](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/DEMO_GUIDE.md). Demo dùng fixtures / `MockRequestService`; không đòi Backend. `HttpApiClient` hiện chặn LIVE, và UI giả lập 401/403 không chứng minh API thật đã được kiểm quyền.

## 4. Backend Helpdesk và B06 — **candidate, chưa phải hướng dẫn production**

[PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) chứa 9 route Helpdesk riêng theo prefix `/api/v1/mvp/requests`, với DTO `requestType=IT_HELPDESK`, `state`, `requesterId`, `assigneeId`, `version`, `assignmentVersion`, `allowedActions`, `{items,nextCursor}` và một số trường detail tùy quyền. Chúng **chưa có trên main** ở checkpoint này.

Để Yến hoặc người vận hành được cấp quyền chạy focused **real Keycloak → HTTP → PostgreSQL 18** test candidate, đọc [B06 run guide tại HEAD ghi nhận](https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/docs/evidence/issue53-b06-real-keycloak-helpdesk-e2e.md). Chỉ thực thi trên checkout **đúng HEAD của PR #63 lúc chạy**, với bốn access token từ Keycloak thực được giữ kín trong **process environment**, và database disposable do launcher tạo. Không tái sử dụng cơ sở dữ liệu hay token từ test khác.

**Không ghi B06 PASS** cho đến khi có báo cáo test ở cùng HEAD với phiên bản/command, PASS/FAIL/SKIP và trạng thái cleanup. Trước đó, chỉ có evidence độc lập 2 + 28 test HTTP/PostgreSQL tại HEAD **cũ** `a3bf42e`.

## 5. Sau khi cài đặt, kiểm tra gì?

- Lưu **SHA** của checkout: `git rev-parse HEAD`.
- Phân biệt `main`, branch Frontend mock, branch Backend candidate.
- Ghi lại môi trường, lệnh chạy, lỗi/skip và URL local; không in secrets.
- Không suy từ việc mở được trang React hoặc health JSON rằng hệ thống **SSO → Gateway → Helpdesk → PostgreSQL** đã thông suốt.
- Nếu có lỗi security thật, đọc [SECURITY.md](../SECURITY.md). Nếu muốn đóng góp sửa lỗi, đọc [CONTRIBUTING.md](../CONTRIBUTING.md).

**Lưu ý phiên bản:** Không dùng `docker compose up` tại root với kỳ vọng tự khởi chạy toàn bộ Frontend + Backend + Keycloak; repo ở checkpoint này chưa có một Compose stack thống nhất được nghiệm thu cho luồng đó.
