<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# Đóng góp cho DX-OS Open-Core

Tài liệu này là quy ước làm việc chung của nhóm TRICORE. Một nguyên tắc gốc:
**chỉ những gì đã merge vào `main` mới được tính là xong.**

## Vai trò

| Vai trò | Người | GitHub |
| --- | --- | --- |
| Điều phối, reviewer chính, người bấm merge | Yến | @phanyen06ym-ien |
| Owner Workflow & Data, quản trị repo | Bảo | @lexuanbao258-collab |
| Owner Application, web, truyền thông | Ánh | @ngang02012007-crypto |

- PR của Bảo và Ánh: Yến review và merge.
- PR của Yến: Bảo approve, sau đó Yến merge. Không ai tự merge PR của mình khi chưa có approve.
- Giờ review cố định: 12:00–12:30 và 21:00–21:30. Merge train: 21:00 thứ Ba và thứ Sáu.

## Luồng làm việc

```
Issue sẵn sàng → nhánh từ main → PR vào main → CI xanh → 1 approve → squash merge → release
```

1. **Issue**: mỗi việc trên 2 giờ có một issue, đúng 1 người nhận, 1 milestone, label `area:*`.
2. **Nhánh**: tạo từ `main` mới nhất, tên `<loại>/<phần>-<mô-tả>`, sống tối đa 3 ngày.
3. **PR**: base luôn là `main`, tối đa 400 dòng, ghi `Closes #N`.
4. **CI** phải xanh. **1 approve** của người không phải tác giả.
5. **Merge**: chỉ squash. Tiêu đề PR trở thành commit trên `main`.

Không push thẳng lên `main` hay `gh-pages`. Không tạo nhánh `review/*`, `candidate/*`, `diagnostic/*`.

## Lệnh hằng ngày

```bash
# 1. Bắt đầu một việc
git switch main
git pull --ff-only
git switch -c feat/workflow-outbox-event

# 2. Làm việc, commit nhỏ, commit thường xuyên
git add -p
git commit -m "feat(workflow): write outbox event in transition transaction"

# 3. Cập nhật với main khi main có thay đổi mới
git fetch origin
git merge origin/main          # hoặc bấm "Update branch" trên trang PR

# 4. Đẩy lên và mở PR
git push -u origin HEAD
gh pr create --base main --fill --assignee @me

# 5. Sau khi PR đã merge
git switch main
git pull --ff-only
git branch -d feat/workflow-outbox-event
```

Nếu lỡ commit vào `main` ở máy mình: `git switch -c feat/ten-viec` rồi
`git switch main && git reset --hard origin/main` (chỉ làm khi chưa push).

## Đặt tên

**Nhánh**: `<loại>/<phần>-<mô-tả>`, ví dụ `feat/gateway-apisix`, `fix/portal-flaky-test`,
`docs/site-integration-guide`.

**Commit và tiêu đề PR**: [Conventional Commits](https://www.conventionalcommits.org/).

```
<loại>(<phần>): <mô tả ngắn, viết thường, không chấm cuối>
```

- Loại: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, `build`.
- Phần: `identity`, `gateway`, `bff`, `workflow`, `data`, `helpdesk`, `facility`,
  `portal`, `site`, `infra`, `docs`, `repo`.

CI kiểm tiêu đề PR theo định dạng này.

## Pull request

- Tối đa 400 dòng thay đổi (không tính lockfile, file sinh tự động). Lớn hơn thì tách.
- Mô tả theo mẫu, không quá 150 chữ. Bằng chứng kiểm thử là kết quả CI trên chính PR.
- Draft tối đa 48 giờ.
- Không xếp chồng PR. Nếu buộc phải phụ thuộc, ghi `Depends on #N` và chờ PR kia merge.

## Review

- Kết luận chỉ có **Approve** hoặc **Request changes**.
- Góp ý gắn vào dòng code, tối đa 10 góp ý mỗi lượt.
  Ghi `[chặn]` cho điều bắt buộc sửa, `[nhỏ]` cho góp ý tuỳ chọn.
- Ý kiến tổng quát tối đa 5 dòng. Phân tích dài viết vào ADR hoặc issue mới.
- Việc ngoài phạm vi PR thì mở issue mới, không chặn PR hiện tại.

## Issue, label, milestone

| Label | Dùng khi |
| --- | --- |
| `area:identity`, `area:gateway`, `area:workflow`, `area:data` | Việc thuộc một Core |
| `area:portal`, `area:site`, `area:infra`, `area:comms` | Ứng dụng, web, hạ tầng, truyền thông |
| `prio:p0` | Chặn release gần nhất |

Milestone theo release: `v0.1.0` (15/11), `v0.2.0` (22/11), `v0.3.0` (29/11), `v1.0.0` (05/12).
Trạng thái không dùng label; trạng thái là cột trên bảng GitHub Project.

## Định nghĩa "Xong"

- [ ] PR đã merge vào `main`, CI xanh.
- [ ] Đạt hết tiêu chí nghiệm thu của issue.
- [ ] Có test cho logic mới.
- [ ] File mới có SPDX header.
- [ ] `CHANGELOG.md` có dòng mô tả nếu người dùng thấy được thay đổi.
- [ ] README, tài liệu API đã khớp nếu thay đổi hành vi bên ngoài.

## Giấy phép trong từng file

Mỗi file mã nguồn mới mở đầu bằng:

```java
// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0
```

SQL dùng `--`, YAML và shell dùng `#`, Markdown dùng `<!-- -->`.

## Báo cáo

- Hằng ngày 22:00, mỗi người 3 dòng trong nhóm chat: **Xong** · **Mai** · **Kẹt**.
- Tối Chủ Nhật, Yến mở issue `Báo cáo tuần N` gửi thầy hướng dẫn. Chỉ ghi việc có link kiểm được.

## Bảo mật

Không commit bí mật, token, mật khẩu hay dữ liệu người dùng thật. Biến môi trường khai báo
trong `.env.example` với giá trị giả; `.env` thật không bao giờ commit. Câu trả lời khảo sát
gốc và ảnh chưa xin phép không đưa lên repo.
