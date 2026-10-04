package com.kcube.mavenview;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * 화면에 보이는 문자열(UI 라벨, 툴팁, 콘솔 메시지)을 언어별로 제공한다.
 * <p>
 * 기본은 영어({@code messages.properties})이고, Eclipse 로케일이 한국어면 {@code messages_ko.properties}의 값으로 덮어쓴다. 한국어 파일에 없는 키는
 * 영어로 대체된다. Error Log 메시지는 개발자용이므로 번역하지 않는다.
 */
public final class Messages
{
	private static final String BASE = "/com/kcube/mavenview/messages";
	private static final Properties TEXTS = load(currentLanguage());

	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private Messages()
	{
	}

	/** 키에 해당하는 문자열을 반환한다. 없으면 키 자체를 반환해 누락을 눈에 띄게 한다. */
	public static String get(String key)
	{
		return TEXTS.getProperty(key, key);
	}

	/** 키에 해당하는 문자열을 {@link MessageFormat} 규칙({0}, {1} ...)으로 채워 반환한다. */
	public static String get(String key, Object... args)
	{
		return MessageFormat.format(get(key), args);
	}

	/** 영어 기본값을 읽고, language가 있으면 해당 언어 파일로 덮어쓴 속성을 만든다. */
	static Properties load(String language)
	{
		Properties props = new Properties();
		read(props, BASE + ".properties");
		if (language != null && !language.isEmpty() && !language.equals("en"))
			read(props, BASE + "_" + language + ".properties");
		return props;
	}

	/** 클래스패스의 UTF-8 속성 파일을 props에 읽어 넣는다. 파일이 없으면 아무것도 하지 않는다. */
	private static void read(Properties props, String resource)
	{
		try (InputStream in = Messages.class.getResourceAsStream(resource))
		{
			if (in != null)
				props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (IOException ignored)
		{
			// 읽기 실패 시 영어 기본값/키 이름으로 표시된다.
		}
	}

	/** Eclipse의 실행 로케일(-nl)의 언어 코드를 반환한다. Eclipse 밖(단위 테스트)에서는 JVM 기본 로케일을 쓴다. */
	private static String currentLanguage()
	{
		try
		{
			String nl = org.eclipse.core.runtime.Platform.getNL();
			if (nl != null && !nl.isEmpty())
				return nl.split("[_-]")[0].toLowerCase(Locale.ROOT);
		}
		catch (Throwable ignored)
		{
			// OSGi가 없는 환경
		}
		return Locale.getDefault().getLanguage();
	}
}
