# mysql-untitled

Simple Java utility to connect to MySQL and run a health check (SELECT 1 by default).

Build
- mvn -q -DskipTests package

Run (env vars)
- MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_DB=mysql MYSQL_USER=root MYSQL_PASSWORD=secret \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main

Or set MYSQL_URL with a full JDBC URL.

Connection reliability and timeouts
- MYSQL_CONNECT_TIMEOUT_MS: JDBC connectTimeout in ms (default: 5000)
- MYSQL_SOCKET_TIMEOUT_MS: JDBC socketTimeout in ms (default: 10000)
- MYSQL_RETRIES: number of connection/health-check attempts (default: 5)
- MYSQL_RETRY_DELAY_MS: delay between attempts in ms (default: 1000)
- MYSQL_WAIT_FOR_HOST: if "true", preflight wait for TCP on host:port before JDBC (default: false)
- MYSQL_WAIT_TIMEOUT_MS: total time to wait for host:port when preflight is enabled (default: 30000)

Default health check query
- By default, the app runs: SELECT 1 and expects the result to be 1.

Make it configurable via env vars
- MYSQL_HEALTHCHECK_QUERY: override the query to run. Default: SELECT 1
- MYSQL_HEALTHCHECK_EXPECT: optional expected value for the first column of the first row.
  - If set, the app compares the first column to this value (string or numeric match).
  - If not set and the query is SELECT 1, the app expects 1 (backward-compatible behavior).
  - If not set and you provide a custom query, any returned row counts as success.

Examples
- Robust startup against slow DB container:
  MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_DB=mysql MYSQL_USER=root MYSQL_PASSWORD=secret \
  MYSQL_RETRIES=20 MYSQL_RETRY_DELAY_MS=1500 MYSQL_WAIT_FOR_HOST=true MYSQL_WAIT_TIMEOUT_MS=60000 \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main

- Custom query expecting a specific value:
  MYSQL_HEALTHCHECK_QUERY="SELECT COUNT(*) FROM your_table" \
  MYSQL_HEALTHCHECK_EXPECT=0 \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main

- Custom query where any row is success (no expected value provided):
  MYSQL_HEALTHCHECK_QUERY="SELECT NOW()" \
  java -cp target/mysql-untitled-1.0-SNAPSHOT.jar:~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar org.example.Main

Troubleshooting Communications link failure
- Ensure the MySQL server is running and listening on the expected host:port.
- Verify network/firewall/Docker settings allow connectivity from this host to the MySQL port.
- Increase MYSQL_CONNECT_TIMEOUT_MS and/or MYSQL_SOCKET_TIMEOUT_MS if the network is slow.
- Use MYSQL_RETRIES and MYSQL_RETRY_DELAY_MS to handle cold starts.
- Consider providing a full MYSQL_URL if you use non-default parameters (SSL, auth plugins, etc.).
