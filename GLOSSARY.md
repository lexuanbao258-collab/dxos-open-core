# Workflow access

Language for deciding who may bind a resource to a workflow or access an existing workflow instance.

## Language

**Verified caller**:
A caller whose identity has been accepted by the trusted authentication boundary. Self-declared identity is not a verified caller.
_Avoid_: Claimed user, authenticated flag

**Operation authority**:
A caller's permission to request a category of Workflow operations. It does not establish access to an individual resource.
_Avoid_: Resource permission

**Resource binding**:
The identity of the resource associated with a Workflow instance. A binding does not establish the caller's tenant membership or permission.
_Avoid_: Resource ownership

**Visible resource**:
A resource whose existence the caller is allowed to know. Visibility does not itself grant permission to bind or act on that resource.

**Hidden resource**:
A resource whose existence must not be disclosed to the caller. Its denial is indistinguishable from that of a nonexistent resource.

**Unavailable policy**:
An access policy that cannot establish a trustworthy decision. Uncertainty never grants access.
