# Releasing

Artifacts go to Maven Central through the [Central Portal](https://central.sonatype.com). The legacy
OSSRH staging of `oss.sonatype.org` was decommissioned in 2025, which is why the build uses
`central-publishing-maven-plugin` instead of `nexus-staging-maven-plugin`.

## One time setup

### 1. Claim the `net.integrio` namespace
Nothing has ever been published under `net.integrio`, so the namespace is free to claim.

1. Sign in to the [Central Portal](https://central.sonatype.com) and open **Namespaces**
2. **Add Namespace** → `net.integrio` → the portal shows a verification code
3. Add the code as a TXT record of `integrio.net`. The zone lives in Route 53 and the apex already
   holds SPF and the verification records of Google and Facebook, so the record has to be **upserted
   with all existing values**, otherwise mail delivery breaks:

   ```bash
   aws route53 list-resource-record-sets --hosted-zone-id <ZONE_ID> \
     --query "ResourceRecordSets[?Name=='integrio.net.'&&Type=='TXT']"
   # add the code to the values of that rrset, then
   aws route53 change-resource-record-sets --hosted-zone-id <ZONE_ID> \
     --change-batch file://change-batch.json
   ```

4. Wait for propagation (`dig +short TXT integrio.net`) and press **Verify Namespace**

The releases up to 1.0.2 sit under the `com.github.minordima` namespace, which belongs to an account
that is no longer used. They stay where they are; 1.1.0 and later live under `net.integrio`.

### 2. Create a signing key
Central rejects unsigned artifacts and looks the public key up on a keyserver.

```bash
gpg --full-generate-key                     # rsa4096, minor@integrio.net
gpg --list-secret-keys --keyid-format long  # note the key id
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
```

Keep the passphrase in a password manager, not in a file next to the build. Back the secret key up
(`gpg --export-secret-keys --armor <KEY_ID>`): the key of the 2020 release is gone, which is why
1.1.0 needs a new one.

### 3. Store the portal token
**Account** → **Generate User Token** in the portal, then in `~/.m2/settings.xml`:

```xml
<server>
  <id>central</id>
  <username>TOKEN_USERNAME</username>
  <password>TOKEN_PASSWORD</password>
</server>
```

The id has to stay `central`, that is what `publishingServerId` in the `pom.xml` points at.

## Releasing a version

```bash
mvn clean verify                 # signs, so the passphrase is asked for here
mvn deploy                       # uploads the bundle and waits for validation
```

The deployment then waits in the portal under **Deployments**. Check it and press **Publish** —
`autoPublish` is deliberately off in the `pom.xml`, because a version that reached Maven Central can
never be replaced. Set it to `true` once the pipeline is trusted.

Publishing is usually visible in Central within 15 minutes and on `repo1.maven.org` within a few
hours.

## Notes

* Local builds that should not ask for the signing passphrase: `mvn install -Dgpg.skip=true`
* Only releases are supported. The portal does not host snapshots, so a snapshot repository has to
  live somewhere else, for example in a bucket served by this wagon
* Should `gpg` fail to ask for the passphrase in a non interactive shell: `export GPG_TTY=$(tty)`
