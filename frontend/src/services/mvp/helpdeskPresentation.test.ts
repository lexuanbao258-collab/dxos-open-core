import { describe, expect, expectTypeOf, it } from "vitest";
import type { ServiceRequest } from "../../domain/request";
import { helpdeskCursor, helpdeskWire } from "../../test/helpdeskFixtures";
import {
  decodeHelpdeskHistory,
  decodeHelpdeskPage,
  decodeHelpdeskRequest,
} from "./http/codecs";
import { HELPDESK_CONTRACT_HEAD } from "./http/types";
import {
  mapHelpdeskHistoryPresentation,
  mapHelpdeskListPresentation,
  mapHelpdeskRequestPresentation,
  type HelpdeskPortalRequestPresentation,
} from "./helpdeskPresentation";

describe("APP-10 exact PR #63 RequestView mapping", () => {
  it("pins the reviewed Backend HEAD and keeps the legacy MOCK model incompatible", () => {
    expect(HELPDESK_CONTRACT_HEAD).toBe(
      "e12d105c89400a5fd69dd6cc1fca670ff1f04f51",
    );
    expectTypeOf<HelpdeskPortalRequestPresentation>().not.toExtend<ServiceRequest>();
  });

  it.each([
    "SUBMITTED",
    "TRIAGED",
    "IN_PROGRESS",
    "RESOLVED",
    "CONFIRMED",
    "future_state",
  ])(
    "preserves authoritative %s, independent versions and explicit Backend facts",
    (state) => {
      const source = helpdeskWire({ state, assignmentVersion: 3, version: 19 });
      const before = structuredClone(source);
      const view = mapHelpdeskRequestPresentation(
        decodeHelpdeskRequest(source),
      );
      expect(view).toMatchObject({ ...source, kind: "it" });
      expect(view.currentAssignee).toEqual({
        id: { scope: "assignee", value: source.assigneeId },
      });
      expect(view.version).toBe(19);
      expect(view.assignmentVersion).toBe(3);
      for (const field of [
        "status",
        "priority",
        "location",
        "requester",
        "department",
        "createdAt",
        "updatedAt",
        "timeline",
        "history",
        "actor",
        "role",
      ]) {
        expect(view).not.toHaveProperty(field);
      }
      expect(source).toEqual(before);
    },
  );

  it.each([undefined, null, "Tên được phép hiển thị", ""])(
    "keeps optional/hidden display names exactly as supplied: %s",
    (name) => {
      const source = helpdeskWire(
        name === undefined
          ? {}
          : {
              requesterDisplayName: name,
              assigneeDisplayName: name,
            },
      );
      const decoded = decodeHelpdeskRequest(source);
      const view = mapHelpdeskRequestPresentation(decoded);
      if (name === undefined) {
        expect(decoded).not.toHaveProperty("requesterDisplayName");
        expect(view).not.toHaveProperty("requesterDisplayName");
        expect(view).not.toHaveProperty("assigneeDisplayName");
        expect(view.currentAssignee).not.toHaveProperty("displayName");
      } else {
        expect(view.requesterDisplayName).toBe(name);
        expect(view.assigneeDisplayName).toBe(name);
        expect(view.currentAssignee?.displayName).toBe(name);
      }
    },
  );

  it("does not manufacture an assignee from a label when assigneeId is null", () => {
    const view = mapHelpdeskRequestPresentation(
      decodeHelpdeskRequest(
        helpdeskWire({
          assigneeId: null,
          assigneeDisplayName: "Visible label",
        }),
      ),
    );
    expect(view.currentAssignee).toBeNull();
    expect(view.assigneeDisplayName).toBe("Visible label");
  });

  it.each([
    undefined,
    null,
    [],
    [
      { id: "worker-1", displayName: null },
      { id: "worker-2", displayName: "Tên Backend" },
    ],
  ])(
    "preserves eligibility absent/null/empty/populated without granting authority: %j",
    (eligibleAssignees) => {
      const source = helpdeskWire({
        allowedActions: [],
        ...(eligibleAssignees === undefined ? {} : { eligibleAssignees }),
      });
      const decoded = decodeHelpdeskRequest(source);
      const view = mapHelpdeskRequestPresentation(decoded);
      expect(view.allowedActions).toEqual([]);
      expect(view).not.toHaveProperty("canAssign");
      if (eligibleAssignees === undefined) {
        expect(decoded).not.toHaveProperty("eligibleAssignees");
        expect(view).not.toHaveProperty("eligibleAssignees");
      } else if (eligibleAssignees === null) {
        expect(view.eligibleAssignees).toBeNull();
      } else {
        expect(view.eligibleAssignees).toEqual({
          completeness: "unknown",
          items: eligibleAssignees.map((option) => ({
            id: { scope: "assignee", value: option.id },
            displayName: option.displayName,
          })),
        });
        expect(decoded.eligibleAssignees).not.toBe(eligibleAssignees);
      }
    },
  );

  it("preserves only Backend-supplied action strings, including start/assign and unknown names", () => {
    const source = helpdeskWire({
      allowedActions: ["assign", "start", "future_action"],
    });
    const view = mapHelpdeskRequestPresentation(decodeHelpdeskRequest(source));
    expect(view.allowedActions).toEqual(source.allowedActions);
    expect(view.allowedActions).not.toBe(source.allowedActions);
    expect(view.allowedActions).not.toContain("start_work");
  });

  it.each([helpdeskCursor, null])(
    "preserves list items and nextCursor %s",
    (nextCursor) => {
      const view = mapHelpdeskListPresentation(
        decodeHelpdeskPage({ items: [helpdeskWire()], nextCursor }),
      );
      expect(view.items[0]).toEqual(
        mapHelpdeskRequestPresentation(helpdeskWire()),
      );
      expect(view.nextCursor).toBe(nextCursor);
      expect(view).not.toHaveProperty("total");
      expect(
        mapHelpdeskListPresentation(
          decodeHelpdeskPage({ items: [], nextCursor: null }),
        ),
      ).toEqual({ items: [], nextCursor: null });
    },
  );

  it.each(["2026-10-26T01:02:03.123456789Z", 1792976523.125])(
    "maps only HistoryView fields preserving time %s and order",
    (occurredAt) => {
      const source = [
        {
          transitionId: "triage",
          sourceState: "SUBMITTED",
          targetState: "TRIAGED",
          version: 1,
          occurredAt,
          actor: "hidden",
          note: "hidden",
          id: "not-in-dto",
        },
      ];
      const history = mapHelpdeskHistoryPresentation(
        decodeHelpdeskHistory(source),
      );
      expect(history).toEqual([
        {
          transitionId: "triage",
          sourceState: "SUBMITTED",
          targetState: "TRIAGED",
          version: 1,
          occurredAt,
        },
      ]);
      expect(Object.keys(history[0])).toEqual([
        "transitionId",
        "sourceState",
        "targetState",
        "version",
        "occurredAt",
      ]);
      expect(history[0]).not.toHaveProperty("title");
      expect(mapHelpdeskHistoryPresentation(decodeHelpdeskHistory([]))).toEqual(
        [],
      );
    },
  );
});
