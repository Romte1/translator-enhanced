package com.translator;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.runelite.client.util.Filepath;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Singleton
class TranslatorAPI
{
	private static final Logger log = LoggerFactory.getLogger(TranslatorAPI.class);

	private static final String TRANSLATIONS_URL =
		"https://raw.githubusercontent.com/iolave/osrs-translations/refs/heads/master/%s/%s.txt";
	private static final String CURATED_MANIFEST_URL =
		"https://raw.githubusercontent.com/Romte1/translator-enhanced-translations/"
			+ "refs/heads/main/manifest.json";
	private static final String CURATED_FILE_URL =
		"https://raw.githubusercontent.com/Romte1/translator-enhanced-translations/"
			+ "refs/heads/main/%s";
	private static final String DATA_COLLECTION_URL =
		"https://translator-enhanced-collection.ivan-ni224.workers.dev/api/v1/data-collection";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final Pattern SHA_256 = Pattern.compile("[a-fA-F0-9]{64}");
	private static final int MAX_MANIFEST_BYTES = 1_000_000;
	private static final int MAX_TRANSLATION_BYTES = 16 * 1024 * 1024;

	@Inject
	private OkHttpClient client;

	@Inject
	private Gson gson;

	private final Set<String> collectedTexts = ConcurrentHashMap.newKeySet();

	private volatile boolean textCaptureEnabled;

	Map<String, String> download(TranslationType type, TranslatorConfig.Language lang) throws IOException
	{
		return downloadFrom(String.format(TRANSLATIONS_URL, lang, type.fileName()));
	}

	Map<String, String> downloadCurated(TranslationType type, TranslatorConfig.Language lang,
		Filepath cache) throws IOException
	{
		JsonObject manifest;
		try
		{
			manifest = gson.fromJson(new String(
				downloadBytes(CURATED_MANIFEST_URL, MAX_MANIFEST_BYTES), StandardCharsets.UTF_8),
				JsonObject.class);
		}
		catch (RuntimeException e)
		{
			throw new IOException("invalid translation manifest", e);
		}

		if (manifest == null || manifest.get("schemaVersion") == null
			|| manifest.get("schemaVersion").getAsInt() != 1)
		{
			throw new IOException("unsupported translation manifest");
		}

		JsonElement filesElement = manifest.get("files");
		if (filesElement == null || !filesElement.isJsonObject())
		{
			throw new IOException("translation manifest has no files");
		}

		String path = "translations/" + type.fileName() + "/" + lang + ".txt";
		JsonElement hashElement = filesElement.getAsJsonObject().get(path);
		if (hashElement == null || !SHA_256.matcher(hashElement.getAsString()).matches())
		{
			throw new IOException("missing SHA-256 for " + path);
		}

		String expected = hashElement.getAsString().toLowerCase(Locale.ROOT);
		byte[] bytes = downloadBytes(String.format(CURATED_FILE_URL, path), MAX_TRANSLATION_BYTES);
		if (!sha256(bytes).equals(expected))
		{
			throw new IOException("SHA-256 mismatch for " + path);
		}

		writeCache(cache, bytes);

		try (InputStream in = new ByteArrayInputStream(bytes);
			 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return TranslationStore.parse(reader);
		}
	}

	private Map<String, String> downloadFrom(String url) throws IOException
	{
		Request request = new Request.Builder()
			.method("GET", null)
			.url(url)
			.build();

		try (Response response = client.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			if (response.code() != 200 || responseBody == null)
			{
				throw new IOException("failed to download " + url + ", status " + response.code());
			}

			try (InputStream in = responseBody.byteStream();
				 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return TranslationStore.parse(reader);
			}
		}
	}

	private byte[] downloadBytes(String url, int maximumBytes) throws IOException
	{
		Request request = new Request.Builder()
			.get()
			.url(url)
			.build();

		try (Response response = client.newCall(request).execute())
		{
			if (response.code() != 200 || !response.request().url().isHttps())
			{
				throw new IOException("failed to download " + url + ", status " + response.code());
			}

			ResponseBody body = response.body();
			if (body == null || body.contentLength() > maximumBytes)
			{
				throw new IOException("invalid response for " + url);
			}

			try (InputStream in = body.byteStream();
				 ByteArrayOutputStream out = new ByteArrayOutputStream())
			{
				byte[] buffer = new byte[16 * 1024];
				int read;
				int total = 0;
				while ((read = in.read(buffer)) != -1)
				{
					total += read;
					if (total > maximumBytes)
					{
						throw new IOException("response too large for " + url);
					}
					out.write(buffer, 0, read);
				}
				return out.toByteArray();
			}
		}
	}

	private static String sha256(byte[] bytes) throws IOException
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder result = new StringBuilder(digest.length * 2);
			for (byte value : digest)
			{
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IOException("SHA-256 unavailable", e);
		}
	}

	private static void writeCache(Filepath cache, byte[] bytes)
	{
		if (cache == null)
		{
			return;
		}

		try
		{
			cache.getParent().createDirectories();
			Filepath temporary = cache.getParent().createTempFile("translations-", ".tmp");
			try
			{
				temporary.write(bytes);
				try
				{
					temporary.moveTo(cache, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
				}
				catch (IOException e)
				{
					temporary.moveTo(cache, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			finally
			{
				temporary.deleteIfExists();
			}
		}
		catch (IOException e)
		{
			log.debug("failed to cache translations at {}", cache, e);
		}
	}

	void setTextCaptureEnabled(boolean enabled)
	{
		textCaptureEnabled = enabled;
		if (!enabled)
		{
			collectedTexts.clear();
		}
	}

	void collect(TranslationType type, String text)
	{
		if (!textCaptureEnabled || text == null || text.isEmpty())
		{
			return;
		}

		collectedTexts.add(type.fileName() + ";" + text);
	}

	void flushCollectedTexts() throws IOException
	{
		if (!textCaptureEnabled || collectedTexts.isEmpty())
		{
			return;
		}

		JsonArray body = new JsonArray();
		for (String entry : collectedTexts)
		{
			int separator = entry.indexOf(';');
			if (separator <= 0 || separator == entry.length() - 1)
			{
				continue;
			}

			JsonObject object = new JsonObject();
			object.add("type", new JsonPrimitive(entry.substring(0, separator)));
			JsonElement text = new JsonPrimitive(entry.substring(separator + 1));
			object.add("text", text);
			body.add(object);
		}

		if (body.size() == 0)
		{
			collectedTexts.clear();
			return;
		}

		Request request = new Request.Builder()
			.header("Content-Type", "application/json")
			.method("POST", RequestBody.create(JSON, body.toString()))
			.url(DATA_COLLECTION_URL)
			.build();

		try (Response response = client.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("failed to send collected texts, got status " + response.code());
			}
		}
	}
}
