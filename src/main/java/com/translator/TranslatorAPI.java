package com.translator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class TranslatorAPI
{
	private static final Logger log = LoggerFactory.getLogger(TranslatorAPI.class);

	private static final String TRANSLATIONS_URL =
		"https://raw.githubusercontent.com/iolave/osrs-translations/refs/heads/master/%s/%s.txt";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	@Inject
	private OkHttpClient client;

	@Inject
	private TranslatorConfig config;

	private final Set<String> collectedTexts = ConcurrentHashMap.newKeySet();

	private volatile boolean textCaptureEnabled;

	Map<String, String> download(TranslationType type, TranslatorConfig.Language lang) throws IOException
	{
		if (type == TranslationType.INTERFACE)
		{
			return downloadFrom(config.interfaceTranslationsUrl().replace("{language}", lang.toString()));
		}

		return downloadFrom(String.format(TRANSLATIONS_URL, lang, type.fileName()));
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
		String url = config.dataCollectionUrl();

		if (!textCaptureEnabled || collectedTexts.isEmpty() || url == null || url.trim().isEmpty())
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
			.url(url)
			.build();

		try (Response response = client.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("failed to send collected texts, got status " + response.code());
			}
		}

		collectedTexts.clear();
		log.debug("sent {} collected texts", body.size());
	}
}
