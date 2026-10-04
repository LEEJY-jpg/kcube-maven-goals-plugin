package com.kcube.mavenview.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

/** 등록된 pom.xml 경로 목록을 workspace preference에 저장/복원한다. */
public final class PomRegistryStore
{
	private static final String PREFS_NODE = "com.kcube.mavenview";
	private static final String PREF_POMS = "registeredPoms";
	private static final String SEP = "\n";

	private PomRegistryStore()
	{
	}

	/** 저장된 pom.xml 경로들을 등록 순서대로 반환한다. */
	public static List<String> load()
	{
		List<String> result = new ArrayList<>();
		String stored = InstanceScope.INSTANCE.getNode(PREFS_NODE).get(PREF_POMS, "");
		for (String path : stored.split(SEP))
		{
			if (!path.isBlank())
				result.add(path);
		}
		return result;
	}

	/** 경로 목록을 저장하고 즉시 flush 한다. */
	public static void save(Collection<String> paths) throws BackingStoreException
	{
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PREFS_NODE);
		prefs.put(PREF_POMS, String.join(SEP, paths));
		prefs.flush();
	}
}
