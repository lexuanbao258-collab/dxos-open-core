<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# Changelog

Mọi thay đổi người dùng thấy được của DX-OS Open-Core được ghi ở đây.
Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/),
phiên bản theo [Semantic Versioning](https://semver.org/lang/vi/).

## [Unreleased]

### Added

- Quy ước đóng góp (`CONTRIBUTING.md`), mẫu issue và PR, `CODEOWNERS`.
- CI chung cho mọi PR vào `main`: build và test backend, frontend, kiểm tra repo.

### Changed

- `backend/gradlew` có quyền thực thi để chạy được trên Linux và macOS.

### Removed

- Workflow riêng cho Issue 23; các test đó chạy trong CI chung.
