import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

const patterns = [
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
  /(?:AKIA|ASIA)[A-Z0-9]{16}/,
  /(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}/,
  /eyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}/,
  /(?:service_role|serviceRoleKey|DB_PASSWORD|client_secret|admin_password)\s*[=:]\s*["'][^"']+["']/i,
  /(?:postgres(?:ql)?:\/\/)[^\s"']+/i,
];
let inspected = 0;
const failures = [];
async function scan(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      await scan(path);
      continue;
    }
    if (!/\.(?:tsx?|m?js|json|html|css|md)$/.test(entry.name)) continue;
    const source = await readFile(path, "utf8");
    inspected++;
    if (patterns.some((pattern) => pattern.test(source))) failures.push(path);
  }
}
await scan("src");
await scan("public");
await scan("dist");
for (const path of ["package.json", "index.html"]) {
  const source = await readFile(path, "utf8");
  inspected++;
  if (patterns.some((pattern) => pattern.test(source))) failures.push(path);
}
if (failures.length) {
  console.error("FAIL: suspicious credentials in files:", failures);
  process.exit(1);
}
console.log(
  `PASS: scanned ${inspected} source/bundle files for common credential patterns. Heuristic check; review remains required.`,
);
