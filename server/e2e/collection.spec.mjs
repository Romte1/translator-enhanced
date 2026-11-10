import { expect, test } from "@playwright/test";

const auth = { Authorization: "Bearer e2e-token" };

test("health and metrics are public", async ({ request }) => {
	const health = await request.get("/health");
	expect(health.ok()).toBeTruthy();
	expect(await health.json()).toMatchObject({ ok: true });

	const metrics = await request.get("/metrics");
	expect(metrics.ok()).toBeTruthy();
	expect(metrics.headers()["content-type"]).toContain("text/plain");
	expect(await metrics.text()).toContain("translator_texts_total");
});

test("the pending queue requires the admin token", async ({ request }) => {
	const response = await request.get("/api/v1/pending");
	expect(response.status()).toBe(401);
});

test("collect, queue and mark a text end to end", async ({ request }) => {
	const text = `E2E collection ${Date.now()} ${Math.random()}`;
	const query = "/api/v1/pending?type=interface&language=e2e&limit=2000";

	const collected = await request.post("/api/v1/data-collection", {
		data: [{ type: "interface", text }],
	});
	expect(collected.ok()).toBeTruthy();
	expect(await collected.json()).toEqual({ added: 1 });

	const pending = await request.get(query, { headers: auth });
	expect(pending.ok()).toBeTruthy();
	expect((await pending.json()).texts.map(item => item.text)).toContain(text);

	const marked = await request.post("/api/v1/translated", {
		headers: auth,
		data: { language: "e2e", items: [{ type: "interface", text }] },
	});
	expect(marked.ok()).toBeTruthy();
	expect(await marked.json()).toEqual({ marked: 1 });

	const after = await request.get(query, { headers: auth });
	expect((await after.json()).texts.map(item => item.text)).not.toContain(text);
});
