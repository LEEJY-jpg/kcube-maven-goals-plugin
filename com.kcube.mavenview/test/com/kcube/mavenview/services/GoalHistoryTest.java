package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GoalHistoryTest
{
	private static String e(String pom, String goal)
	{
		return GoalHistory.entry(pom, goal);
	}

	@Test
	void entryRoundTrip()
	{
		String entry = e("/a/b/pom.xml", "clean install -DskipTests");
		assertEquals("/a/b/pom.xml", GoalHistory.pomOf(entry));
		assertEquals("clean install -DskipTests", GoalHistory.goalOf(entry));
	}

	@Test
	void recentsKeepOnlyTenNewestFirst()
	{
		GoalHistory h = new GoalHistory();
		for (int i = 1; i <= 15; i++)
			h.addRecent(e("/p/pom.xml", "goal" + i));
		assertEquals(10, h.recents().size());
		assertEquals(e("/p/pom.xml", "goal15"), h.recents().get(0));
		assertEquals(e("/p/pom.xml", "goal6"), h.recents().get(9));
	}

	@Test
	void recentDuplicateMovesToFront()
	{
		GoalHistory h = new GoalHistory();
		h.addRecent(e("/p/pom.xml", "a"));
		h.addRecent(e("/p/pom.xml", "b"));
		h.addRecent(e("/p/pom.xml", "a"));
		assertEquals(List.of(e("/p/pom.xml", "a"), e("/p/pom.xml", "b")), h.recents());
	}

	@Test
	void loadTruncatesOldOversizedRecentList()
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 25; i++)
			sb.append(e("/p/pom.xml", "g" + i)).append(GoalHistory.LIST_SEP);
		GoalHistory h = new GoalHistory();
		h.load("", sb.toString());
		assertEquals(10, h.recents().size());
		assertEquals(e("/p/pom.xml", "g1"), h.recents().get(0), "저장된 순서(최근이 앞)를 유지하고 뒤쪽을 버린다");
	}

	@Test
	void loadIgnoresMalformedLinesAndDuplicates()
	{
		GoalHistory h = new GoalHistory();
		h.load("garbage\n" + e("/p/pom.xml", "x") + "\n\n\tnopath", e("/p/pom.xml", "y") + "\n" + e("/p/pom.xml", "y"));
		assertEquals(Set.of(e("/p/pom.xml", "x")), h.favorites());
		assertEquals(List.of(e("/p/pom.xml", "y")), h.recents());
	}

	@Test
	void serializeAndLoadRoundTrip()
	{
		GoalHistory a = new GoalHistory();
		a.toggleFavorite(e("/a/pom.xml", "compile"));
		a.toggleFavorite(e("/b/pom.xml", "test"));
		a.addRecent(e("/a/pom.xml", "package"));
		a.addRecent(e("/b/pom.xml", "verify"));
		GoalHistory b = new GoalHistory();
		b.load(a.serializeFavorites(), a.serializeRecents());
		assertEquals(List.copyOf(a.favorites()), List.copyOf(b.favorites()));
		assertEquals(a.recents(), b.recents());
	}

	@Test
	void toggleFavorite()
	{
		GoalHistory h = new GoalHistory();
		String entry = e("/a/pom.xml", "compile");
		assertTrue(h.toggleFavorite(entry));
		assertTrue(h.isFavorite(entry));
		assertFalse(h.toggleFavorite(entry));
		assertFalse(h.isFavorite(entry));
	}

	@Test
	void pruneRemovesEntriesOfDeletedPomFromBothLists()
	{
		GoalHistory h = new GoalHistory();
		h.toggleFavorite(e("/gone/pom.xml", "compile"));
		h.toggleFavorite(e("/keep/pom.xml", "compile"));
		h.addRecent(e("/gone/pom.xml", "package"));
		h.addRecent(e("/keep/pom.xml", "package"));
		assertTrue(h.prune(path -> path.startsWith("/keep")));
		assertEquals(Set.of(e("/keep/pom.xml", "compile")), h.favorites());
		assertEquals(List.of(e("/keep/pom.xml", "package")), h.recents());
		assertFalse(h.prune(path -> path.startsWith("/keep")), "더 지울 것이 없으면 false");
	}
}
