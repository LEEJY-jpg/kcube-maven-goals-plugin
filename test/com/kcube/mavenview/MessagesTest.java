package com.kcube.mavenview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class MessagesTest
{
	@Test
	void defaultIsEnglish()
	{
		assertEquals("Run", Messages.load("en").getProperty("action.run"));
		assertEquals("Run", Messages.load("fr").getProperty("action.run"), "지원하지 않는 언어는 영어");
		assertEquals("Run", Messages.load(null).getProperty("action.run"));
	}

	@Test
	void koreanOverridesEnglish()
	{
		assertEquals("실행", Messages.load("ko").getProperty("action.run"));
	}

	@Test
	void koreanHasSameKeysAsEnglish()
	{
		Properties en = Messages.load("en");
		Properties ko = Messages.load("ko");
		assertFalse(en.isEmpty());
		assertEquals(en.keySet(), ko.keySet());
		for (String key : en.stringPropertyNames())
		{
			// 번역이 비어 있지 않고, 자리표시자({0})가 영어와 동일해야 한다.
			assertFalse(ko.getProperty(key).isBlank(), key);
			assertEquals(en.getProperty(key).contains("{0}"), ko.getProperty(key).contains("{0}"), key);
		}
		assertNotEquals(en.getProperty("dialog.title"), ko.getProperty("dialog.title"));
	}

	@Test
	void formatsArguments()
	{
		// 테스트 JVM 로케일과 무관하게 인자 치환만 검증한다.
		String s = Messages.get("console.finished", 0);
		assertFalse(s.contains("{0}"));
		assertEquals("missing.key", Messages.get("missing.key"));
	}
}
