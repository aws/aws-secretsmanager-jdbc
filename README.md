# AWS Secrets Manager JDBC Library

The **AWS Secrets Manager JDBC Library** enables Java developers to easily connect to SQL databases using secrets stored in AWS Secrets Manager.

## License

This library is licensed under the Apache 2.0 License.

## Features

* Provides wrappers to common JDBC drivers enabling simple database connectivity
* Support change cache item ttl using the environment variable or system property

## Building from Source

After you've downloaded the code from GitHub, you can build it using Maven. To disable GPG signing in the build, use this command: `mvn clean install -Dgpg.skip=true`

## Usage
The recommended way to use the SQL Connection Library is to consume it from Maven.  The latest released version can be found at: https://mvnrepository.com/artifact/io.github.kuraun/aws-secretsmanager-jdbc

``` xml
<dependency>
    <groupId>io.github.kuraun</groupId>
    <artifactId>aws-secretsmanager-jdbc</artifactId>
    <version>1.0.7</version>
</dependency>
```

### Usage Example
We provide database drivers that intercept calls to real database drivers and swap out secret IDs for actual login credentials.
This prevents hard-coding database credentials into your application code. This can be integrated into your app through a few
configuration file changes. Here is an example for making this work with your spring boot config:

* application.yml
```
spring:
  datasource:
    username: secretId
    url: jdbc-secretsmanager:mysql://127.0.0.1:3306/sakila?useUnicode=true&characterEncoding=utf8
    driverClassName: com.amazonaws.secretsmanager.sql.AWSSecretsManagerMySQLDriver


```
