import { rmSync } from "node:fs";
import { join } from "node:path";

const dataDir = join(process.cwd(), ".e2e-data");
rmSync(dataDir, { recursive: true, force: true });

process.env.PORT = "8099";
process.env.ADMIN_TOKEN = "e2e-token";
process.env.DATA_DIR = dataDir;

await import("../server.mjs");
