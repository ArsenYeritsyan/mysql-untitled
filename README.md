# mysql-untitled

Simple Java utility to connect to MySQL and run a health check (SELECT 1).

Build
- mvn -q -DskipTests package

Run (env vars)
- MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_DB=mysql MYSQL_USER=root MYSQL_PASSWORD=secret \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main

Or set MYSQL_URL with a full JDBC URL.

Default health check query
- By default, the app runs: SELECT 1 and expects the result to be 1.

Make it configurable via env vars
- MYSQL_HEALTHCHECK_QUERY: override the query to run. Default: SELECT 1
- MYSQL_HEALTHCHECK_EXPECT: optional expected value for the first column of the first row.
  - If set, the app compares the first column to this value (string or numeric match).
  - If not set and the query is SELECT 1, the app expects 1 (backward-compatible behavior).
  - If not set and you provide a custom query, any returned row counts as success.

Examples
- Custom query expecting a specific value:
  MYSQL_HEALTHCHECK_QUERY="SELECT COUNT(*) FROM your_table" \
  MYSQL_HEALTHCHECK_EXPECT=0 \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main

- Custom query where any row is success (no expected value provided):
  MYSQL_HEALTHCHECK_QUERY="SELECT NOW()" \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main
