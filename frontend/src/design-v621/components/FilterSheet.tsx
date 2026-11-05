// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useRef, useState } from "react";
import {
  SERVICES,
  STEPS,
  STEP_LABELS,
  type ListFilters,
  type Priority,
  type ServiceId,
  type Step,
} from "../model";

interface Props {
  open: boolean;
  filters: ListFilters;
  onApply: (patch: Pick<ListFilters, "service" | "status" | "priority">) => void;
  onClose: () => void;
}

/** Mobile filter bottom sheet on a native modal <dialog>. */
export function FilterSheet({ open, filters, onApply, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const [service, setService] = useState<ListFilters["service"]>("");
  const [status, setStatus] = useState<ListFilters["status"]>("");
  const [priority, setPriority] = useState<ListFilters["priority"]>("");

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      setService(filters.service);
      setStatus(filters.status);
      setPriority(filters.priority);
      if (typeof dialog.showModal === "function") dialog.showModal();
      else dialog.setAttribute("open", "");
    } else if (!open && dialog.open) {
      if (typeof dialog.close === "function") dialog.close();
      else dialog.removeAttribute("open");
    }
    // Draft values are seeded only when the sheet opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  return (
    <dialog
      ref={ref}
      id="filter-dialog"
      aria-labelledby="filter-title"
      onClose={onClose}
    >
      <div className="sheet-head">
        <strong id="filter-title">Lọc yêu cầu</strong>
        <button
          className="btn"
          type="button"
          id="filter-close"
          aria-label="Đóng bộ lọc"
          onClick={onClose}
        >
          Đóng
        </button>
      </div>
      <div className="sheet-content">
        <label htmlFor="sheet-service">Dịch vụ</label>
        <select
          className="ot-select"
          id="sheet-service"
          value={service}
          onChange={(e) => setService(e.target.value as ServiceId | "")}
        >
          <option value="">Tất cả dịch vụ</option>
          {Object.entries(SERVICES).map(([id, text]) => (
            <option key={id} value={id}>
              {text}
            </option>
          ))}
        </select>
        <label htmlFor="sheet-status">Trạng thái</label>
        <select
          className="ot-select"
          id="sheet-status"
          value={status}
          onChange={(e) => setStatus(e.target.value as Step | "")}
        >
          <option value="">Mọi trạng thái</option>
          {STEPS.map((s) => (
            <option key={s} value={s}>
              {STEP_LABELS[s]}
            </option>
          ))}
        </select>
        <label htmlFor="sheet-priority">Ưu tiên</label>
        <select
          className="ot-select"
          id="sheet-priority"
          value={priority}
          onChange={(e) => setPriority(e.target.value as Priority | "")}
        >
          <option value="">Mọi mức ưu tiên</option>
          <option value="Cao">Cao</option>
          <option value="Bình thường">Bình thường</option>
        </select>
      </div>
      <div className="sheet-actions">
        <button
          className="btn"
          id="clear-sheet"
          type="button"
          onClick={() => {
            onApply({ service: "", status: "", priority: "" });
            onClose();
          }}
        >
          Xóa lọc
        </button>
        <button
          className="btn btn-primary"
          id="apply-sheet"
          type="button"
          onClick={() => {
            onApply({ service, status, priority });
            onClose();
          }}
        >
          Áp dụng
        </button>
      </div>
    </dialog>
  );
}
