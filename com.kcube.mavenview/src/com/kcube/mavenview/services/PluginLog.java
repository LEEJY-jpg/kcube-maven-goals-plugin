package com.kcube.mavenview.services;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/** Eclipse Error Log에 이 플러그인 이름으로 메시지를 남기는 공용 유틸리티. */
public final class PluginLog
{
	private static final String PLUGIN_ID = "com.kcube.mavenview";

	private PluginLog()
	{
	}

	/** @param severity {@link org.eclipse.core.runtime.IStatus} 심각도 */
	public static void log(int severity, String message, Throwable e)
	{
		try
		{
			Platform.getLog(PluginLog.class).log(new Status(severity, PLUGIN_ID, message, e));
		}
		catch (RuntimeException | LinkageError ex)
		{
			// OSGi 밖(단위 테스트 등)에서는 Eclipse 로그를 쓸 수 없다. 로깅 실패가 호출자를 깨뜨리면 안 되므로 stderr로 대신한다.
			System.err.println("[" + PLUGIN_ID + "] " + message + (e != null ? ": " + e : ""));
		}
	}
}
