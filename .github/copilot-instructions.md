## Copilot Coding Agent Configuration

We use Java 21 (eclipse-temurin:21-jdk-jammy) to build the app via gradle.

If you do gradle tasks, you need to prefix them by the following env var to decrease build time. If you forget to set the env var "IS_COPILOT" to "true", the gradle buildNative task will execute which is not required for your work.
```bash
IS_COPILOT=true
```
Example: "IS_COPILOT=true gradle assembleDebug"

Only use the "debug" flavor of the app when you make gradle builds.
You cannot gradle build "release" as the signing keys are not part of the repository.

Do not try to upgrade the kotlin version in "gradle/libs.versions.toml", it will throw a lot of warnings and errors.
