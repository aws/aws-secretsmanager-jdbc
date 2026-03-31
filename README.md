# AWS Secrets Manager JDBC Library

[![Java Build](https://github.com/aws/aws-secretsmanager-jdbc/actions/workflows/CI.yml/badge.svg?event=push)](https://github.com/aws/aws-secretsmanager-jdbc/actions/workflows/CI.yml)
[![Coverage](https://codecov.io/gh/aws/aws-secretsmanager-jdbc/branch/v2/graph/badge.svg?token=hCl7eBaSwn)](https://codecov.io/gh/aws/aws-secretsmanager-jdbc)

The **AWS Secrets Manager JDBC Library** enables Java developers to easily connect to SQL databases using secrets stored in AWS Secrets Manager.

## License

This library is licensed under the Apache 2.0 License.

## Features

- Provides wrappers to common JDBC drivers enabling simple database connectivity
- Provides database connection pooling support through c3p0

## Building from Source

After you've downloaded the code from GitHub, you can build it using Maven.

- To disable GPG signing in the build, use this command: `mvn clean install -Dgpg.skip=true`
- To build the default (non-shaded) JAR, use this command: `mvn clean install`
- To build the shaded (uber) JAR with all dependencies included, use this command: `mvn clean install -Pshade`
  The shaded JAR will be generated in the `target/` directory with the `-shaded` classifier, e.g.: `target/aws-secretsmanager-jdbc-2.0.3-shaded.jar`

## Usage

The recommended way to use the SQL Connection Library is to consume it from Maven. The latest released version can be found at: https://mvnrepository.com/artifact/com.amazonaws.secretsmanager/aws-secretsmanager-jdbc

```xml
<dependency>
    <groupId>com.amazonaws.secretsmanager</groupId>
    <artifactId>aws-secretsmanager-jdbc</artifactId>
    <version>2.0.0</version>
</dependency>
```

To use the latest build (pre-release), don't forget to enable the download of snapshot jars from Maven.

```xml
<profiles>
  <profile>
    <id>allow-snapshots</id>
    <activation><activeByDefault>true</activeByDefault></activation>
    <repositories>
      <repository>
        <id>snapshots-repo</id>
        <url>https://aws.oss.sonatype.org/content/repositories/snapshots</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
      </repository>
    </repositories>
  </profile>
</profiles>
```

### Usage Example

We provide database drivers that intercept calls to real database drivers and replace secret IDs with actual login credentials.
This prevents hard-coding database credentials into your application code.

The following is an example which uses the secret to resolve both the endpoint and the login credentials.

```
// Load the JDBC driver
Class.forName( "com.amazonaws.secretsmanager.sql.AWSSecretsManagerPostgreSQLDriver" ).newInstance();

// Retrieve the connection info from the secret using the secret ARN
String URL = "secretId";

// Populate the user property with the secret ARN to retrieve user and password from the secret
Properties info = new Properties( );
info.put( "user", "secretId" );

// Establish the connection
conn = DriverManager.getConnection(URL, info);
```

To specify a custom endpoint and port instead of resolving from the secret, use the jdbc-secretsmanager prefix with your database information.

```
// Options to resolve the connection information

// Set url to secret arn to resolve endpoint and port from secret
String URL = "secretId";

// Use jdbc-secretsmanager prefix to specify endpoint and port instead of resolving from secret
String URL = "jdbc-secretsmanager:postgresql://example.com:5432/database";
```

The secret should be in the correct JSON format. For more information, see the [AWS Secrets Manager documentation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/reference_secret_json_structure). For example:

```json
{
  "host": "<host name>",
  "username": "<username>",
  "password": "<password>",
  "dbname": "<database name>",
  "port": "<port number>"
}
```

We support a variety of drivers. For more information, see the [AWS Secrets Manager JDBC documentation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_jdbc).

## Credentials

This library uses the [Default Credential Provider Chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html). The following options exist to override some of the defaults:

1. Set a PrivateLink DNS endpoint URL and a region in the secretsmanager.properties file:

```text
drivers.vpcEndpointUrl= #The endpoint URL
drivers.vpcEndpointRegion= #The endpoint region
```

2. Override the primary region by setting the 'AWS_SECRET_JDBC_REGION' environment variable to the preferred region, or via the secretsmanager.properties file:

```text
drivers.region= #The region to use.
```

3. Enable Post-Quantum TLS (PQTLS) by setting the following in the secretsmanager.properties file:

```text
drivers.postQuantumTlsEnabled=true
```
For more information about Post-Quantum TLS in the AWS SDK, see the [AWS SDK for Java documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration.html#post-quantum-tls).

**NOTE**: PQ-TLS uses the AWS Common Runtime (CRT) which relies on system libraries and may not work as expected on macOS or Windows at this time ([ref](https://github.com/awslabs/aws-crt-java#tls-behavior)).

If this driver is running on EKS, the library could pick up the credentials of the node it is running on instead of the service account role ([issue](https://github.com/aws/aws-secretsmanager-jdbc/issues/55)). To address this, add version `2` of `software.amazon.awssdk:sts` to your Gradle/Maven project file as a dependency.
