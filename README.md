# AWS Secrets Manager JDBC Library

[![Java Build](https://github.com/aws/aws-secretsmanager-jdbc/actions/workflows/CI.yml/badge.svg?event=push)](https://github.com/aws/aws-secretsmanager-jdbc/actions/workflows/CI.yml)
[![Coverage](https://codecov.io/gh/aws/aws-secretsmanager-jdbc/branch/master/graph/badge.svg?token=hCl7eBaSwn)](https://codecov.io/gh/aws/aws-secretsmanager-jdbc)

The **AWS Secrets Manager JDBC Library** enables Java developers to easily connect to SQL databases using secrets stored in AWS Secrets Manager.

## License

This library is licensed under the Apache 2.0 License.

## Features

* Provides wrappers to common JDBC drivers enabling simple database connectivity
* Provides database connection pooling support through c3p0

## Building from Source

After you've downloaded the code from GitHub, you can build it using Maven. To disable GPG signing in the build, use this command: `mvn clean install -Dgpg.skip=true`

## Usage
The recommended way to use the SQL Connection Library is to consume it from Maven.  The latest released version can be found at: https://mvnrepository.com/artifact/com.amazonaws.secretsmanager/aws-secretsmanager-jdbc

``` xml
<dependency>
    <groupId>com.amazonaws.secretsmanager</groupId>
    <artifactId>aws-secretsmanager-jdbc</artifactId>
    <version>1.0.8</version>
</dependency>
```

To use the latest build (pre-release), don't forget to enable the download of snapshot jars from Maven.

``` xml
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
We provide database drivers that intercept calls to real database drivers and swap out secret IDs for actual login credentials.
This prevents hard-coding database credentials into your application code. This can be integrated into your app through a few
configuration file changes. Here is an example for making this work with your c3p0 config:

```properties
# c3p0.properties

# MySQL example
c3p0.user=secretId
c3p0.driverClass=com.amazonaws.secretsmanager.sql.AWSSecretsManagerMySQLDriver
c3p0.jdbcUrl=jdbc-secretsmanager:mysql://example.com:3306

# PostgreSQL example
# c3p0.user=secretId
# c3p0.driverClass=com.amazonaws.secretsmanager.sql.AWSSecretsManagerPostgreSQLDriver
# c3p0.jdbcUrl=jdbc-secretsmanager:postgresql://example.com:5432/database

# Oracle example
# c3p0.user=secretId
# c3p0.driverClass=com.amazonaws.secretsmanager.sql.AWSSecretsManagerOracleDriver
# c3p0.jdbcUrl=jdbc-secretsmanager:oracle:thin:@example.com:1521/ORCL

# MSSQLServer example
# c3p0.user=secretId
# c3p0.driverClass=com.amazonaws.secretsmanager.sql.AWSSecretsManagerMSSQLServerDriver
# c3p0.jdbcUrl=jdbc-secretsmanager:sqlserver://example.com:1433
```

The only changes that need to happen in the c3p0 config are to:

* change the jdbc url to one that our driver will intercept (starting with jdbc-secretsmanager),
* change the c3p0 user to be the secret ID of the secret in secrets manager that has the username and password,
* and change the `driverClass` to be our driver wrapper.

The secret being used should be in the JSON format we use for our rotation lambdas for RDS databases. E.g:

```json
{
	"username": "user",
	"password": "pass",
	...
}
```

