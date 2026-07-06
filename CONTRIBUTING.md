## How To Contribute
 
#### Basics

Pull requests and bug reports are welcomed.  For enhancement pull requests, please ask first to save time!  It's possible the proposed enhancement is outside the scope or design goals of the project.

#### Pushing to Maven Central

Contact Mark Allen at mark@revetware.com to request Central Portal deployment access.

Once granted, make sure your ```~/.m2/settings.xml``` file has ```central-portal``` entries:

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
  <profiles>
    <profile>
      <id>central-portal</id>
      <properties>
        <gpg.passphrase>YOUR_PASSPHRASE_HERE</gpg.passphrase>
      </properties>
    </profile>    
  </profiles>
</settings>
```

You can then push to Maven Central:

```
$ mvn clean deploy -Prelease -Dgpg.passphrase=YOUR_PASSPHRASE
```

The release profile is only needed when publishing signed release artifacts. Normal contributor builds should use `mvn clean verify` and do not require Central Portal or GPG credentials.
