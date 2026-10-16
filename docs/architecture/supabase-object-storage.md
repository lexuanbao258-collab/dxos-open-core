# Supabase Object Storage adapter — Issue 26

## Protocol decision

REST-only; Keycloak remains the application's identity provider. No Supabase Auth user session, AWS SDK or S3 credential is introduced. Backend credentials do not authorize application users; application/Gateway performs that check before calling ObjectStoragePort.

The current [info API](https://supabase.com/docs/reference/javascript/storage-from-info) and [download API](https://supabase.com/docs/reference/javascript/storage-from-download) document versionId. Inspected provider routes pass this ID to the object lookup and fetch content using the resolved internal version:

- [getObjectInfo.ts](https://github.com/supabase/storage/blob/master/src/http/routes/object/getObjectInfo.ts)
- [getObject.ts](https://github.com/supabase/storage/blob/master/src/http/routes/object/getObject.ts)
- [object lookup](https://github.com/supabase/storage/blob/master/src/storage/object.ts)
- [database lookup](https://github.com/supabase/storage/blob/master/src/storage/database/pg.ts): an explicit version adds an equality predicate; a missing exact row raises NoSuchKey, with no fallback to current.

These references are upstream master inspected on 2026-10-14, not proof of the version deployed in the team project.

read(key) resolves current version through info, requests info with that exact versionId, verifies the response version/name/bucket, then downloads with that same versionId. An available ETag adds an If-Match check; it is not the version identity. Returned ObjectContent wraps only that response. No current-key retry/fallback occurs if a version disappears. A vanished version before opening the resource can produce NOT_FOUND; once open, provider truncation/failure produces UNAVAILABLE. Real-provider version fidelity and read completion after overwrite/delete remain acceptance gates. If the deployed provider ignores versionId or cannot meet the snapshot contract, reassess isolated S3 reads rather than silently serving current content.

metadata(key) uses REST info without a content resource. It does not promise a snapshot shared with a separate read operation.

## Write and delete

[Standard uploads](https://supabase.com/docs/guides/storage/uploads/standard-uploads) document same-path concurrent behavior with upsert=false. CREATE_ONLY uses POST object with x-upsert=false; OVERWRITE uses x-upsert=true. No HEAD/lookup precedes either write. The provider enforces absence; local locks are not used. Every write carries the complete metadata envelope, including an empty custom map. There is no metadata merge or separate metadata mutation.

Delete uses single-object REST DELETE. Missing/repeated deletion must return NOT_FOUND; hosted behavior, including delete markers/versioning, must pass the real gate. S3 idempotent deletion is not used. Do not enable bucket versioning that turns repeated deletion into a successful marker write without revalidating this contract.

No application write/delete retries. A lost acknowledgement may follow publication/deletion: UNAVAILABLE does not prove rollback. PostgreSQL transactions do not undo storage mutations.

## Key identity and metadata format

Logical keys remain verbatim Java Strings. Physical names are prefix/v1/Base64URL(no padding, exact UTF-16 code units). Encoding code units manually preserves isolated surrogates without replacement collisions. No trimming, normalization, case-folding or URI decoding occurs. Raw traversal/slashes/backslashes never become provider path syntax. Physical key length is checked against configured limits. Mapping/prefix changes require migration; prefix is not authorization.

Custom metadata travels in one JSON field dxos-envelope-v1, containing format version 1, contentType and a custom array of name/value pairs. Strings inside the envelope are Base64 of their exact UTF-16 code units, preserving Unicode/control characters/isolated surrogates. The complete outer JSON is Base64 in REST x-metadata. Names are not HTTP header names, so Source and source remain distinct. Original content type is returned exactly through the envelope; its transport representation must be a safe concrete media type. No inferred filename MIME type. Unknown/malformed envelopes fail UNAVAILABLE; oversized input fails INVALID_INPUT before mutation.

## Streams and limits

Upload preflight consumes caller input from current position to EOF into an adapter-owned temporary file with an 8 KiB implementation buffer. Exact count must match declared length, including zero. IO/runtime caller-read failure is INVALID_INPUT. Caller input is never closed or retained. An input exceeding the configured maximum is drained to EOF without writing further bytes, then rejected. Local file failures are UNAVAILABLE. File deletion is attempted on every path; filesystem cleanup failure cannot be guaranteed away. After a process crash, an operator may remove stale dxos-upload- files from the dedicated staging directory only after confirming no live upload owns them.

HTTP file publishers stream staging files; object bytes are not retained in heap. Resource release and temporary-file cleanup occur before store returns. Cleanup failure after a confirmed upload reports UNAVAILABLE, so callers must reconcile and not blind retry.

Read resources are single-use. Stream close and ObjectContent close release the provider response, including after partial consumption; close is idempotent even when the first close fails. Metadata remains accessible. Closed content access is INVALID_INPUT. Lazy read/available/close faults and premature/excess EOF lengths are sanitized. Destination IO errors from transferTo belong to the caller and are not provider faults.

All product limits are required configuration: max-object-bytes, max-metadata-bytes (encoded x-metadata size), max-key-bytes (physical ASCII name), max-response-bytes. Operators must set values compatible with project/bucket/proxy limits; the adapter cannot discover every upstream limit. Staging disk quota and upload concurrency require deployment-level controls. HTTP request/connect timeouts are configured; the JDK input-stream body has no independent idle-read timeout, so callers must close/cancel stalled resources. No redirect following.

## Errors and credentials

[Supabase structured errors](https://supabase.com/docs/guides/storage/debugging/error-codes) map to the existing five Core codes. Known duplicate on CREATE_ONLY -> CONFLICT; known missing object -> NOT_FOUND; authentication/signature/permission -> ACCESS_FAILURE; known invalid caller request/413 -> INVALID_INPUT; transient/unknown/malformed errors, missing configured bucket and local staging IO -> UNAVAILABLE. Lock timeout is UNAVAILABLE, not a confirmed duplicate. Failed precondition is CONFLICT. Raw HTTP status alone does not establish missing-object or duplicate semantics.

Every exposed StorageException has only the stable enum message, no cause, suppression or stack trace. Provider messages/bodies/endpoints/credentials are never attached or logged. Secret-key mode sends apikey only; service-role mode sends apikey and legacy Bearer JWT. Never send a non-JWT secret API key as a user JWT. Bảo provisions/approves credentials privately.

Configuration is enabled only by provider=supabase, requires HTTPS /storage/v1 base URL without embedded credentials/query/fragment, safe bucket/prefix, all limits, absolute staging path and positive timeouts. Disabled configuration registers no storage fallback. Enabled missing config fails closed. No bucket is created or made public by this adapter.
