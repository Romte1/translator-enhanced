#!/usr/bin/env node
/*
 * Collection service for the Translator plugin.
 *
 * Stores every untranslated text sent by the clients (opt-in), deduplicated, and exposes
 * the aggregated queue so the worker translates what the players actually see.
 *
 * Environment:
 *   PORT          listening port (default 8080)
 *   DATA_DIR      where the jsonl files live (default ./data)
 *   ADMIN_TOKEN   required for /pending and /translated
 *
 * No dependencies: `node server/server.mjs`.
 */

import { createServer } from "node:http";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, appendFileSync, existsSync, mkdirSync, renameSync } from "node:fs";
import { join } from "node:path";

const port = Number(process.env.PORT ?? 8080);
const dataDir = process.env.DATA_DIR ?? join(process.cwd(), "data");
const adminToken = process.env.ADMIN_TOKEN ?? "";

mkdirSync(dataDir, { recursive: true });

const textsFile = join(dataDir, "texts.jsonl");
const translatedFile = join(dataDir, "translated.jsonl");

const texts = new Map();
const translated = new Set();
const requests = new Map();

load();

let dirty = false;
setInterval(() => {
	if (dirty) {
		persist();
		dirty = false;
	}
}, 30000).unref();

process.on("SIGTERM", () => {
	if (dirty) {
		persist();
	}
	process.exit(0);
});

const server = createServer(async (req, res) => {
	try {
		const url = new URL(req.url, "http://localhost");

		if (req.method === "GET" && url.pathname === "/health") {
			return send(res, 200, { ok: true, texts: texts.size, translated: translated.size });
		}

		if (req.method === "GET" && url.pathname === "/metrics") {
			return sendText(res, 200, metrics());
		}

		if (req.method === "POST" && url.pathname === "/api/v1/data-collection") {
			if (!allow(req)) {
				return send(res, 429, { error: "too many requests" });
			}

			const body = await readJson(req, 512 * 1024);
			if (!Array.isArray(body)) {
				return send(res, 400, { error: "expected an array" });
			}

			const now = new Date().toISOString();
			let added = 0;

			for (const item of body.slice(0, 1000)) {
				const type = String(item?.type ?? "interface").slice(0, 40);
				const text = String(item?.text ?? "");

				if (text.length === 0 || text.length > 4000) {
					continue;
				}

				const hash = hashOf(type, text);
				

				const row = texts.get(hash);
				if (row) {
					row.count++;
					row.last = now;
				} else {
					texts.set(hash, { hash, type, text, count: 1, first: now, last: now });
					added++;
				}
			}

			dirty = true;
			return send(res, 200, { added });
		}

		if (req.method === "GET" && url.pathname === "/api/v1/pending") {
			if (!authorize(req)) {
				return send(res, 401, { error: "unauthorized" });
			}

			const type = url.searchParams.get("type");
			const language = url.searchParams.get("language") ?? "spanish";
			const limit = Math.min(Number(url.searchParams.get("limit") ?? 500), 2000);

			const rows = [...texts.values()]
				.filter(row => !translated.has(markKey(language, row.hash)) && (type === null || row.type === type))
				.sort((a, b) => b.count - a.count || a.text.localeCompare(b.text))
				.slice(0, limit)
				.map(row => ({ type: row.type, text: row.text, count: row.count }));

			return send(res, 200, { texts: rows });
		}

		if (req.method === "POST" && url.pathname === "/api/v1/translated") {
			if (!authorize(req)) {
				return send(res, 401, { error: "unauthorized" });
			}

			const body = await readJson(req, 1024 * 1024);
			const language = String(body?.language ?? "spanish");
			let marked = 0;

			for (const item of body?.items ?? []) {
				const hash = hashOf(String(item?.type ?? "interface"), String(item?.text ?? ""));
				const key = markKey(language, hash);

				if (texts.has(hash) && !translated.has(key)) {
					translated.add(key);
					appendFileSync(translatedFile, `${language}\t${hash}\n`, "utf8");
					marked++;
				}
			}

			return send(res, 200, { marked });
		}

		return send(res, 404, { error: "not found" });
	} catch (e) {
		console.error(e);
		return send(res, 500, { error: "internal error" });
	}
});

server.listen(port, () => {
	console.log(`translator collection listening on :${port}, ${texts.size} texts`);
});

function hashOf(type, text) {
	return createHash("sha256").update(type + "\n" + text).digest("hex");
}

function load() {
	if (existsSync(textsFile)) {
		for (const line of readFileSync(textsFile, "utf8").split("\n")) {
			if (line.length === 0) {
				continue;
			}
			const row = JSON.parse(line);
			texts.set(row.hash, row);
		}
	}

	if (existsSync(translatedFile)) {
		for (const line of readFileSync(translatedFile, "utf8").split("\n")) {
			if (line.length === 0) {
				continue;
			}

			const [language, hash] = line.includes("\t") ? line.split("\t") : ["spanish", line];
			translated.add(markKey(language.trim(), hash.trim()));
		}
	}
}

function markKey(language, hash) {
	return language + "\n" + hash;
}

function persist() {
	const temporary = textsFile + ".tmp";
	writeFileSync(temporary, [...texts.values()].map(row => JSON.stringify(row)).join("\n") + "\n", "utf8");
	renameSync(temporary, textsFile);
}

function authorize(req) {
	if (adminToken.length === 0) {
		return false;
	}

	const header = req.headers.authorization ?? "";
	return header === "Bearer " + adminToken;
}

function allow(req) {
	const ip = req.socket.remoteAddress ?? "unknown";
	const now = Date.now();
	const hits = (requests.get(ip) ?? []).filter(time => now - time < 60000);

	if (hits.length >= 30) {
		requests.set(ip, hits);
		return false;
	}

	hits.push(now);
	requests.set(ip, hits);
	return true;
}

function readJson(req, limit) {
	return new Promise((resolve, reject) => {
		let size = 0;
		const chunks = [];

		req.on("data", chunk => {
			size += chunk.length;
			if (size > limit) {
				reject(new Error("payload too large"));
				req.destroy();
				return;
			}
			chunks.push(chunk);
		});

		req.on("end", () => {
			try {
				resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
			} catch (e) {
				reject(e);
			}
		});

		req.on("error", reject);
	});
}

function metrics() {
	return [
		"# HELP translator_texts_total Number of distinct collected texts.",
		"# TYPE translator_texts_total gauge",
		`translator_texts_total ${texts.size}`,
		"# HELP translator_translated_total Number of language/text translation marks.",
		"# TYPE translator_translated_total gauge",
		`translator_translated_total ${translated.size}`,
		"",
	].join("\n");
}

function send(res, status, payload) {
	const body = JSON.stringify(payload);
	res.writeHead(status, {
		"Content-Type": "application/json; charset=utf-8",
		"Content-Length": Buffer.byteLength(body),
	});
	res.end(body);
}

function sendText(res, status, body) {
	res.writeHead(status, {
		"Content-Type": "text/plain; version=0.0.4; charset=utf-8",
		"Content-Length": Buffer.byteLength(body),
	});
	res.end(body);
}
