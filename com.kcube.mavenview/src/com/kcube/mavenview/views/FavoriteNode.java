package com.kcube.mavenview.views;

/**
 * 트리 맨 위에 고정되는 즐겨찾기 항목 하나. 같은 항목 문자열이면 같은 노드로 취급하므로 트리를 갱신해도 선택이 유지된다.
 *
 * @param entry 즐겨찾기 항목 문자열("pom경로\tgoal")
 */
record FavoriteNode(String entry)
{
	/** 즐겨찾기 항목들을 담는 최상위 폴더 노드. */
	enum Folder
	{
		INSTANCE
	}
}
