package com.kcube.mavenview.services;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.PrintWriter;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** build.sh --test 가 사용하는 콘솔 실행기(JUnit Platform Launcher). 실패가 있으면 종료 코드 1로 끝난다. */
public final class TestRunner
{
	public static void main(String[] args)
	{
		LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request().selectors(
			selectPackage("com.kcube.mavenview")).build();
		Launcher launcher = LauncherFactory.create();
		SummaryGeneratingListener listener = new SummaryGeneratingListener();
		launcher.execute(request, listener);
		PrintWriter out = new PrintWriter(System.out);
		listener.getSummary().printTo(out);
		listener.getSummary().printFailuresTo(out);
		out.flush();
		System.exit(listener.getSummary().getTotalFailureCount() == 0 ? 0 : 1);
	}
}
