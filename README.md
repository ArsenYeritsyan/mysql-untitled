# mysql-untitled

Simple Java utility to connect to MySQL and run a health check (SELECT 1).

Build
- mvn -q -DskipTests package

Run (env vars)
- MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_DB=mysql MYSQL_USER=root MYSQL_PASSWORD=secret \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main

Or set MYSQL_URL with a full JDBC URL.
