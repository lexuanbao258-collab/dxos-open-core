import { makeFixtures } from "../fixtures/requests";
import {
  validateRequest,
  type ActionId,
  type CreateRequestInput,
  type DemoRole,
  type Fault,
  type RequestAction,
  type ServiceRequest,
} from "../domain/request";
import { PortalError, type RequestService } from "./RequestService";

/** Application demo responses only, not a WorkflowRuntime or authorization implementation. In-memory; reload resets data. */
export class MockRequestService implements RequestService {
  private items = makeFixtures();
  private nextFault: Fault = "";
  constructor(private delay = 350) {}
  setFault(fault: Fault) {
    this.nextFault = fault;
  }
  reset() {
    this.items = makeFixtures();
    this.nextFault = "";
  }
  private async respond(write = false) {
    const fault = this.nextFault;
    if (fault !== "unknown" || write) this.nextFault = "";
    await new Promise((resolve) => setTimeout(resolve, this.delay));
    if (fault === "unknown" && write)
      throw new PortalError(
        "COMMIT_OUTCOME_UNKNOWN",
        "Chưa xác định kết quả thao tác. Không gửi lại; kiểm tra lịch sử trước khi tiếp tục.",
      );
    const failures: Record<string, [string, string]> = {
      network: [
        "NETWORK",
        "Kết nối mô phỏng bị gián đoạn. Bạn có thể chủ động tải lại.",
      ],
      "401": [
        "401",
        "Phiên demo đã hết hạn. Vui lòng vào Trạng thái phiên để đăng nhập lại.",
      ],
      "403": [
        "403",
        "Bạn không có quyền thực hiện thao tác này trong kịch bản demo.",
      ],
      "404": ["404", "Không tìm thấy yêu cầu hoặc yêu cầu không khả dụng."],
    };
    if (failures[fault]) throw new PortalError(...failures[fault]);
  }
  private visible(item: ServiceRequest, role: DemoRole) {
    return role === "processor" || item.requester === "Ngọc Ánh";
  }
  private find(id: string, role: DemoRole) {
    const item = this.items.find((r) => r.id === id);
    if (!item || !this.visible(item, role))
      throw new PortalError(
        "404",
        "Không tìm thấy yêu cầu hoặc yêu cầu không khả dụng.",
      );
    return item;
  }
  async list(role: DemoRole) {
    await this.respond();
    return structuredClone(this.items.filter((r) => this.visible(r, role)));
  }
  async get(id: string, role: DemoRole) {
    await this.respond();
    return structuredClone(this.find(id, role));
  }
  actions(item: ServiceRequest, role: DemoRole): RequestAction[] {
    if (
      item.kind === "it" &&
      item.status === "resolved" &&
      role === "employee" &&
      this.visible(item, role)
    )
      return [{ id: "confirm", label: "Xác nhận hoàn thành" }];
    if (role !== "processor") return [];
    // Fixed mock response examples for both sample services; Core owns real allowed transitions.
    if (item.status === "pending")
      return [
        { id: "approve", label: "Phê duyệt" },
        { id: "reject", label: "Từ chối", tone: "danger" },
      ];
    if (item.status === "approved")
      return [{ id: "start", label: "Bắt đầu xử lý" }];
    if (item.status === "processing")
      return [{ id: "complete", label: "Hoàn thành" }];
    return [];
  }
  async create(input: CreateRequestInput, role: DemoRole) {
    await this.respond(true);
    if (Object.keys(validateRequest(input)).length)
      throw new PortalError("VALIDATION", "Kiểm tra lại các trường thông tin.");
    const now = new Date().toISOString();
    const item: ServiceRequest = {
      ...input,
      title: input.title.trim(),
      description: input.description.trim(),
      location: input.location.trim(),
      id: `DX-${1049 + this.items.length - 10}`,
      status: "pending",
      requester: role === "employee" ? "Ngọc Ánh" : "Lê Xuân Bảo",
      department: role === "employee" ? "Phòng phát triển" : "Phòng điều phối",
      assignee: "Chưa phân công",
      createdAt: now,
      updatedAt: now,
      version: 0,
      timeline: [
        {
          id: crypto.randomUUID(),
          at: now,
          actor: role === "employee" ? "Ngọc Ánh" : "Lê Xuân Bảo",
          title: "Đã gửi yêu cầu",
          note: "Được tạo từ cổng dịch vụ · MOCK DATA.",
        },
      ],
    };
    this.items.unshift(item);
    return structuredClone(item);
  }
  async transition(
    id: string,
    action: ActionId,
    note: string,
    expectedVersion: number,
    role: DemoRole,
  ) {
    await this.respond(true);
    const item = this.find(id, role);
    if (item.version !== expectedVersion)
      throw new PortalError(
        "CONFLICT",
        "Yêu cầu đã thay đổi. Tải lại chi tiết trước khi thao tác.",
      );
    if (!this.actions(item, role).some((a) => a.id === action))
      throw new PortalError(
        "403",
        "Thao tác không được cho phép trong kịch bản demo.",
      );
    if (note.trim().length < 5 || note.trim().length > 1000)
      throw new PortalError("VALIDATION", "Ghi chú cần từ 5 đến 1.000 ký tự.");
    const responses = {
      approve: { status: "approved", title: "Đã phê duyệt" },
      reject: { status: "rejected", title: "Từ chối yêu cầu" },
      start: { status: "processing", title: "Bắt đầu xử lý" },
      complete:
        item.kind === "it"
          ? { status: "resolved", title: "IT đã xử lý và thông báo kết quả" }
          : { status: "completed", title: "Hoàn thành yêu cầu" },
      confirm: {
        status: "confirmed",
        title: "Người gửi đã xác nhận hoàn thành",
      },
    } satisfies Record<ActionId, { status: ServiceRequest["status"]; title: string }>;
    const result = responses[action];
    item.status = result.status;
    item.version += 1;
    item.updatedAt = new Date().toISOString();
    item.assignee = item.kind === "it" ? "Nhóm IT Support" : "Nhóm Facility";
    item.timeline.push({
      id: crypto.randomUUID(),
      at: item.updatedAt,
      actor: role === "employee" ? item.requester : "Lê Xuân Bảo",
      title: result.title,
      note: note.trim(),
    });
    return structuredClone(item);
  }
}
export const requestService = new MockRequestService();
