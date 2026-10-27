import { helpdeskId, helpdeskWire } from "./helpdeskFixtures";
import type {
  HelpdeskHistoryEntry,
  HelpdeskRequest,
} from "../services/mvp/http/types";
import type {
  HelpdeskHttpRequest,
  HelpdeskHttpTransport,
} from "../services/mvp/http/transport";

/** Test-only Backend simulation. Never imported by main.tsx or default App.
 * Capabilities are fixture responses, not Portal authorization rules.
 */
export function fakeHelpdeskTransport(seed?: HelpdeskRequest) {
  let current = seed;
  const calls: HelpdeskHttpRequest[] = [];
  const history: HelpdeskHistoryEntry[] = [];
  const capabilities: Record<string, string[]> = {
    SUBMITTED: ["triage"],
    TRIAGED: ["assign", "start"],
    IN_PROGRESS: ["resolve"],
    RESOLVED: ["confirm"],
    CONFIRMED: [],
  };
  function response(detail = false): HelpdeskRequest {
    const value = {
      ...current!,
      allowedActions:
        current!.state === "TRIAGED" && current!.assigneeId === null
          ? ["assign"]
          : (capabilities[current!.state] ?? current!.allowedActions),
    };
    const {
      requesterDisplayName: _requester,
      assigneeDisplayName: _assignee,
      eligibleAssignees: _eligible,
      ...bare
    } = value;
    return detail
      ? {
          ...bare,
          requesterDisplayName: "Người gửi kiểm thử",
          ...(bare.assigneeId
            ? { assigneeDisplayName: "Người xử lý kiểm thử" }
            : {}),
          ...(bare.allowedActions.includes("assign")
            ? {
                eligibleAssignees: [
                  { id: "fake-worker", displayName: "Người xử lý kiểm thử" },
                ],
              }
            : {}),
        }
      : bare;
  }
  const transport: HelpdeskHttpTransport = async (request) => {
    calls.push(structuredClone(request));
    const url = new URL(request.path, "http://fake.invalid");
    const base = "/api/v1/mvp/requests";
    if (url.pathname === base && request.method === "POST") {
      current = helpdeskWire({
        id: helpdeskId,
        title: request.body!.title as string,
        description: request.body!.description as string,
        state: "SUBMITTED",
        version: 1,
        assignmentVersion: 0,
        assigneeId: null,
      });
      return { status: 201, body: response() };
    }
    if (url.pathname === base && request.method === "GET")
      return {
        status: 200,
        body: {
          items: current && !url.searchParams.has("after") ? [response()] : [],
          nextCursor: null,
        },
      };
    if (!current || !url.pathname.startsWith(`${base}/${current.id}`))
      return { status: 404 };
    const command = url.pathname.slice(`${base}/${current.id}`.length);
    if (request.method === "GET") {
      if (command === "") return { status: 200, body: response(true) };
      if (command === "/history")
        return { status: 200, body: structuredClone(history) };
      return { status: 404 };
    }
    const action = command.slice(1);
    if (!response().allowedActions.includes(action)) return { status: 403 };
    if (action === "assign") {
      if (request.body?.expectedAssignmentVersion !== current.assignmentVersion)
        return { status: 409 };
      current = {
        ...current,
        assigneeId: request.body!.assigneeId as string,
        assignmentVersion: current.assignmentVersion + 1,
      };
      return { status: 200, body: response() };
    }
    if (request.body?.expectedVersion !== current.version)
      return { status: 409 };
    const target: Record<string, string> = {
      triage: "TRIAGED",
      start: "IN_PROGRESS",
      resolve: "RESOLVED",
      confirm: "CONFIRMED",
    };
    if (!target[action]) return { status: 404 };
    if (action === "start" && current.assigneeId === null)
      return { status: 403 };
    const sourceState = current.state;
    current = {
      ...current,
      state: target[action],
      version: current.version + 1,
      ...(action === "resolve"
        ? { resolution: request.body!.resolution as string }
        : {}),
    };
    history.push({
      transitionId: action === "start" ? "start_work" : action,
      sourceState,
      targetState: current.state,
      version: current.version,
      occurredAt: "2026-10-26T10:30:00Z",
    });
    return { status: 200, body: response() };
  };
  return { transport, calls, history };
}
