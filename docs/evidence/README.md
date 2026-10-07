# Evidence

Nơi lưu bằng chứng kiểm tra gắn với issue, branch, commit và PR;
không lưu credentials hoặc generated build output.

## TASK 01 — Repository & Package Baseline

- Issue title đề xuất: `Prepare repository and Core package baseline`.
  Chưa có số issue cho task này; chưa tạo PR hoặc push/merge.
- Branch: `chore/core-package-baseline`.
- Commit trước task: `6569d22fb4c62a0c241b02cc28764c33c6cc8f9e`.
- Checkpoint 1: `1bbd7cb` — `chore: add repository collaboration structure`.
- Checkpoint 2: `d53a273` — `chore: add core package boundaries`.
- Checkpoint 3: `docs: document repository baseline and local configuration`
  chứa root README, environment template và bằng chứng xác minh này.

Xác minh ngày 2026-10-07 trên Windows, JDK 21.0.11, Gradle Wrapper 8.14.3:

| Lệnh / kiểm tra | Kết quả |
| --- | --- |
| `backend/`: `.\gradlew.bat test` | PASS: 8 suites, 110 tests; 0 failures/errors/skipped |
| `backend/`: `.\gradlew.bat build` | PASS; tạo executable JAR và plain JAR trong `backend/build/libs/` |
| `git diff --check` | PASS |
| Diff với commit trước task cho source cũ, tests, config, build và Wrapper | Không đổi |
| 10 `package-info.java` mới | Chỉ responsibility comment và package declaration |
| `.env.example` | Đúng 5 biến `DB_*`, tất cả giá trị trống |
| `backend/bin/` | Đã xác minh 30 `.class` và 3 resource copies, xóa đúng thư mục; rule ignore hoạt động |

Lần test đầu trong sandbox bị chặn mạng khi Wrapper tải Gradle;
chạy lại với quyền truy cập cần thiết đã PASS. Không sửa build hoặc tests.

SHA-256 trước/sau task khớp byte-for-byte:

- V1: `0DB782CC6BA909304812D29EA377EBB1B41A7E7784AADB56E792EFBFB19A2DC7`.
- V2: `3EEB7E8BD02370D8EDFC55F80D4387F9C7C2323F7646525E85D18BB12A3E03F4`.

Chỉ có V1/V2; không tạo migration mới và giữ `ddl-auto: none`.
Unit/service/MVC tests không cần PostgreSQL; chưa xác minh startup,
migration, rollback hoặc concurrency trên PostgreSQL thật trong task này.

**ARCHITECTURE REVIEW NOTE:** Modular monolith được giữ nguyên. Request vẫn
là application prototype có JPA và lifecycle IT Support; không chuyển nghiệp vụ
đó vào Core. Skeleton mới không import provider, Identity hoặc Request, chưa có
implementation và chưa cưỡng chế dependency bằng công cụ. Không chọn provider
mới hoặc tách service; contract Workflow Core thuộc task tiếp theo.
