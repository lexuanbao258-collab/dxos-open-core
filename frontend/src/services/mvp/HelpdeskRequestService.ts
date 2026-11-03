import { HelpdeskApiClient } from "./http/HelpdeskApiClient";
import type {
  HelpdeskAssignInput,
  HelpdeskCreateInput,
  HelpdeskListInput,
  HelpdeskResolveInput,
  HelpdeskVersionInput,
} from "./http/types";
import {
  mapHelpdeskHistoryPresentation,
  mapHelpdeskListPresentation,
  mapHelpdeskRequestPresentation,
} from "./helpdeskPresentation";

/**
 * Application service for the reviewed DTO, using an explicitly injected client.
 * Unbound: no transport/default instance, persona, token or Portal composition
 * changes. In particular this does not masquerade as legacy RequestService.
 */
export class HelpdeskRequestService {
  constructor(private readonly client: HelpdeskApiClient) {}

  async create(input: HelpdeskCreateInput) {
    return mapHelpdeskRequestPresentation(await this.client.create(input));
  }

  async list(input: HelpdeskListInput = {}) {
    return mapHelpdeskListPresentation(await this.client.list(input));
  }

  async detail(id: string) {
    return mapHelpdeskRequestPresentation(await this.client.detail(id));
  }

  async triage(id: string, input: HelpdeskVersionInput) {
    return mapHelpdeskRequestPresentation(await this.client.triage(id, input));
  }

  async assign(id: string, input: HelpdeskAssignInput) {
    return mapHelpdeskRequestPresentation(await this.client.assign(id, input));
  }

  async start(id: string, input: HelpdeskVersionInput) {
    return mapHelpdeskRequestPresentation(await this.client.start(id, input));
  }

  async resolve(id: string, input: HelpdeskResolveInput) {
    return mapHelpdeskRequestPresentation(await this.client.resolve(id, input));
  }

  async confirm(id: string, input: HelpdeskVersionInput) {
    return mapHelpdeskRequestPresentation(await this.client.confirm(id, input));
  }

  async history(id: string) {
    return mapHelpdeskHistoryPresentation(await this.client.history(id));
  }
}

/** Structural seam for APP-11 fake service/component integration tests. */
export type HelpdeskAssignmentService = Pick<
  HelpdeskRequestService,
  "detail" | "assign"
>;
