## Copilot Coding Agent Configuration

We use Java 21 (eclipse-temurin:21-jdk-jammy) to build the app via gradle.

If you do gradle tasks, you need to prefix them by the following env var to decrease build time. If you forget to set the env var "IS_COPILOT" to "true", the gradle buildNative task will execute which is not required for your work.
```bash
IS_COPILOT=true
```
Example: "IS_COPILOT=true gradle assembleDebug"

Only use the "debug" flavor of the app when you make gradle builds.
You cannot gradle build "release" as the signing keys are not part of the repository.
Please refrain from building the whole app with "assemleDebug" as this will take much time if it is not really required for your work. If possible, try to build smaller parts. If you need to see the lint report, use "IS_COPILOT=true gradle lintDebug"

Do not try to upgrade the kotlin version in "gradle/libs.versions.toml", it will throw a lot of warnings and errors.

No matter which language I use to write my prompt for you, please always do your coding work and code comments in english.

Do not edit the "wiki/CHANGELOG.md" file. I'll do that myself when I prepare a new release.
