package com.kcube.mavenview.services;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * pom.xml 한 개 안에서 {@code ${...}} 참조를 풀어 주는 경량 치환기.
 * <p>
 * 지원 범위: 그 pom의 {@code <properties>}, 그리고 {@code project.*}/{@code pom.*}의 artifactId·groupId·version·name,
 * {@code project.parent.*}, {@code project.basedir}/{@code basedir}. 부모 pom, settings.xml, 시스템/환경 변수, 프로파일의
 * {@code <properties>}는 읽지 않으므로(= effective pom이 아님) 풀 수 없는 참조는 {@code ${...}} 그대로 남긴다.
 */
final class PomProperties
{
	private static final Pattern REF = Pattern.compile("\\$\\{([^}]+)}");
	/** 속성이 다른 속성을 참조하는 중첩을 풀 최대 횟수. 순환 참조(a→b→a)에서 무한 반복하지 않게 한다. */
	private static final int MAX_PASSES = 10;

	private final Map<String, String> values;

	private PomProperties(Map<String, String> values)
	{
		this.values = values;
	}

	/** project 엘리먼트와 pom 파일 위치에서 치환 표를 만든다. */
	static PomProperties of(Element project, File pom)
	{
		Map<String, String> v = new HashMap<>();
		Element props = MavenPomParser.directChild(project, "properties");
		if (props != null)
		{
			for (Node n = props.getFirstChild(); n != null; n = n.getNextSibling())
			{
				if (n instanceof Element e && e.getTextContent() != null)
					v.put(e.getTagName(), e.getTextContent().trim());
			}
		}
		Element parent = MavenPomParser.directChild(project, "parent");
		String groupId = firstNonNull(MavenPomParser.text(project, "groupId"), text(parent, "groupId"));
		String version = firstNonNull(MavenPomParser.text(project, "version"), text(parent, "version"));
		for (String prefix : new String[] {"project.", "pom."})
		{
			putIfAbsent(v, prefix + "artifactId", MavenPomParser.text(project, "artifactId"));
			putIfAbsent(v, prefix + "groupId", groupId);
			putIfAbsent(v, prefix + "version", version);
			putIfAbsent(v, prefix + "name", MavenPomParser.text(project, "name"));
			putIfAbsent(v, prefix + "parent.artifactId", text(parent, "artifactId"));
			putIfAbsent(v, prefix + "parent.groupId", text(parent, "groupId"));
			putIfAbsent(v, prefix + "parent.version", text(parent, "version"));
		}
		if (pom != null && pom.getParentFile() != null)
		{
			putIfAbsent(v, "project.basedir", pom.getParentFile().getAbsolutePath());
			putIfAbsent(v, "basedir", pom.getParentFile().getAbsolutePath());
		}
		return new PomProperties(v);
	}

	/** 문자열 안의 {@code ${...}}를 풀 수 있는 만큼 풀어 반환한다. null은 null. */
	String resolve(String s)
	{
		if (s == null || s.indexOf("${") < 0)
			return s;
		String current = s;
		for (int pass = 0; pass < MAX_PASSES; pass++)
		{
			Matcher m = REF.matcher(current);
			StringBuilder sb = new StringBuilder();
			boolean changed = false;
			while (m.find())
			{
				String value = values.get(m.group(1).trim());
				if (value != null)
					changed = true;
				m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group()));
			}
			m.appendTail(sb);
			current = sb.toString();
			if (!changed)
				break;
		}
		return current;
	}

	private static String text(Element parent, String tag)
	{
		return parent == null ? null : MavenPomParser.text(parent, tag);
	}

	private static String firstNonNull(String a, String b)
	{
		return a != null && !a.isBlank() ? a : b;
	}

	private static void putIfAbsent(Map<String, String> m, String key, String value)
	{
		if (value != null && !value.isBlank())
			m.putIfAbsent(key, value);
	}
}
