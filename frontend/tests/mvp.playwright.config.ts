import { defineConfig } from "@playwright/test";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import base from "../playwright.config";

/** Explicit fake-only browser entry. Standard production build stays unchanged. */
export default defineConfig({
  ...base,
  testDir: ".",
  testMatch: "helpdesk-mvp.spec.ts",
  outputDir: mkdtempSync(join(tmpdir(), "dxos-app14-browser-")),
  projects: base.projects!.map((project) => ({
    ...project,
    metadata: { fakeHelpdeskPreview: true },
  })),
  webServer: {
    command:
      "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 4173 --strictPort",
    cwd: resolve(import.meta.dirname, ".."),
    url: "http://127.0.0.1:4173",
    reuseExistingServer: false,
  },
});
