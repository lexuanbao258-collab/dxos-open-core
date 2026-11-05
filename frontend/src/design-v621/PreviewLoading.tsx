// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

/**
 * Suspense fallback while the lazy preview chunk loads. Its CSS lives in that
 * same chunk, so the few styles it needs are inline. `role="status"` is a
 * polite live region; nothing here is interactive.
 */
export function PreviewLoading() {
  return (
    <div
      role="status"
      style={{
        padding: "64px 24px",
        textAlign: "center",
        fontFamily: "'Be Vietnam Pro', system-ui, sans-serif",
        fontSize: 15,
        lineHeight: 1.6,
        color: "#172721",
      }}
    >
      Đang tải bản xem trước OpenTrix…
    </div>
  );
}
