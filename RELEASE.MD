In order to release a version the following actions are needed:

1. **Version and docs**
   - Set the new version (e.g. `3.1.1`) in:
     - `package.json` (`version` field)
     - `plugin.xml` (root `<plugin>` attribute `version`)
   - Add a release entry in `CHANGELOG.md` (date, Added/Fixed/Changed/Documentation).
   - Update `README.md` if needed (e.g. "New in X.Y.Z", example version strings).

2. **Lock file**
   - Run `npm install` so `package-lock.json` stays in sync with `package.json`.

3. **Git tag and release**
   - Go to [Releases → Create a new release](https://github.com/josuelmm/cordova-background-geolocation/releases/new).
   - Create a tag with the version name (e.g. `v3.1.1`).
   - Publish the release. GitHub Actions will publish the package to npm.