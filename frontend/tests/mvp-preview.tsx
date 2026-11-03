// Explicit test entry only. main.tsx keeps <App /> and MockRequestService.
import ReactDOM from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { App } from "../src/App";
import { HelpdeskRequestService } from "../src/services/mvp/HelpdeskRequestService";
import { HelpdeskApiClient } from "../src/services/mvp/http/HelpdeskApiClient";
import { fakeHelpdeskTransport } from "../src/test/fakeHelpdeskTransport";
import "@fontsource/be-vietnam-pro/vietnamese-400.css";
import "@fontsource/be-vietnam-pro/vietnamese-500.css";
import "@fontsource/be-vietnam-pro/vietnamese-600.css";
import "@fontsource/be-vietnam-pro/vietnamese-700.css";
import "../src/styles.css";
import "../src/university-theme.css";

const fake = fakeHelpdeskTransport();
const service = new HelpdeskRequestService(
  new HelpdeskApiClient(fake.transport, () => {}),
);
ReactDOM.createRoot(document.getElementById("root")!).render(
  <MemoryRouter initialEntries={["/session"]}>
    <App helpdeskService={service} />
  </MemoryRouter>,
);
