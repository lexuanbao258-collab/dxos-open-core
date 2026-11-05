// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes } from "react-router-dom";
import { PortalProvider, usePortal } from "../../context";
import { HelpdeskRequestService } from "../../services/mvp/HelpdeskRequestService";
import { HelpdeskApiClient } from "../../services/mvp/http/HelpdeskApiClient";
import {
  bootstrapHelpdeskSession,
  sameOriginHelpdeskTransport,
} from "../../services/mvp/http/sameOriginTransport";
import { HelpdeskPortal } from "./HelpdeskPortal";

function Login() {
  return (
    <p role="status">
      Phiên chưa được xác thực hoặc đã hết hạn.{" "}
      <a href="/oauth2/authorization/keycloak">Đăng nhập Keycloak</a>
    </p>
  );
}

function SessionPortal() {
  const { sessionExpired } = usePortal();
  if (sessionExpired) return <Login />;
  return (
    <Routes>
      <Route path="/mvp/helpdesk/*" element={<HelpdeskPortal />} />
      <Route path="*" element={<Navigate to="/mvp/helpdesk" replace />} />
    </Routes>
  );
}

/** Separate local composition: no demo persona or mock service grants access. */
export default function LocalHelpdeskIntegration() {
  const [session, setSession] = useState<
    "loading" | "anonymous" | "expired" | "failed" | HelpdeskRequestService
  >("loading");
  useEffect(() => {
    let active = true;
    bootstrapHelpdeskSession()
      .then((result) => {
        if (!active) return;
        setSession(
          result
            ? new HelpdeskRequestService(
                new HelpdeskApiClient(
                  sameOriginHelpdeskTransport(result.csrfToken),
                  () => {
                    if (active) setSession("expired");
                  },
                ),
              )
            : "anonymous",
        );
      })
      .catch(() => {
        if (active) setSession("failed");
      });
    return () => {
      active = false;
    };
  }, []);
  return (
    <main id="main-content" className="page">
      <header>
        <h1>DX-OS · Helpdesk integration local</h1>
        <Link to="/mvp/helpdesk">Danh sách Helpdesk</Link>
      </header>
      {session === "loading" ? (
        <p role="status">Đang kiểm tra phiên…</p>
      ) : session === "failed" ? (
        <p role="alert">
          Không thể kiểm tra phiên BFF. Kiểm tra HTTPS và kết nối, sau đó tải
          lại trang.
        </p>
      ) : typeof session === "string" ? (
        <Login />
      ) : (
        <PortalProvider helpdeskService={session} helpdeskSession>
          <SessionPortal />
        </PortalProvider>
      )}
    </main>
  );
}
