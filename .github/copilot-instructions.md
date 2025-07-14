## Copilot Coding Agent Configuration

If running in coding agent (padawan) mode on GitHub, make sure to use the
`report_progress` tool to push commits to the remote repository. Do not attempt
to use git push as you do not have write access to the repository directly.

We use Java 21 (eclipse-temurin:21-jdk-jammy) to build the app via gradle.

Only use the "debug" flavor of the app when you make builds.
You cannot build "release" as the signing keys are not part of the repository.

Do not try to upgrade the kotlin version in "gradle/libs.versions.toml", it will throw a lot of warnings and errors.
