# KCube Maven Goals Eclipse Plug-in

Eclipse Plug-in for displaying Maven lifecycle phases and plugin goals in an Ant View-like tree.

## Features
- Detect selected Eclipse Maven project / pom.xml
- Display Maven lifecycle phases
- Display Maven plugins and configured executions
- Double-click a lifecycle phase or plugin goal to run Maven
- Run Maven through the local `mvn` executable
- Output Maven stdout/stderr to an Eclipse Console
- Refresh from the current selection
- Eclipse 4.x / Java 17+ target (built with `--release 17`)

## Project layout
- `com.kcube.mavenview/` — the plug-in bundle (sources, `plugin.xml`, icons, tests)
- `com.kcube.mavenview.feature/` — Eclipse feature
- `com.kcube.mavenview.update-site/` — p2 update site (`category.xml`)

## Build
Requires Maven 3.9+ and JDK 17+.

```bash
mvn clean verify
```

The p2 repository is generated in `com.kcube.mavenview.update-site/target/repository`
(and zipped as `com.kcube.mavenview.update-site-<version>.zip`).
For a quick single-jar build for `dropins`, use `./build.sh` (see `개발사항.md`).

## Import (development)
1. Eclipse: File > Import > Existing Projects into Workspace
2. Select the `com.kcube.mavenview` directory.
3. Run `com.kcube.mavenview` as an Eclipse Application.

## License
Apache License 2.0 — see [LICENSE](LICENSE).
