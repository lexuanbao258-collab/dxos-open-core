/** Contract data only; no navigation, logout request or UI wiring is shipped. */
export interface BffBrowserAuthContract {
  readonly login: {
    readonly method: "GET";
    readonly path: "/oauth2/authorization/keycloak";
    readonly navigation: "top-level";
  };
  readonly logout: {
    readonly method: "POST";
    readonly path: "/bff/logout";
    readonly csrfHeader: "X-CSRF-TOKEN";
    readonly browserOidcRedirect: "PENDING YEN CONFIRMATION";
  };
  readonly csrf403: "PENDING YEN CONFIRMATION";
  readonly sessionExpiry: "PENDING YEN CONFIRMATION";
}

export const BFF_BROWSER_AUTH_CONTRACT: BffBrowserAuthContract = Object.freeze({
  login: Object.freeze({
    method: "GET",
    path: "/oauth2/authorization/keycloak",
    navigation: "top-level",
  }),
  logout: Object.freeze({
    method: "POST",
    path: "/bff/logout",
    csrfHeader: "X-CSRF-TOKEN",
    browserOidcRedirect: "PENDING YEN CONFIRMATION",
  }),
  csrf403: "PENDING YEN CONFIRMATION",
  sessionExpiry: "PENDING YEN CONFIRMATION",
});
