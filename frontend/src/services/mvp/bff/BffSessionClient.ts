import { bffRequestOptions, readBffJson, type BffFetch } from "./http";

export type BffSessionState = Readonly<{ authenticated: boolean }>;

export class BffSessionError extends Error {
  constructor(
    readonly code:
      | "SESSION_REQUIRED"
      | "STALE_SESSION"
      | "INVALID_SESSION_RESPONSE"
      | "SESSION_NETWORK_FAILURE",
  ) {
    super(code);
    this.name = "BffSessionError";
  }
}

/** A transport belongs to one verified session generation, never its successor. */
export interface BffSessionScope {
  assertActive(): void;
  getCsrfToken(): string;
  invalidate(): void;
}

function decodeSession(body: unknown): string | null {
  if (body === null || typeof body !== "object" || Array.isArray(body))
    throw new BffSessionError("INVALID_SESSION_RESPONSE");
  const source = body as Record<string, unknown>;
  const keys = Object.keys(source);
  if (source.authenticated === false && keys.length === 1) return null;
  if (
    source.authenticated === true &&
    keys.length === 2 &&
    typeof source.csrfToken === "string" &&
    source.csrfToken.length > 0 &&
    !/[^\x21-\x7e]/.test(source.csrfToken)
  )
    return source.csrfToken;
  throw new BffSessionError("INVALID_SESSION_RESPONSE");
}

/**
 * Unbound Phase 2A client. Only an explicit load() probes /bff/session.
 * CSRF lives in this instance; no browser storage, roles or OAuth tokens.
 * Each load/invalidation retires all previous scopes, even if the cookie stayed
 * the same. Build a new transport/client after a successful explicit load.
 */
export class BffSessionClient {
  private generation = 0;
  private csrfToken: string | null = null;

  constructor(private readonly fetchBff: BffFetch) {}

  getState(): BffSessionState {
    return Object.freeze({ authenticated: this.csrfToken !== null });
  }

  invalidate(): void {
    this.generation++;
    this.csrfToken = null;
  }

  async load(): Promise<BffSessionState> {
    this.invalidate();
    const generation = this.generation;
    let response: Response;
    try {
      response = await this.fetchBff("/bff/session", {
        ...bffRequestOptions("GET"),
        headers: { Accept: "application/json" },
      });
    } catch {
      if (generation !== this.generation)
        throw new BffSessionError("STALE_SESSION");
      throw new BffSessionError("SESSION_NETWORK_FAILURE");
    }
    const body = await readBffJson(response);
    if (generation !== this.generation)
      throw new BffSessionError("STALE_SESSION");
    if (response.status !== 200)
      throw new BffSessionError("INVALID_SESSION_RESPONSE");
    this.csrfToken = decodeSession(body);
    return this.getState();
  }

  capture(): BffSessionScope {
    const generation = this.generation;
    const assertActive = () => {
      if (generation !== this.generation)
        throw new BffSessionError("STALE_SESSION");
      if (this.csrfToken === null)
        throw new BffSessionError("SESSION_REQUIRED");
    };
    assertActive();
    return Object.freeze({
      assertActive,
      getCsrfToken: () => {
        assertActive();
        return this.csrfToken!;
      },
      invalidate: () => {
        if (generation === this.generation) this.invalidate();
      },
    });
  }
}
