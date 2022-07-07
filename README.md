This repo contains code from various blogs taken from the Rock the JVM Blog. Many of the examples build on previous
ones. Specifically:

(1) [Learn Doobie for the Greater Good](https://blog.rockthejvm.com/doobie/).

The code is run against the [official Postgres docker image](https://hub.docker.com/_/postgres), as specified in the
`stack.yml` file, located in same directory as this readme file. To start the database, open a command prompt in this
directory and run the following command:

```
docker-compose -f stack.yml up
```

Once initialisation is complete, postgres is available via [localhost, port 8080](http://localhost:8080).

The initial schema and dataload for the [Learn Doobie for the Greater Good](https://blog.rockthejvm.com/doobie/)
exercises were created via the [postgres web interface](http://localhost:8080) (SQL command option).

The example that writes query results to a file is based on
[Save Doobie stream from database to file](https://stackoverflow.com/questions/60569610/save-doobie-stream-from-database-to-file).

Other links:

* [Book of Doobie](https://tpolecat.github.io/doobie/docs/index.html)
* [Streaming all the way with ZIO, Doobie, Quill, http4s and fs2](https://juliano-alves.com/2020/06/15/streaming-all-the-way-zio-doobie-quill-http4s-fs2/)
* [Sample film database](https://www.postgresqltutorial.com/postgresql-sample-database/) - not used, but may be useful in the future.

(2) [FS2 Tutorial: More than Functional Streaming in Scala](https://blog.rockthejvm.com/fs2/)

(3) [Unleashing the Power of HTTP Apis: The Http4s Library](https://blog.rockthejvm.com/http4s-tutorial/)

