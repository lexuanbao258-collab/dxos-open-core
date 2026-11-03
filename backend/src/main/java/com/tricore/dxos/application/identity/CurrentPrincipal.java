package com.tricore.dxos.application.identity;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;

/**
 * Provides the identity of the currently verified caller.
 *
 * <p>Implementations must obtain the principal from a trusted,
 * verified authentication boundary. A caller-supplied or manually
 * constructed principal is not proof of authentication.</p>
 */
public interface CurrentPrincipal {

    AuthenticatedPrincipal requireVerified();
}