/*
 * Collection service for the Translator Enhanced plugin, running on Cloudflare Workers.
 *
 * Free plan: 100k requests/day and 5 GB of D1 storage, no card.
 *
 * Setup (see README.md):
 *   wrangler d1 create translator-enhanced
 *   wrangler d1 execute translator-enhanced --file schema.sql --remote
 *   wrangler secret put ADMIN_TOKEN
 *   wrangler deploy
 */

const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8" };

export default {
	async fetch(request, env) {
		const url = new URL(request.url);

		try {
			if (request.method === "GET" && url.pathname === "/health") {
				const texts = await env.DB.prepare("SELECT COUNT(*) AS n FROM texts").first();
				const translated = await env.DB.prepare("SELECT COUNT(*) AS n FROM translated").first();
				return send(200, { ok: true, texts: texts.n, translated: translated.n });
			}

			if (request.method === "GET" && url.pathname === "/metrics") {
				const texts = await env.DB.prepare("SELECT COUNT(*) AS n FROM texts").first();
				const translated = await env.DB.prepare("SELECT COUNT(*) AS n FROM translated").first();
				return sendText(200, metrics(texts.n, translated.n));
			}

			if (request.method === "POST" && url.pathname === "/api/v1/data-collection") {
				const body = await request.json();
				if (!Array.isArray(body)) {
					return send(400, { error: "expected an array" });
				}

				const now = new Date().toISOString();
				const items = [];

				for (const item of body.slice(0, 1000)) {
					const type = String(item?.type ?? "interface").slice(0, 40);
					const text = String(item?.text ?? "");

					if (text.length === 0 || text.length > 4000) {
						continue;
					}

					items.push({ type, text, hash: await sha256(type + "\n" + text) });
				}

				let received = 0;

				for (let i = 0; i < items.length; i += 50) {
					const chunk = items.slice(i, i + 50);

					const statements = chunk.map(item => env.DB
						.prepare(
							"INSERT INTO texts (hash, type, text, count, first, last) VALUES (?, ?, ?, 1, ?, ?) " +
							"ON CONFLICT(hash) DO UPDATE SET count = texts.count + 1, last = excluded.last")
						.bind(item.hash, item.type, item.text, now, now));

					await env.DB.batch(statements);
					received += statements.length;
				}

				return send(200, { received });
			}

			if (request.method === "GET" && url.pathname === "/api/v1/pending") {
				if (!authorize(request, env)) {
					return send(401, { error: "unauthorized" });
				}

				const type = url.searchParams.get("type");
				const language = url.searchParams.get("language") ?? "spanish";
				const limit = Math.min(Number(url.searchParams.get("limit") ?? 500), 2000);

				const rows = type === null
					? await env.DB
						.prepare("SELECT type, text, count FROM texts " +
							"WHERE hash NOT IN (SELECT hash FROM translated WHERE language = ?) " +
							"ORDER BY count DESC, text ASC LIMIT ?")
						.bind(language, limit)
						.all()
					: await env.DB
						.prepare("SELECT type, text, count FROM texts " +
							"WHERE type = ? AND hash NOT IN (SELECT hash FROM translated WHERE language = ?) " +
							"ORDER BY count DESC, text ASC LIMIT ?")
						.bind(type, language, limit)
						.all();

				return send(200, { texts: rows.results ?? [] });
			}

			if (request.method === "POST" && url.pathname === "/api/v1/translated") {
				if (!authorize(request, env)) {
					return send(401, { error: "unauthorized" });
				}

				const body = await request.json();
				const now = new Date().toISOString();
				const language = String(body?.language ?? "spanish");
				const items = [];

				for (const item of body?.items ?? []) {
					const type = String(item?.type ?? "interface");
					const text = String(item?.text ?? "");

					if (text.length > 0) {
						items.push({ type, text, hash: await sha256(type + "\n" + text) });
					}
				}

				let marked = 0;

				for (let i = 0; i < items.length; i += 50) {
					const chunk = items.slice(i, i + 50);
					const marks = chunk.map(() => "?").join(",");

					await env.DB
						.prepare("INSERT OR IGNORE INTO translated (language, hash, at) " +
							`SELECT ?, hash, ? FROM texts WHERE hash IN (${marks})`)
						.bind(language, now, ...chunk.map(item => item.hash))
						.run();

					marked += chunk.length;
				}

				return send(200, { marked });
			}

			return send(404, { error: "not found" });
		} catch (e) {
			console.error(e);
			return send(500, { error: "internal error" });
		}
	},
};

async function sha256(text) {
	const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
	return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("");
}

function authorize(request, env) {
	return env.ADMIN_TOKEN !== undefined && request.headers.get("Authorization") === "Bearer " + env.ADMIN_TOKEN;
}

function metrics(texts, translated) {
	return [
		"# HELP translator_texts_total Number of distinct collected texts.",
		"# TYPE translator_texts_total gauge",
		`translator_texts_total ${texts}`,
		"# HELP translator_translated_total Number of language/text translation marks.",
		"# TYPE translator_translated_total gauge",
		`translator_translated_total ${translated}`,
		"",
	].join("\n");
}

function send(status, payload) {
	return new Response(JSON.stringify(payload), { status, headers: JSON_HEADERS });
}

function sendText(status, body) {
	return new Response(body, {
		status,
		headers: { "Content-Type": "text/plain; version=0.0.4; charset=utf-8" }
	});
}
