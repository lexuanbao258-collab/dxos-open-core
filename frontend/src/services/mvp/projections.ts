import type { AuthorizedHistory } from "./types";

/**
 * Copy an already-authorized normalized history. This does not authorize raw
 * audit events, decode wire DTOs, sort events or infer missing actor/note data.
 * Completeness is preserved; absence of an event is not a recovery guarantee.
 */
export function projectAuthorizedHistory(
  history: AuthorizedHistory,
): AuthorizedHistory {
  return {
    completeness: history.completeness,
    events: history.events.map((event) => ({
      id: event.id,
      at: event.at,
      title: event.title,
      ...(event.actor === undefined ? {} : { actor: event.actor }),
      ...(event.note === undefined ? {} : { note: event.note }),
    })),
  };
}
