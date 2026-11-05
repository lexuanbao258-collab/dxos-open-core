// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { FilterSheet } from "../components/FilterSheet";
import { PageHead } from "../components/PageHead";
import { RequestResults } from "../components/RequestResults";
import { useFittingPlaceholder, useQueueKeyboard } from "../hooks";
import { Icon } from "../icons";
import {
  activeFilterCount,
  counters,
  numbers,
  selectRows,
  stepLabel,
} from "../logic";
import {
  SERVICES,
  STEPS,
  type ListRoute,
  type ListTab,
  type Priority,
  type ServiceId,
  type Step,
} from "../model";
import { BASE, usePreview } from "../PreviewContext";

const TABS: Record<ListRoute, ReadonlyArray<readonly [ListTab, string]>> = {
  mine: [
    ["open", "Đang mở"],
    ["closed", "Đã xong"],
    ["all", "Tất cả"],
  ],
  queue: [
    ["open", "Cần theo dõi"],
    ["processing", "Đang xử lý"],
    ["all", "Tất cả"],
  ],
};

export function RequestList({ route }: { route: ListRoute }) {
  const {
    tickets,
    qa,
    setQa,
    announce,
    tabs,
    setTab,
    filters,
    patchFilters,
    resetFilters,
  } = usePreview();
  const mine = route === "mine";
  const tab = tabs[route];
  const f = filters[route];
  const count = counters(tickets, route);
  const rows = useMemo(
    () => selectRows(tickets, route, tab, f),
    [tickets, route, tab, f],
  );
  const filterCount = activeFilterCount(f);

  const [sheetOpen, setSheetOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [feedback, setFeedback] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);
  const resultsRef = useRef<HTMLElement>(null);
  const placeholder = useFittingPlaceholder(
    searchRef,
    "Tìm mã hoặc tiêu đề",
    "Tìm…",
  );

  // Reload only simulates a short loading state; it never calls a service.
  const qaRef = useRef(qa);
  qaRef.current = qa;
  useEffect(() => {
    if (!reloading) return;
    const timer = setTimeout(() => {
      setReloading(false);
      if (qaRef.current === "loading") {
        setQa("normal");
        announce("Đã tải lại danh sách mẫu.");
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [reloading, setQa, announce]);

  const inspect = useCallback(
    (ticketId: string | null) => {
      if (ticketId === null) return setFeedback("");
      const ticket = tickets.find((t) => t.id === ticketId);
      if (ticket && ticket.allowedActions.length === 0) {
        const message = `${ticketId}: chưa được máy chủ cấp thao tác xử lý.`;
        setFeedback(message);
        announce(message);
      }
    },
    [tickets, announce],
  );
  useQueueKeyboard({
    enabled: route === "queue" && qa === "normal" && !sheetOpen,
    container: resultsRef,
    searchRef,
    onInspect: inspect,
  });

  const title = mine ? "Yêu cầu của tôi" : "Bàn xử lý";
  const description = mine
    ? `${count.open} yêu cầu đang mở · ${count.closed} đã xác nhận`
    : `${count.open} cần theo dõi · ${numbers(tickets).high} ưu tiên cao`;

  return (
    <>
      <PageHead
        title={title}
        description={description}
        actions={mine ? <CreateButton /> : undefined}
      />
      <section
        className={`list-toolbar ${mine ? "mobile-mine" : ""} ${searching ? "searching" : ""}`}
        aria-label="Tìm và lọc"
      >
        <div className="segments" aria-label="Nhóm yêu cầu">
          {TABS[route].map(([value, name]) => (
            <button
              key={value}
              type="button"
              className="seg"
              aria-pressed={tab === value}
              onClick={() => setTab(route, value)}
            >
              {name}
              <small>{count[value === "all" ? "all" : value]}</small>
            </button>
          ))}
        </div>
        <div className="toolbar-main">
          <div className="search-box">
            <Icon name="search" />
            <label className="ot-sr-only" htmlFor="search">
              Tìm kiếm yêu cầu
            </label>
            <input
              id="search"
              ref={searchRef}
              className="ot-input"
              placeholder={placeholder}
              value={f.query}
              autoComplete="off"
              onChange={(e) => patchFilters(route, { query: e.target.value })}
            />
          </div>
          {mine && (
            <button
              type="button"
              className="btn search-toggle"
              aria-label="Mở tìm kiếm"
              onClick={() => {
                setSearching(true);
                requestAnimationFrame(() => searchRef.current?.focus());
              }}
            >
              <Icon name="search" />
            </button>
          )}
          <select
            className="ot-select"
            id="service-select"
            aria-label="Lọc dịch vụ"
            value={f.service}
            onChange={(e) =>
              patchFilters(route, { service: e.target.value as ServiceId | "" })
            }
          >
            <option value="">Tất cả dịch vụ</option>
            {Object.entries(SERVICES).map(([id, text]) => (
              <option key={id} value={id}>
                {text}
              </option>
            ))}
          </select>
          <select
            className="ot-select"
            id="status-select"
            aria-label="Lọc trạng thái"
            value={f.status}
            onChange={(e) =>
              patchFilters(route, { status: e.target.value as Step | "" })
            }
          >
            <option value="">Mọi trạng thái</option>
            {STEPS.map((s) => (
              <option key={s} value={s}>
                {stepLabel(s)}
              </option>
            ))}
          </select>
          <select
            className="ot-select"
            id="priority-select"
            aria-label="Lọc ưu tiên"
            value={f.priority}
            onChange={(e) =>
              patchFilters(route, { priority: e.target.value as Priority | "" })
            }
          >
            <option value="">Mọi ưu tiên</option>
            <option value="Cao">Cao</option>
            <option value="Bình thường">Bình thường</option>
          </select>
          <button
            className="btn filter-toggle"
            type="button"
            aria-haspopup="dialog"
            onClick={() => setSheetOpen(true)}
          >
            <Icon name="filter" /> Lọc{filterCount ? ` (${filterCount})` : ""}
          </button>
          <div className="toolbar-end">
            <button
              className="btn"
              type="button"
              aria-label="Tải lại dữ liệu mẫu"
              style={{ minWidth: 44 }}
              onClick={() => {
                setQa("loading");
                setReloading(true);
              }}
            >
              <Icon name="reload" />
            </button>
          </div>
        </div>
        <div className="result-note">
          <span id="count">
            {rows.length} / {count.all} yêu cầu
          </span>
          <span className="filter-chips" id="chips">
            {f.service && <span className="chip">{SERVICES[f.service]}</span>}
            {f.status && <span className="chip">{stepLabel(f.status)}</span>}
            {f.priority && <span className="chip">{f.priority}</span>}
          </span>
          <span className="sort-label">
            {mine
              ? "Sắp xếp: Mới cập nhật trước"
              : "Sắp xếp: Ưu tiên cao, cũ nhất trước"}
          </span>
        </div>
      </section>
      <div
        className="kbd-feedback"
        id="kbd-feedback"
        role="status"
        hidden={!feedback}
      >
        {feedback}
      </div>
      <section
        className="list-region"
        id="list-region"
        aria-label="Kết quả"
        ref={resultsRef}
      >
        <RequestResults
          rows={rows}
          route={route}
          qa={qa}
          onResetFilters={() => resetFilters(route)}
        />
      </section>
      {route === "queue" && (
        <p className="foot-note kbd-hint">
          Phím J/K: chuyển focus giữa yêu cầu · Enter: mở chi tiết · E: xem tình
          trạng thao tác · Esc: trở về ô tìm kiếm.
        </p>
      )}
      <FilterSheet
        open={sheetOpen}
        filters={f}
        onApply={(patch) => patchFilters(route, patch)}
        onClose={() => setSheetOpen(false)}
      />
    </>
  );
}

export function CreateButton() {
  return (
    <Link className="btn btn-primary" to={`${BASE}/create`}>
      <Icon name="plus" /> Tạo yêu cầu
    </Link>
  );
}
