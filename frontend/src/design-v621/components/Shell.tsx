// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useRef } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { Icon, type IconName } from "../icons";
import type { QaScenario } from "../model";
import { BASE, usePreview } from "../PreviewContext";

const NAV = [
  ["", "Tổng quan"],
  ["/mine", "Yêu cầu của tôi"],
  ["/queue", "Bàn xử lý"],
  ["/create", "Tạo yêu cầu"],
  ["/integration", "Hệ thống"],
] as const;

const BOTTOM: ReadonlyArray<readonly [string, string, IconName]> = [
  ["", "Tổng quan", "home"],
  ["/mine", "Yêu cầu", "ticket"],
  ["/create", "Tạo mới", "plus"],
  ["/queue", "Bàn xử lý", "queue"],
];

const TITLES: Record<string, string> = {
  "": "Tổng quan",
  "/mine": "Yêu cầu của tôi",
  "/queue": "Bàn xử lý",
  "/create": "Tạo yêu cầu",
  "/integration": "Trạng thái tích hợp",
};

export const QA_NAMES: Record<Exclude<QaScenario, "normal">, string> = {
  loading: "Đang tải",
  empty: "Không có dữ liệu",
  error: "Lỗi đọc",
  success: "Thành công",
  unknown: "Kết quả ghi chưa xác định",
};
const QA_MOBILE_NAMES = { ...QA_NAMES, unknown: "Ghi chưa xác định" };

/** Which top-level section a pathname belongs to (detail → its origin). */
function useSection(): string {
  const { pathname, search } = useLocation();
  const rest = pathname.slice(BASE.length).replace(/\/+$/, "");
  if (rest.startsWith("/detail")) {
    const from = new URLSearchParams(search).get("from");
    return from === "queue" ? "/queue" : from === "overview" ? "" : "/mine";
  }
  return rest in TITLES ? rest : "";
}

export function Shell() {
  const { qa, setQa, announce, announcement } = usePreview();
  const { pathname } = useLocation();
  const section = useSection();
  const mainRef = useRef<HTMLElement>(null);
  const first = useRef(true);
  const rest = pathname.slice(BASE.length).replace(/\/+$/, "");
  const title = rest.startsWith("/detail")
    ? "Chi tiết yêu cầu"
    : (TITLES[rest] ?? TITLES[""]);

  useEffect(() => {
    document.title = `${title} · OpenTrix V6.2.1 (MOCK)`;
  }, [title]);

  // Mirror the HTML hashchange behaviour: scroll to top and focus <main>
  // after every navigation except the initial render.
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    window.scrollTo?.(0, 0);
    mainRef.current?.focus({ preventScroll: true });
  }, [pathname]);

  const active = qa !== "normal";
  const link = (suffix: string) => `${BASE}${suffix}`;
  const navProps = (suffix: string) =>
    section === suffix ? ({ "aria-current": "page" } as const) : {};

  return (
    <div className="ot-root">
      <a
        className="ot-skip-link"
        href="#main"
        onClick={(e) => {
          e.preventDefault();
          mainRef.current?.focus();
        }}
      >
        Đến nội dung chính
      </a>
      <header className="ot-topbar">
        <div className="top-inner">
          <Link
            className="ot-brand"
            to={link("")}
            aria-label="OpenTrix, về Tổng quan"
          >
            <span className="brand-name">OpenTrix</span>
            <span className="brand-line" aria-hidden="true" />
            <span className="brand-sub">Service Portal · DX-OS Open-Core</span>
          </Link>
          <nav
            className="desktop-nav"
            id="desktop-nav"
            aria-label="Điều hướng chính"
          >
            {NAV.map(([suffix, text]) => (
              <Link key={suffix} to={link(suffix)} {...navProps(suffix)}>
                {text}
              </Link>
            ))}
          </nav>
          <div className="header-end">
            <span
              className="mode"
              title="Dữ liệu mô phỏng, không có kết nối LIVE"
            >
              MOCK / DEV
            </span>
            <span className="persona">
              Chế độ: <strong>Demo</strong>
            </span>
            <span className="mobile-system-link">
              <Link to={link("/integration")}>Hệ thống</Link>
            </span>
          </div>
        </div>
      </header>
      <div className="ot-wrap">
        <aside
          className={`ot-notice ${active ? "qa-active" : ""}`}
          aria-label="Trạng thái môi trường"
        >
          <span className="notice-label">
            <span className="notice-long">
              Đây là dữ liệu mẫu để xem trước thiết kế. Các kết nối và thao tác
              LIVE chưa được xác nhận.
            </span>
            <span className="ot-sr-only">
              Đây là dữ liệu mô phỏng, không phải thông tin vận hành thật.
            </span>
            <span className="mobile-only">Dữ liệu mẫu · Chưa kết nối LIVE</span>
          </span>
          {active && (
            <>
              <span id="qa-banner-state" className="qa-banner-state">
                <span className="qa-state-desktop">
                  Mô phỏng: {QA_NAMES[qa]}
                </span>
                <span className="qa-state-mobile">
                  Mô phỏng: {QA_MOBILE_NAMES[qa]}
                </span>
              </span>
              <button
                id="qa-banner-reset"
                className="qa-banner-reset"
                type="button"
                aria-label="Tắt mô phỏng"
                onClick={() => {
                  setQa("normal");
                  mainRef.current?.focus({ preventScroll: true });
                  announce("Đã tắt mô phỏng trạng thái giao diện.");
                }}
              >
                Tắt
              </button>
            </>
          )}
          <Link to={link("/integration")}>Xem giới hạn</Link>
        </aside>
        <main id="main" tabIndex={-1} ref={mainRef}>
          <div id="view">
            <div className="screen">
              <Outlet />
            </div>
          </div>
        </main>
        <footer className="ot-footer">
          <span>OpenTrix · Service Portal V6.2.1 · bản xem trước nội bộ</span>
          <span>Không dùng để xác nhận năng lực LIVE.</span>
        </footer>
      </div>
      <nav
        className="bottom-nav"
        id="bottom-nav"
        aria-label="Điều hướng nhanh trên điện thoại"
      >
        {BOTTOM.map(([suffix, text, icon]) => (
          <Link key={suffix} to={link(suffix)} {...navProps(suffix)}>
            <Icon name={icon} />
            <span>{text}</span>
          </Link>
        ))}
      </nav>
      <div
        className="ot-sr-only"
        id="announcement"
        aria-live="polite"
        aria-atomic="true"
      >
        {announcement}
      </div>
    </div>
  );
}
