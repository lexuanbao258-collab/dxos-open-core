// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { Navigate, Route, Routes } from "react-router-dom";
// Use the combined weight files: they carry per-subset `unicode-range`, so the
// Vietnamese subset (ă ơ ư đ ạ ắ ệ …) is really used. The per-subset
// `vietnamese-*.css` / `latin-*.css` files have no range and the last one wins.
import "@fontsource/be-vietnam-pro/400.css";
import "@fontsource/be-vietnam-pro/500.css";
import "@fontsource/be-vietnam-pro/600.css";
import "@fontsource/be-vietnam-pro/700.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import "@fontsource/ibm-plex-mono/600.css";
import "./design-v621.css";
import "./design-v621-react.css";
import "./design-v7.css";
import { Shell } from "./components/Shell";
import { CreateRequest } from "./pages/CreateRequest";
import { Integration } from "./pages/Integration";
import { Overview } from "./pages/Overview";
import { RequestDetail } from "./pages/RequestDetail";
import { RequestList } from "./pages/RequestList";
import { BASE, PreviewProvider } from "./PreviewContext";

/**
 * OpenTrix V6.2.1 React preview, mounted at `/design-v621/*`.
 * MOCK/DEV only: no service, auth or LIVE dependency.
 */
export default function DesignV621() {
  return (
    <PreviewProvider>
      <Routes>
        <Route element={<Shell />}>
          <Route index element={<Overview />} />
          <Route path="mine" element={<RequestList route="mine" />} />
          <Route path="queue" element={<RequestList route="queue" />} />
          <Route path="detail/:id" element={<RequestDetail />} />
          <Route path="create" element={<CreateRequest />} />
          <Route path="integration" element={<Integration />} />
          <Route path="*" element={<Navigate to={BASE} replace />} />
        </Route>
      </Routes>
    </PreviewProvider>
  );
}
