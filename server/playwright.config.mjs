import { defineConfig } from "@playwright/test";

export default defineConfig({
	testDir: "./e2e",
	timeout: 15_000,
	fullyParallel: false,
	workers: 1,
	reporter: "list",
	use: {
		baseURL: "http://127.0.0.1:8099",
	},
	webServer: {
		command: "node e2e/start-server.mjs",
		url: "http://127.0.0.1:8099/health",
		reuseExistingServer: false,
		timeout: 15_000,
	},
});
