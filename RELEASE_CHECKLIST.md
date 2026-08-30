# Launcher Release Checklist

Use this after the matching Knox Survivors Workshop build has been uploaded and is visible to subscribers.

1. Confirm the Workshop package contains `42/knox-runtime.properties`, the Java agent JAR, and its checksum.
2. Confirm `launcherCompatibility=1` and that `runtimeVersion` matches the agent JAR manifest version.
3. Test the staged Workshop package and launcher on Windows.
4. Commit and push launcher changes.
5. Create and push a version tag, for example `v0.2.0-preview.1`.
6. Wait for the GitHub Actions checks on Windows, Linux, and macOS.
7. Download each archive from the draft/pre-release and inspect its contents.
8. Publish the GitHub pre-release.
9. Post the GitHub release link and the instructions in `TESTING.md` to testers.
10. Keep the release marked as a preview until Linux and macOS testers report that the game starts and the Java bridge loads.

Do not publish a launcher release before the matching Workshop update. The launcher intentionally rejects the old IsoZombie Workshop package.
