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

## Limitations
The tree is built by a lightweight reader of each `pom.xml`, **not** from Maven's effective POM, so it can differ from what Maven really runs:
- Only plug-ins declared in `<build><plugins>` (and in profiles' `<build><plugins>`) are shown. `<pluginManagement>` and `<reporting>` entries are not.
- `${...}` references are resolved only from the same file: its `<properties>`, `project.*` / `pom.*` (artifactId, groupId, version, name, parent values) and `project.basedir`. Unresolvable references (system/env variables, settings.xml, profile properties) are shown as-is.
- Plug-ins and executions that come from a **parent POM** or from Maven's default lifecycle bindings are not listed; profile activation is not evaluated.
- Run such goals from the *Lifecycle* node or with *Run with Options...* when a plug-in you need is not shown.

## Installation
Requires Eclipse 2023-09 or later with m2e (included in most Eclipse packages) and Java 17+.

1. Help > Install New Software... > Add...
2. Location: `https://leejy-jpg.github.io/kcube-maven-goals-plugin/`
3. Select **KCube Maven Tools** > **KCube Maven Goals**, then finish and restart Eclipse.
4. Open Window > Show View > Other... > **Maven Goals**.

> **Note:** The plug-in is not code-signed, so Eclipse shows a "Trust Artifacts" dialog
> ("Do you trust unsigned content of unknown origin?") during installation.
> Select the `com.kcube.mavenview` entries and click **Trust Selected** to continue.
> You do not need to check "Always trust all content".

> **Tip:** If the install tries to download hundreds of unrelated bundles or fails with
> "Can't download artifact ...", uncheck
> **"Contact all update sites during install to find required software"** in the install dialog.
> This is caused by the many update sites registered in your Eclipse, not by this plug-in.
> Also make sure only this site is selected under *Work with*.

## Project layout
- `com.kcube.mavenview/` — the plug-in bundle (sources, `plugin.xml`, icons, tests)
- `com.kcube.mavenview.tests/` — unit-test fragment (runs the sources in `com.kcube.mavenview/test/` during `mvn verify`; not shipped)
- `com.kcube.mavenview.feature/` — Eclipse feature
- `com.kcube.mavenview.update-site/` — p2 update site (`category.xml`)

## Build
Requires Maven 3.9+ and JDK 17+.

```bash
mvn clean verify
```

The p2 repository is generated in `com.kcube.mavenview.update-site/target/repository`
(and zipped as `com.kcube.mavenview.update-site-<version>.zip`).
For a quick single-jar build for `dropins`, use `./build.sh` (see `DEVELOPMENT.md`).

## Import (development)
1. Eclipse: File > Import > Existing Projects into Workspace
2. Select the `com.kcube.mavenview` directory.
3. Run `com.kcube.mavenview` as an Eclipse Application.

## License
Apache License 2.0 — see [LICENSE](LICENSE).
