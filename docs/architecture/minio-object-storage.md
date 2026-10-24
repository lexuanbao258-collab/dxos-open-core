# MinIO ObjectStoragePort adapter

Provider selection: dxos.object-storage.provider=minio. Implementation is isolated in infrastructure.storage.minio; Core Data, Workflow and PostgreSQL migrations are unchanged. Supabase REST implementation remains independent and unmodified.

## Protocol and publication

AWS SDK for Java v2 2.56.0 S3 with Apache HTTP 5 uses an explicit MinIO endpoint, region and static application credentials. Path-style addressing is enabled. AWS chunked encoding is disabled; a staged file provides an exact HTTP Content-Length. Optional SDK request/response checksums use WHEN_REQUIRED. No default AWS credentials or region lookup is used. Connect, pool-acquisition, socket-read and API call timeouts are bounded. Each operation has one SDK attempt; no application mutation retries.

CREATE_ONLY uses a single PutObject with If-None-Match: *. Absence is enforced by the server, across clients and JVMs. There is no HEAD-before-PUT or local storage lock. A confirmed PreconditionFailed/412 becomes CONFLICT. ConditionalRequestConflict/409 becomes UNAVAILABLE because it does not prove an existing object. Conditional header support on the selected MinIO build is a required real-server acceptance gate.

OVERWRITE uses unconditional single PUT, creating or replacing content, content type and the entire custom metadata envelope atomically. Concurrent writers publish whole objects; the last completed write wins. No compare-and-swap version contract is added.

Single PUT only: configuration rejects max-object-bytes above 5 GiB; default 64 MiB. Multipart is deliberately absent because a conditional final commit would require its own concurrency proof. Increasing limits beyond single PUT is not supported.

## Ownership and exact length

Store stages caller input from its current position to EOF in an 8 KiB buffer. Exact declared byte count is verified before publication. A too-long stream is drained to EOF without growing disk beyond max-object-bytes, then rejected. Short/long/zero-length mismatch and IO/runtime caller read failures map to INVALID_INPUT. Caller input is never closed or retained. Staging disk errors map to UNAVAILABLE. Staging cleanup is attempted on success and failure; cleanup failure after successful PUT reports UNAVAILABLE with a potentially published object.

The configured staging directory must be absolute and adapter-specific. On POSIX it is created with mode 0700, an existing directory with group/other permissions is rejected, symlink leaf directories are rejected, and temporary files use 0600. On Windows use a private directory protected by ACLs. Operator controls must bound concurrent uploads and disk capacity. Crashes may leave dxos-minio-upload- files; cleanup requires checking that no live upload owns them.

Read uses one GET response. Metadata is decoded from that response, never from a separate HEAD which could race with an overwrite. The returned stream checks premature/excess EOF, wraps lazy read/available/close failures, and aborts the underlying SDK response on close to release a partial read without draining the rest. Close is idempotent; closed stream access is INVALID_INPUT; metadata remains available. Destination IO errors during transferTo remain caller IO errors. The real MinIO gate must prove an open read completes with the same bytes across overwrite/delete.

Metadata uses HEAD without opening a content stream. S3 HEAD lacks an error body: a scoped ListObjectsV2 request distinguishes missing object from missing bucket. A 403 on HEAD/GET can hide absence when ListBucket is prefix-scoped; a successful exact-prefix list with no exact key establishes NOT_FOUND. A denied list stays ACCESS_FAILURE. No blanket mapping of 403/404 to missing is used.

Delete checks existence with HEAD, then uses S3 DELETE. Sequential missing and repeated delete are NOT_FOUND. HEAD/DELETE is not atomic: two simultaneous deletes can both succeed, or delete can remove a replacement published between HEAD and DELETE. Core does not require concurrent-delete compare-and-swap semantics. No such guarantee is claimed, and no JVM lock is presented as a distributed solution. New Compose buckets are unversioned; storage object retention/versioning is an operator decision outside this baseline.

## Key and metadata identity

Keys are opaque case-sensitive Java strings. Physical names are key-prefix/v1/Base64URL(no padding, exact UTF-16 code units). No trim, Unicode normalization, URL decoding or filesystem path conversion occurs. Traversal, slash, backslash and isolated surrogate characters remain distinct logical keys. Physical ASCII key size is checked against a maximum of 1024 bytes. Changing the prefix/codec requires a data migration; prefix is not application authorization.

Custom names/values and original content type live in one versioned JSON envelope, Base64 encoded in x-amz-meta-dxos-envelope-v1. Inner strings encode exact UTF-16 code units, preserving case-distinct names, Unicode, controls and isolated surrogates. Original content type is returned exactly, while the transport content type must be a safe concrete media type. Encoded envelope defaults to 1800 bytes; configured maximum cannot exceed 1900, leaving room within S3 user metadata limits. Missing/malformed envelope is UNAVAILABLE; oversized caller metadata is INVALID_INPUT before PUT.

## Error boundary and configuration

Stable Core errors only: INVALID_INPUT, NOT_FOUND, CONFLICT, UNAVAILABLE, ACCESS_FAILURE. Missing bucket is UNAVAILABLE. Authentication/signature/permission failures are ACCESS_FAILURE. Unknown service errors, network faults and timeouts are UNAVAILABLE. Raw provider messages, causes, bodies, credentials and endpoints are neither attached to StorageException nor logged by the adapter. Properties toString is redacted. Operational outcome can be uncertain after a network/cleanup failure; reconcile rather than blindly retry. PostgreSQL rollback does not undo object operations.

MinIO configuration only binds when provider=minio. Its single minioObjectStoragePort bean is mutually exclusive with Supabase's provider=supabase configuration. Disabled selection registers neither provider. Enabled missing/invalid configuration fails closed. No bucket creation or public policy mutation occurs in Java. HTTP requires explicit allow-insecure-http=true for local/isolated development; use HTTPS for remote production access.

## Linux infrastructure and acceptance

infra/minio builds server and mc from fixed upstream commits. Compose runs the server as UID 10001 with a persistent named volume and binds API/console to loopback. Root credentials exist only for server/bootstrap; backend uses a distinct identity with GetObject/PutObject/DeleteObject on the configured physical prefix and scoped ListBucket. Bootstrap refuses to adopt an existing identity carrying unrelated policies/groups, disables anonymous bucket access, and stores mc configuration only in a private tmpfs.

The Community server/client repositories are archived; source image creation and real-server tests have not been executed in this environment. Reproducibility requires retaining the resulting image digest; production promotion requires reviewing/updating base-image digests and scanning the built image. Single-node Compose demonstrates deployment and durability, not high availability or a backup strategy.

References: [SDK conditional PUT](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/s3/model/PutObjectRequest.Builder.html), [S3 conditional write behavior](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html), [HTTP 5 configuration](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-apache5.html), [MinIO source distribution](https://github.com/minio/minio).
