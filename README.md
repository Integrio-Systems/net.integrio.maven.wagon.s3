# Integrio Maven Wagon



## Description
This project is a fork of a [Maven Wagon](https://github.com/spring-projects/aws-maven) for [Amazon S3](http://aws.amazon.com/s3/).  In order to to publish artifacts to an S3 bucket, the user (as identified by their access key) must be listed as an owner on the bucket.

Next to `s3://` urls the extension serves `cos://` urls backed by [IBM Cloud Object Storage](#ibm-cloud-object-storage).


## Why this fork?
- original repo not maintained for a long time but we updated fork to the latest libs.
- we fixed some of issues that blocks others and us.
- no support from maintainers of original repo. 


## Usage
To publish Maven artifacts to S3 a build extension must be defined in a project's `pom.xml`.  The latest version of the wagon can be found on the [`net.integrio:maven.wagon`](https://central.sonatype.com/artifact/net.integrio/maven.wagon) page in Maven Central.

```xml
<project>
  ...
  <build>
    ...
    <extensions>
      ...
      <extension>
        <groupId>net.integrio</groupId>
        <artifactId>maven.wagon</artifactId>
        <version>1.1.1</version>
      </extension>
      ...
    </extensions>
    ...
  </build>
  ...
</project>
```

Once the build extension is configured distribution management repositories can be defined in the `pom.xml` with an `s3://` scheme.

```xml
<project>
  ...
  <distributionManagement>
    <repository>
      <id>aws-release</id>
      <name>AWS Release Repository</name>
      <url>s3://<BUCKET>/release</url>
    </repository>
    <snapshotRepository>
      <id>aws-snapshot</id>
      <name>AWS Snapshot Repository</name>
      <url>s3://<BUCKET>/snapshot</url>
    </snapshotRepository>
  </distributionManagement>
  ...
</project>
```

Finally the `~/.m2/settings.xml` must be updated to include access and secret keys for the account. The access key should be used to populate the `username` element, and the secret access key should be used to populate the `password` element.

```xml
<settings>
  ...
  <servers>
    ...
    <server>
      <id>aws-release</id>
      <username>0123456789ABCDEFGHIJ</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCD</password>
      <configuration>
        <wagonProvider>s3</wagonProvider>
      </configuration>
    </server>
    <server>
      <id>aws-snapshot</id>
      <username>0123456789ABCDEFGHIJ</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCD</password>
      <configuration>
        <wagonProvider>s3</wagonProvider>
      </configuration>
    </server>
    ...
  </servers>
  ...
</settings>
```

### Connecting through a Proxy
For being able to connect behind an HTTP proxy you need to add the following configuration to `~/.m2/settings.xml`:

```xml
<settings>
  ...
  <proxies>
     ...
     <proxy>
         <active>true</active>
         <protocol>s3</protocol>
         <host>myproxy.host.com</host>
         <port>8080</port>
         <username>proxyuser</username>
         <password>somepassword</password>
         <nonProxyHosts>www.google.com|*.somewhere.com</nonProxyHosts>
     </proxy>
     ...
    </proxies>
  ...
</settings>
```

Alternatively, the access and secret keys for the account can be provided using (applied in order below)

* `aws.accessKeyId` and `aws.secretKey` [system properties](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/SystemPropertiesCredentialsProvider.html)
* `AWS_ACCESS_KEY_ID` (or `AWS_ACCESS_KEY`) and `AWS_SECRET_KEY` (or `AWS_SECRET_ACCESS_KEY`) [environment variables](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/EnvironmentVariableCredentialsProvider.html)
* `aws_access_key_id` and `aws_secret_access_key` of [aws cli](https://docs.aws.amazon.com/cli/latest/userguide/cli-config-files.html)
* The Amazon EC2 [Instance Metadata Service](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/EC2ContainerCredentialsProviderWrapper.html)

## IBM Cloud Object Storage
The same build extension also provides a wagon for [IBM Cloud Object Storage](https://www.ibm.com/products/cloud-object-storage) (COS) under the `cos://` scheme. It uses the official IBM COS SDK, so both ways of authenticating against COS work: an **IAM API key** and **HMAC credentials**.

The walkthrough below deploys artifacts into an existing bucket. The wagon never creates buckets, so create the bucket up front in the [console](https://cloud.ibm.com/objectstorage) or with the CLI.

### Step 1: get credentials
In the console open your Cloud Object Storage instance, go to **Service credentials** and create a new credential with the role `Writer` (or `Manager`). Switch **Include HMAC Credential** on if you prefer HMAC over IAM. The credential looks like this:

```json
{
  "apikey": "0123456789abcdefghijklmnopqrstuvwxyzABCDEFG",
  "resource_instance_id": "crn:v1:bluemix:public:cloud-object-storage:global:a/<ACCOUNT>:<INSTANCE>::",
  "cos_hmac_keys": {
    "access_key_id": "0123456789abcdef0123456789abcdef",
    "secret_access_key": "0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
```

Keep either the `apikey` or the two `cos_hmac_keys` at hand — one of them is enough.

### Step 2: find the endpoint of the bucket
A bucket is only reachable through the endpoint of its region. In the console open the bucket, go to **Configuration** and copy the public endpoint, or look it up in the [list of endpoints](https://cloud.ibm.com/docs/cloud-object-storage?topic=cloud-object-storage-endpoints). Public endpoints of regional buckets look like this:

```
s3.us-south.cloud-object-storage.appdomain.cloud
```

Use `s3.private.<REGION>.cloud-object-storage.appdomain.cloud` from inside IBM Cloud and `s3.direct.<REGION>.cloud-object-storage.appdomain.cloud` for direct links — the wagon handles all three.

### Step 3: add the build extension
```xml
<project>
  ...
  <build>
    ...
    <extensions>
      ...
      <extension>
        <groupId>net.integrio</groupId>
        <artifactId>maven.wagon</artifactId>
        <version>1.1.1</version>
      </extension>
      ...
    </extensions>
    ...
  </build>
  ...
</project>
```

### Step 4: point the repositories at the bucket
The host of the url is the bucket, with the endpoint of step 2 appended to it, and the path is the prefix the artifacts are stored under:

```xml
<project>
  ...
  <distributionManagement>
    <repository>
      <id>cos-release</id>
      <name>IBM COS Release Repository</name>
      <url>cos://<BUCKET>.s3.us-south.cloud-object-storage.appdomain.cloud/release</url>
    </repository>
    <snapshotRepository>
      <id>cos-snapshot</id>
      <name>IBM COS Snapshot Repository</name>
      <url>cos://<BUCKET>.s3.us-south.cloud-object-storage.appdomain.cloud/snapshot</url>
    </snapshotRepository>
  </distributionManagement>
  ...
</project>
```

Keeping the endpoint out of the url is supported as well: use `cos://<BUCKET>/release` and configure the `endpoint` on the server of step 5. That keeps the `pom.xml` free of the region, which helps when the same project is deployed to buckets in different regions.

### Step 5: put the credentials into `~/.m2/settings.xml`
The `id` of the `server` has to match the `id` of the repository. With an **IAM API key** the user name is the literal `apikey` and the password is the API key:

```xml
<settings>
  ...
  <servers>
    ...
    <server>
      <id>cos-release</id>
      <username>apikey</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCDEFG</password>
      <configuration>
        <wagonProvider>cos</wagonProvider>
      </configuration>
    </server>
    <server>
      <id>cos-snapshot</id>
      <username>apikey</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCDEFG</password>
      <configuration>
        <wagonProvider>cos</wagonProvider>
      </configuration>
    </server>
    ...
  </servers>
  ...
</settings>
```

With **HMAC credentials** the `access_key_id` goes into `username` and the `secret_access_key` into `password`, exactly like the S3 wagon above. Any user name other than `apikey`, `iam`, `iam-apikey` or `ibm-apikey` is treated as an HMAC access key id.

Passwords can be encrypted with [`mvn --encrypt-password`](https://maven.apache.org/guides/mini/guide-encryption.html) as in any other `server` entry.

If the url of step 4 carries no endpoint, add it here:

```xml
<server>
  <id>cos-release</id>
  <username>apikey</username>
  <password>0123456789abcdefghijklmnopqrstuvwxyzABCDEFG</password>
  <configuration>
    <wagonProvider>cos</wagonProvider>
    <endpoint>s3.us-south.cloud-object-storage.appdomain.cloud</endpoint>
  </configuration>
</server>
```

### Step 6: deploy
```
$ mvn deploy
...
[INFO] Uploading to cos-release: cos://my-bucket/release/net/integrio/demo/1.0.0/demo-1.0.0.pom
[INFO] Uploaded to cos-release: cos://my-bucket/release/net/integrio/demo/1.0.0/demo-1.0.0.pom (1.1 kB at 4.3 kB/s)
[INFO] Uploading to cos-release: cos://my-bucket/release/net/integrio/demo/1.0.0/demo-1.0.0.jar
[INFO] Uploaded to cos-release: cos://my-bucket/release/net/integrio/demo/1.0.0/demo-1.0.0.jar (2.1 kB at 306 kB/s)
[INFO] BUILD SUCCESS
```

Add `-X` to see which endpoint, bucket and region the wagon resolved.

### Consuming artifacts from the bucket
A project that reads from the bucket needs the same build extension of step 3, a `repository` instead of a `distributionManagement` entry, and the matching `server` of step 5:

```xml
<project>
  ...
  <repositories>
    <repository>
      <id>cos-release</id>
      <url>cos://<BUCKET>.s3.us-south.cloud-object-storage.appdomain.cloud/release</url>
    </repository>
  </repositories>
  ...
</project>
```

A `Reader` role is enough for this. If the bucket is public, artifacts can also be resolved over plain `https://<ENDPOINT>/<BUCKET>/release` with no credentials and no extension at all.

### Configuration reference
Everything except the credentials can be set in the `<configuration>` of the server, as a system property or as an environment variable, in that order of precedence. An endpoint in the url of the repository wins over all of them.

| `<configuration>`   | System property             | Environment variable       | Description
| ------------------- | --------------------------- | -------------------------- | -----------
| `endpoint`          | `ibm.cos.endpoint`          | `IBM_COS_ENDPOINT`         | Service endpoint, for example `s3.eu-de.cloud-object-storage.appdomain.cloud`. Prefix it with `http://` to talk to an endpoint without TLS
| `region`            | `ibm.cos.region`            | `IBM_COS_REGION`           | Region of the bucket, for example `eu-de`. Used to build the public endpoint when none is configured, and as the region requests are signed for. Defaults to the region of the endpoint
| `serviceInstanceId` | `ibm.cos.serviceInstanceId` | `IBM_SERVICE_INSTANCE_ID`  | The `resource_instance_id` of the credential. Only needed for creating buckets, not for deploying artifacts into an existing one
| `iamEndpoint`       | `ibm.cos.iamEndpoint`       | `IBM_AUTH_ENDPOINT`        | Token endpoint of IAM. Defaults to `https://iam.cloud.ibm.com/identity/token`
| `pathStyleAccess`   |                             |                            | `false` addresses the bucket as a subdomain of the endpoint instead of as the first path segment. Defaults to `true`

Credentials can be provided outside of `settings.xml` too, which is what a build server usually does. The HMAC keys carry an `ibm.cos` / `IBM_COS_` prefix, so the keys of a COS bucket never get mixed up with the keys of an AWS account when the same build deploys to both. They are applied in the order below:

| Credential                | System property           | Environment variable          |
| ------------------------- | ------------------------- | ----------------------------- |
| IAM API key               | `ibm.cos.apiKey`          | `IBM_API_KEY_ID`              |
| HMAC `access_key_id`      | `ibm.cos.accessKeyId`     | `IBM_COS_ACCESS_KEY_ID`       |
| HMAC `secret_access_key`  | `ibm.cos.secretAccessKey` | `IBM_COS_SECRET_ACCESS_KEY`   |

The system property wins over the environment variable of the same credential, and an API key wins over HMAC keys. Both halves of an HMAC pair have to be set — one without the other fails the build with the name of the missing half instead of quietly reaching for the AWS keys.

For backwards compatibility the unprefixed AWS names are still read for HMAC, after all of the above: the `aws.accessKeyId` and `aws.secretKey` system properties, then the `AWS_ACCESS_KEY_ID` (or `AWS_ACCESS_KEY`) and `AWS_SECRET_KEY` (or `AWS_SECRET_ACCESS_KEY`) environment variables. Prefer the prefixed names in new setups.

So a build server needs no `settings.xml` at all when the repository url carries the endpoint:

```bash
export IBM_API_KEY_ID=0123456789abcdefghijklmnopqrstuvwxyzABCDEFG
mvn deploy
```

Or with HMAC credentials:

```bash
export IBM_COS_ACCESS_KEY_ID=0123456789abcdef0123456789abcdef
export IBM_COS_SECRET_ACCESS_KEY=0123456789abcdef0123456789abcdef0123456789abcdef
mvn deploy
```

### Connecting through a proxy
Proxies are taken from the `<proxies>` of `~/.m2/settings.xml` with `<protocol>cos</protocol>`, and they are used for the requests to IAM as well:

```xml
<settings>
  ...
  <proxies>
     ...
     <proxy>
         <active>true</active>
         <protocol>cos</protocol>
         <host>myproxy.host.com</host>
         <port>8080</port>
         <username>proxyuser</username>
         <password>somepassword</password>
     </proxy>
     ...
    </proxies>
  ...
</settings>
```

### Troubleshooting

| Symptom | Cause
| ------- | -----
| `No IBM Cloud Object Storage endpoint configured for ...` | Neither the url nor the server configuration names an endpoint or a region, see step 2
| `Unable to load IBM Cloud Object Storage credentials ...` | No `server` matches the `id` of the repository, or its password is empty
| `403 Forbidden`, `AccessDenied` | The credential lacks a role on the bucket. Deploying needs `Writer` or `Manager`, resolving needs `Reader`
| `403 Forbidden`, `SignatureDoesNotMatch` | An HMAC secret that does not belong to the access key id, or an API key that ended up in an HMAC `username`/`password` pair instead of below `apikey`
| `404 Not Found`, `NoSuchBucket` | The bucket does not exist, or the endpoint belongs to another region than the bucket
| Certificate errors for a bucket that contains dots | Only happens with `pathStyleAccess` turned off, remove it to get the default back

## Making Artifacts Public
This wagon doesn't set an explict ACL for each artifact that is uploaded. Instead you should create an AWS Bucket Policy to set permissions on objects. A bucket policy can be set in the [AWS Console](https://console.aws.amazon.com/s3) and can be generated using the [AWS Policy Generator](http://awspolicygen.s3.amazonaws.com/policygen.html).

In order to make the contents of a bucket public you need to add statements with the following details to your policy:

| Effect  | Principal | Action       | Amazon Resource Name (ARN)
| ------- | --------- | ------------ | --------------------------
| `Allow` | `*`       | `ListBucket` | `arn:aws:s3:::<BUCKET>`
| `Allow` | `*`       | `GetObject`  | `arn:aws:s3:::<BUCKET>/*`

If your policy is setup properly it should look something like:

```json
{
  "Id": "Policy1397027253868",
  "Statement": [
    {
      "Sid": "Stmt1397027243665",
      "Action": [
        "s3:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::<BUCKET>",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    },
    {
      "Sid": "Stmt1397027177153",
      "Action": [
        "s3:GetObject"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::<BUCKET>/*",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    }
  ]
}
```

If you prefer to use the [command line](http://aws.amazon.com/documentation/cli/), you can use the following script to make the contents of a bucket public:

```bash
BUCKET=<BUCKET>
TIMESTAMP=$(date +%Y%m%d%H%M)
POLICY=$(cat<<EOF
{
  "Id": "public-read-policy-$TIMESTAMP",
  "Statement": [
    {
      "Sid": "list-bucket-$TIMESTAMP",
      "Action": [
        "s3:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::$BUCKET",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    },
    {
      "Sid": "get-object-$TIMESTAMP",
      "Action": [
        "s3:GetObject"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::$BUCKET/*",
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    }
  ]
}
EOF
)

aws s3api put-bucket-policy --bucket $BUCKET --policy "$POLICY"
```



## License

Copyright 2018-Present Platform Team.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
