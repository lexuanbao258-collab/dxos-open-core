// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { PREVIEW_TICKETS } from "./fixtures";
import { emptyFilters } from "./logic";
import type {
  ListFilters,
  ListRoute,
  ListTab,
  PreviewTicket,
  QaScenario,
} from "./model";

export const BASE = "/design-v621";

interface PreviewState {
  tickets: readonly PreviewTicket[];
  qa: QaScenario;
  setQa: (q: QaScenario) => void;
  announcement: string;
  announce: (message: string) => void;
  tabs: Record<ListRoute, ListTab>;
  setTab: (route: ListRoute, tab: ListTab) => void;
  filters: Record<ListRoute, ListFilters>;
  patchFilters: (route: ListRoute, patch: Partial<ListFilters>) => void;
  resetFilters: (route: ListRoute) => void;
}

const Ctx = createContext<PreviewState | null>(null);

export function PreviewProvider({ children }: { children: ReactNode }) {
  const [qa, setQa] = useState<QaScenario>("normal");
  const [announcement, setAnnouncement] = useState("");
  const [tabs, setTabs] = useState<Record<ListRoute, ListTab>>({
    mine: "open",
    queue: "open",
  });
  const [filters, setFilters] = useState<Record<ListRoute, ListFilters>>({
    mine: emptyFilters(),
    queue: emptyFilters(),
  });
  const announce = useCallback((m: string) => setAnnouncement(m), []);
  const setTab = useCallback(
    (route: ListRoute, tab: ListTab) =>
      setTabs((t) => ({ ...t, [route]: tab })),
    [],
  );
  const patchFilters = useCallback(
    (route: ListRoute, patch: Partial<ListFilters>) =>
      setFilters((f) => ({ ...f, [route]: { ...f[route], ...patch } })),
    [],
  );
  const resetFilters = useCallback(
    (route: ListRoute) =>
      setFilters((f) => ({ ...f, [route]: emptyFilters() })),
    [],
  );
  const value = useMemo<PreviewState>(
    () => ({
      tickets: PREVIEW_TICKETS,
      qa,
      setQa,
      announcement,
      announce,
      tabs,
      setTab,
      filters,
      patchFilters,
      resetFilters,
    }),
    [
      qa,
      announcement,
      announce,
      tabs,
      setTab,
      filters,
      patchFilters,
      resetFilters,
    ],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function usePreview(): PreviewState {
  const v = useContext(Ctx);
  if (!v) throw new Error("usePreview must be used inside PreviewProvider");
  return v;
}
