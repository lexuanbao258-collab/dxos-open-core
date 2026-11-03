import type {
  ActionId,
  CreateRequestInput,
  DemoRole,
  RequestAction,
  ServiceRequest,
} from "../domain/request";

export interface RequestService {
  list(role: DemoRole): Promise<ServiceRequest[]>;
  get(id: string, role: DemoRole): Promise<ServiceRequest>;
  actions(request: ServiceRequest, role: DemoRole): RequestAction[];
  create(input: CreateRequestInput, role: DemoRole): Promise<ServiceRequest>;
  transition(
    id: string,
    action: ActionId,
    note: string,
    expectedVersion: number,
    role: DemoRole,
  ): Promise<ServiceRequest>;
}
export class PortalError extends Error {
  constructor(
    public code: string,
    message: string,
  ) {
    super(message);
    this.name = "PortalError";
  }
}
export const safeError = (error: unknown) =>
  error instanceof PortalError
    ? error.message
    : "Không thể tải dữ liệu. Vui lòng thử tải lại.";
