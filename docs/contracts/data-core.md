# Data Core: Object Storage contract

`com.tricore.dxos.core.data` owns the provider-neutral Object Storage API.
This is a pure Java 21 contract in the Spring Boot modular monolith, with no
Spring, JPA, JDBC, PostgreSQL, AWS or MinIO dependency. Provider adapters are
future work; this change does not register a runtime storage implementation.

## API and values

| Type | Responsibility |
| --- | --- |
| `ObjectStoragePort` | Synchronous `store`, `read`, `metadata` and `delete` operations |
| `ObjectKey` | Non-null, nonblank, opaque, case-sensitive string |
| `ObjectMetadata` | Key, content type, exact nonnegative byte length and immutable custom metadata |
| `StoreObjectCommand` | Required metadata, caller-owned input stream and explicit write mode |
| `StoreMode` | `CREATE_ONLY` or `OVERWRITE`, with no default |
| `ObjectContent` | `AutoCloseable` read resource containing metadata and one input stream |
| `StorageErrorCode` / `StorageException` | Normalized Data capability failures |

A key identifies an object within the storage namespace configured outside
Core. It is preserved verbatim: no trimming, path normalization, URI parsing,
bucket name, SDK type or provider naming rules. Consumers should use logical
identifiers rather than credentials or signed URLs. Namespace/provider mapping
belongs to the adapter, which must preserve key identity without collisions.

Content type is a required nonblank media-type string such as `text/plain` or
`application/octet-stream`; Core does not infer it from a filename or sniff
content. The caller supplies an appropriate media type. Length is an exact byte
count, not a character count; zero is valid, negative/unknown length is not.
Custom metadata is a defensively copied `Map<String, String>` with nonblank
names and non-null values (empty values are allowed). An empty map is valid.
No provider metadata or database entity appears in the contract.

## Operation semantics

- `store(command)` consumes input from its current position through EOF,
  synchronously, and returns the stored metadata. Declared length must match
  consumed bytes; mismatches and caller-input read failures are `INVALID_INPUT`.
  Adapters must not retain the stream for asynchronous work after the call.
- `CREATE_ONLY` creates only when absent. An existing key yields `CONFLICT`
  without changing its content or metadata. Absence must be enforced against
  concurrent writers; a lookup followed by an unconditional write is insufficient.
- `OVERWRITE` creates if absent or replaces content and the entire metadata map
  if present. It has no expected-version condition or metadata merge behavior.
  Concurrent overwrites have no ordering guarantee; the contract supplies no CAS.
- `read(key)` returns metadata and content describing the same object version.
  The resource represents one read, not a shared snapshot with other port calls.
- `metadata(key)` retrieves metadata without opening a content resource.
- `delete(key)` removes content and metadata. Missing keys yield `NOT_FOUND`;
  repeated delete also yields `NOT_FOUND`, rather than silently succeeding.

Other operational failures can leave an uncertain provider outcome. This API
does not promise rollback, exactly-once delivery, retry safety, or atomicity
across multiple object calls. A future adapter must document its consistency
and write-publication guarantees; the in-memory fake does not prove them.

## Stream ownership

The caller creates and closes the store input stream on both success and
failure. Neither the port nor its adapter closes it or takes ownership. A
failed attempt may consume input; retry requires a fresh or explicitly reset
stream. Core does not buffer complete objects into `byte[]`.

The caller owns the returned read resource and closes it, even after partial
consumption or failure:

```java
try (var object = storage.read(key)) {
    object.content().transferTo(destination);
}
```

`ObjectContent.close()` releases the stream and all read resources and is
idempotent. Closing the stream directly also releases its underlying resources.
The stream is single-use, unusable after close (`INVALID_INPUT`), and not
promised to support mark/reset. Metadata remains available after close.
The caller separately owns `destination` in the example. Adapters must wrap
provider streams so errors during lazy reading and close become sanitized
`StorageException`, without raw provider `IOException`, message or cause.

## Normalized errors and adapter mapping

| Condition | Code |
| --- | --- |
| Null/blank key, invalid metadata/command/length, invalid resource use or unreadable caller input | `INVALID_INPUT` |
| Object missing for read, metadata or delete | `NOT_FOUND` |
| Conditional operation failed, including existing create-only key | `CONFLICT` |
| Provider/network temporarily unavailable | `UNAVAILABLE` |
| Permission/access failure | `ACCESS_FAILURE` |

Constructors and port calls use `StorageException` for invalid inputs, so
validation is normalized before provider interaction. This intentionally uses
`INVALID_INPUT` for Data rather than Workflow's structural argument exceptions.
The exception follows the existing Core exception shape: `code()`, enum name
as message, no cause, no suppression and no stack trace. Transport mappings are
outside Core. Credentials, tokens, connection strings, SQL, provider messages,
stack traces and provider exceptions must not cross the adapter boundary.
Future adapters must classify all provider failures using these codes, including
stream read/close failures; unknown provider failures must be sanitized and
classified by the adapter rather than rethrown verbatim.

## Verification scope

`ObjectStoragePortTest` uses `InMemoryObjectStorage` in test source only.
It checks store/read/metadata/delete, missing objects, invalid inputs,
create-only conflicts (including concurrent writers), overwrite, length checks,
immutable metadata, resource lifecycle and error sanitization without services.
Small fixtures are buffered only by the fake. These tests illustrate the port
contract; future adapters must also prove provider behavior with their own tests.

See [persistence boundaries](../architecture/data-persistence-boundaries.md)
for ownership, transaction limits and migration constraints.
