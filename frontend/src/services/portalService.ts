import { requestService as mockService } from "./MockRequestService";
import type { RequestService } from "./RequestService";

// The composition root binds an interface to mock mode. No env flag can enable LIVE.
export const requestService: RequestService = mockService;
export const demoControls = {
  setFault: mockService.setFault.bind(mockService),
  reset: mockService.reset.bind(mockService),
};
