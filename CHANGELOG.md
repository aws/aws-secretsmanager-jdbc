# Changelog

### Release 1.0.2 (May 28, 2018)
* Add support for MariaDB
* For MySQL, check for com.mysql.cj.jdbc.Driver in class path and fall back to com.mysql.jdbc.Driver
* Change JSON parsing error message to be more generic

### Release 1.0.1 (December 5, 2018)
* Fixed an issue with the way JDBC URLs are handled:
  * acceptsURL() now returns false if the URL is a JDBC URL that does not begin with jdbc-secretsmanager
  * connect() returns null if the URL parameter is not one we accept
* Updated jackson-databind dependency to 2.8.11.1
