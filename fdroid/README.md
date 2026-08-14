# Self-hosted F-Droid repository

This folder will become the static content deployed to Cloudflare Pages.

## Intended release workflow

1. Create a reproducible signed release APK from a tagged source revision.
2. Place the signed APK in `fdroid/repo/` locally or in a protected release workflow.
3. Run `fdroid update` using a protected repository signing key.
4. Deploy the resulting `fdroid/` directory to Cloudflare Pages.

Do not commit `repo/`, generated index files, private keystores, release keys, Cloudflare API credentials, or APKs. Those assets belong in protected CI secrets or offline release storage.

A self-hosted repo should be treated like a release channel: publish a clear fingerprint, preserve the signing key, and test updates from a clean F-Droid client before announcing it.
