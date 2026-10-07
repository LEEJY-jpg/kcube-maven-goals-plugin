package com.kcube.mavenview.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 즐겨찾기와 최근 실행 목록(UI 의존성 없음).
 * <p>
 * 항목은 "pom 경로 + TAB + goal 명령행" 문자열이다. 최근 실행 목록은 항상 최대 {@value #MAX_RECENT}개만 유지하며(오래된 것부터 버림),
 * pom.xml이 사라진 항목은 {@link #prune(Predicate)}로 즐겨찾기와 최근 실행 양쪽에서 함께 지운다.
 */
public final class GoalHistory
{
	/** 최근 실행 목록에 보관할 최대 개수. */
	public static final int MAX_RECENT = 10;
	/** 항목 안에서 pom 경로와 goal 문자열을 구분하는 문자. */
	public static final String ENTRY_SEP = "\t";
	/** 저장할 때 항목들을 이어 붙이는 구분자(파일 경로에 나올 수 없는 개행). */
	public static final String LIST_SEP = "\n";

	private final Set<String> favorites = new LinkedHashSet<>();
	private final LinkedList<String> recents = new LinkedList<>();

	/** pom 경로와 goal 명령행으로 항목 문자열을 만든다. */
	public static String entry(String pomPath, String goal)
	{
		return pomPath + ENTRY_SEP + goal;
	}

	/** 항목 문자열에서 pom 경로를 꺼낸다. 구분자가 없으면 전체를 반환한다. */
	public static String pomOf(String entry)
		{
		int i = entry.indexOf(ENTRY_SEP);
		return i < 0 ? entry : entry.substring(0, i);
		}

	/** 항목 문자열에서 goal 명령행을 꺼낸다. 구분자가 없으면 빈 문자열을 반환한다. */
	public static String goalOf(String entry)
		{
		int i = entry.indexOf(ENTRY_SEP);
		return i < 0 ? "" : entry.substring(i + ENTRY_SEP.length());
		}

	/** 즐겨찾기 목록(추가한 순서, 읽기 전용). */
	public Set<String> favorites()
	{
		return Collections.unmodifiableSet(favorites);
	}

	/** 최근 실행 목록(가장 최근이 맨 앞, 읽기 전용). */
	public List<String> recents()
	{
		return Collections.unmodifiableList(recents);
	}

	/** 항목이 즐겨찾기인지 확인한다. */
	public boolean isFavorite(String entry)
	{
		return favorites.contains(entry);
	}

	/** 즐겨찾기를 토글한다. 추가됐으면 true, 제거됐으면 false를 반환한다. */
	public boolean toggleFavorite(String entry)
	{
		if (favorites.remove(entry))
			return false;
		favorites.add(entry);
		return true;
	}

	/** 최근 실행 맨 앞에 추가한다. 중복은 하나로 합치고 {@value #MAX_RECENT}개를 넘는 오래된 항목은 버린다. */
	public void addRecent(String entry)
	{
		recents.remove(entry);
		recents.addFirst(entry);
		trimRecents();
	}

	/** 최근 실행 목록을 모두 지운다. */
	public void clearRecents()
	{
		recents.clear();
	}

	/**
	 * pom.xml이 더 이상 존재하지 않는 항목을 즐겨찾기와 최근 실행에서 모두 지운다.
	 *
	 * @param pomExists pom 경로를 받아 파일이 존재하는지 알려주는 함수
	 * @return 하나라도 지웠으면 true
	 */
	public boolean prune(Predicate<String> pomExists)
	{
		boolean a = favorites.removeIf(e -> !pomExists.test(pomOf(e)));
		boolean b = recents.removeIf(e -> !pomExists.test(pomOf(e)));
		return a || b;
	}

	/**
	 * 모든 항목의 pom 경로를 변환한다(예: 절대 경로 → 정규 경로). 변환 결과가 같아진 항목은 하나로 합치고 순서는 유지하며, 최근 실행은 최대
	 * 개수까지만 남긴다. 저장돼 있던 옛 형식의 경로를 현재 키 형식으로 맞추는 데 쓴다.
	 *
	 * @return 하나라도 바뀌었으면 true
	 */
	public boolean rekey(java.util.function.UnaryOperator<String> pomMapper)
	{
		java.util.function.UnaryOperator<String> mapEntry = e -> entry(pomMapper.apply(pomOf(e)), goalOf(e));
		List<String> oldFavorites = new ArrayList<>(favorites);
		List<String> oldRecents = new ArrayList<>(recents);
		favorites.clear();
		oldFavorites.forEach(e -> favorites.add(mapEntry.apply(e)));
		recents.clear();
		for (String e : oldRecents)
		{
			String mapped = mapEntry.apply(e);
			if (!recents.contains(mapped))
				recents.add(mapped);
		}
		trimRecents();
		return !oldFavorites.equals(new ArrayList<>(favorites)) || !oldRecents.equals(recents);
	}

	/** 저장된 문자열(개행으로 구분)에서 목록을 읽는다. 형식이 잘못된 줄은 무시하고, 최근 실행은 최대 개수까지만 읽는다. */
	public void load(String favoritesText, String recentsText)
	{
		favorites.clear();
		recents.clear();
		for (String e : split(favoritesText))
			favorites.add(e);
		for (String e : split(recentsText))
		{
			if (!recents.contains(e))
				recents.add(e);
		}
		trimRecents();
	}

	/** 즐겨찾기를 저장용 문자열로 만든다. */
	public String serializeFavorites()
	{
		return String.join(LIST_SEP, favorites);
	}

	/** 최근 실행을 저장용 문자열로 만든다. */
	public String serializeRecents()
	{
		return String.join(LIST_SEP, recents);
	}

	/** 최근 실행이 최대 개수를 넘으면 뒤(오래된 것)에서부터 버린다. */
	private void trimRecents()
	{
		while (recents.size() > MAX_RECENT)
			recents.removeLast();
	}

	/** 개행으로 나누고 형식이 맞는 항목만 반환한다. */
	private static List<String> split(String text)
	{
		List<String> result = new ArrayList<>();
		if (text == null)
			return result;
		for (String e : text.split(LIST_SEP))
		{
			if (e.contains(ENTRY_SEP) && !e.startsWith(ENTRY_SEP))
				result.add(e);
		}
		return result;
	}
}
