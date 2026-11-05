// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useLayoutEffect, useState, type RefObject } from "react";

/** True when `text` fits on one line inside the input's content box. */
export function textFitsInput(input: HTMLInputElement, text: string): boolean {
  const cs = getComputedStyle(input);
  const available =
    input.clientWidth -
    (parseFloat(cs.paddingLeft) || 0) -
    (parseFloat(cs.paddingRight) || 0);
  // Not laid out (jsdom, display:none): nothing to measure, keep full text.
  if (available <= 0) return true;
  const ctx = document.createElement("canvas").getContext("2d");
  if (!ctx) return true;
  ctx.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
  return ctx.measureText(text).width <= available;
}

/**
 * Picks the long placeholder when it fits the input's real width and the
 * short one otherwise. Measuring the element (instead of a fixed `max-width`
 * breakpoint) removes the 361–370px gap where the long text was clipped.
 */
export function useFittingPlaceholder(
  ref: RefObject<HTMLInputElement | null>,
  full: string,
  short: string,
): string {
  const [text, setText] = useState(full);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => setText(textFitsInput(el, full) ? full : short);
    measure();
    const observer =
      typeof ResizeObserver === "undefined"
        ? null
        : new ResizeObserver(measure);
    observer?.observe(el);
    window.addEventListener("resize", measure);
    // Re-measure once the self-hosted fonts are really loaded.
    void document.fonts?.ready.then(measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, [ref, full, short]);
  return text;
}

const isShown = (el: Element) => el.getClientRects().length > 0;

interface QueueKeyboardOptions {
  enabled: boolean;
  container: RefObject<HTMLElement | null>;
  searchRef: RefObject<HTMLInputElement | null>;
  /** Called with the focused ticket id on `E`, or null to clear. */
  onInspect: (ticketId: string | null) => void;
}

/**
 * Bàn xử lý shortcuts: J/K move focus, E reports the action state of the
 * focused ticket (never performs a write), Esc returns to search.
 */
export function useQueueKeyboard({
  enabled,
  container,
  searchRef,
  onInspect,
}: QueueKeyboardOptions): void {
  useEffect(() => {
    if (!enabled) return;
    // Table links and card links coexist in the DOM; CSS shows one set.
    const controls = () =>
      [
        ...(container.current?.querySelectorAll<HTMLAnchorElement>(
          "a.row-link[data-ticket],a.mobile-card[data-ticket]",
        ) ?? []),
      ].filter(isShown);
    const onKey = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      const target = event.target as Element | null;
      if (
        target?.closest?.(
          'input,textarea,select,[contenteditable="true"],[role="combobox"]',
        )
      )
        return;
      const list = controls();
      if (!list.length) return;
      const current = list.indexOf(document.activeElement as HTMLAnchorElement);
      const key = event.key.toLowerCase();
      if (key === "j" || key === "k") {
        event.preventDefault();
        const dir = key === "j" ? 1 : -1;
        const next =
          current < 0
            ? dir === 1
              ? 0
              : list.length - 1
            : Math.max(0, Math.min(list.length - 1, current + dir));
        list[next].focus();
        list[next].scrollIntoView?.({ block: "nearest" });
        return;
      }
      if (key === "e" && current >= 0) {
        event.preventDefault();
        onInspect(list[current].dataset.ticket ?? null);
        return;
      }
      if (event.key === "Escape" && current >= 0) {
        event.preventDefault();
        searchRef.current?.focus();
      }
    };
    const onFocusIn = (event: FocusEvent) => {
      if (
        (event.target as Element | null)?.matches?.(
          "a.row-link[data-ticket],a.mobile-card[data-ticket]",
        )
      )
        onInspect(null);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("focusin", onFocusIn);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("focusin", onFocusIn);
    };
  }, [enabled, container, searchRef, onInspect]);
}
