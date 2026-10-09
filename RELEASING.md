# Building and publishing updates

The public repository is https://github.com/nivekmai/paperwork.

## Normal update

1. Make the changes on a branch and open a pull request. CI builds the app, runs Android and release-automation tests, checks lint, and independently renders the exported PDF test fixtures.
2. For a release, increase **both** `versionName` and `versionCode` in `app/build.gradle`. For example, after `0.10.2` / `12`, use `0.10.3` / `13`.
3. Optionally add a `## 0.10.3` entry to `CHANGELOG.md` with concise user-facing changes. If no entry exists, release notes are generated from commit subjects since the previous version tag. Write descriptive commit/PR titles.
4. Merge into `main`. After all checks pass, Actions signs a release APK with the existing Paperwork certificate and creates a version tag and **draft GitHub release**. The draft includes the APK, source archive, SHA-256 checksums, and changelog.
5. Open https://github.com/nivekmai/paperwork/releases, review the draft and install the APK for a phone check, then click **Publish release**. Publishing is a deliberate manual step.

Ordinary pushes without a version bump still run CI but do not create another release. Published releases and their tags are never overwritten. To change a version already tagged, bump both version fields again. A failed release job can be rerun against its original commit; it repairs the existing draft without duplicating it. The workflow can also be run manually from the Actions tab on `main`.

## Build downloads

Every successful CI run saves APKs under the `android-builds` artifact and validation reports under `validation-reports`. The CI debug APK uses a temporary runner certificate; use the **release APK attached to the draft/release** for in-place updates to the installed app. Release APKs are not debuggable, but retain the existing development signing certificate for compatibility.

## Signing and repository maintenance

The release job uses these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`
- `ANDROID_KEY_ALIAS`

Configure these secrets using the existing local Android keystore to preserve update compatibility. Signing material is never committed to Git. Preserve a secure backup: replacing the certificate prevents in-place updates. The workflow verifies the certificate fingerprint before attaching any APK. Without these secrets, build validation still runs, but the release job stops with a setup message.

Pull-request builds do not receive signing secrets or release-write permissions. Signing runs only after successful checks on `main`. Third-party workflow actions are pinned to commit hashes; Dependabot proposes monthly action updates. The Gradle wrapper, build tools, and PDF verification dependency are pinned too.

No open-source license has been selected yet; repository visibility alone does not grant a license.
