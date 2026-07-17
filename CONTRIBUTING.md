## How To Contribute
 
#### Basics

Pull requests and bug reports are welcomed.  For enhancement pull requests, please ask first to save time!  It's possible the proposed enhancement is outside the scope or design goals of the project.

#### Pushing to Maven Central

Contact Mark Allen at mark@revetware.com to request Central Portal deployment access.

Once granted, put the Central Portal tokens in `~/.m2/settings.xml`. The server IDs must match the IDs used by the
POM:

```xml
<settings>
  <servers>
    <server>
      <id>central-portal</id>
      <username>YOUR_CENTRAL_PORTAL_TOKEN_USERNAME_HERE</username>
      <password>YOUR_CENTRAL_PORTAL_TOKEN_PASSWORD_HERE</password>
    </server>
    <server>
      <id>central-portal-snapshots</id>
      <username>YOUR_CENTRAL_PORTAL_TOKEN_USERNAME_HERE</username>
      <password>YOUR_CENTRAL_PORTAL_TOKEN_PASSWORD_HERE</password>
    </server>
  </servers>
</settings>
```

Configure the release signing key with GnuPG and use `gpg-agent`/pinentry for passphrase entry. Do not put the signing
passphrase in this repository, `settings.xml`, a Maven `-D` argument, or an environment variable: command arguments
can be retained in shell history and exposed through process inspection.

Rehearse the complete signed build without uploading anything:

```shell
mvn -B -ntp -Prelease clean verify
```

The release profile intentionally refuses every version except exact `3.0.0`; it cannot be used while the POM still
reports `3.0.0-SNAPSHOT`. After the signed Java release commit exists, update the sibling website's generation lock
and run `npm run build && npm run verify:release` there. That coordinated gate checks the Java README and changelog,
generated website and AI documentation, canonical schema, clean source SHAs, build provenance, and versioned Javadocs
before the CI-produced deployment bundle is eligible to publish.

Inspect the generated artifacts and `.asc` signatures in `target/`. Once that exact staging flow succeeds from the
release commit, deploy with:

```shell
mvn -B -ntp -Prelease clean deploy
```

The `release` profile is only needed when building or publishing signed release artifacts. Normal contributor builds
should use `mvn -B -ntp clean verify` and do not require Central Portal or GPG credentials.
