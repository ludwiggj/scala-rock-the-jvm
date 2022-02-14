Most of the code in this repo taken from [Learn Doobie for the Greater Good](https://blog.rockthejvm.com/doobie/).

I used the [official Postgres docker image](https://hub.docker.com/_/postgres). I created stack.yml in same directory as this readme
file, then from a command prompt in this directory:

```
docker-compose -f stack.yml up
```

Once initialisation is complete, postgres is available via [localhost, port 8080](http://localhost:8080).

I created the initial schema and dataload for the [Learn Doobie for the Greater Good](https://blog.rockthejvm.com/doobie/)
exercises via the [postgres web interface](http://localhost:8080) (SQL command option).

Example writing query results to a file based on [Save Doobie stream from database to file](https://stackoverflow.com/questions/60569610/save-doobie-stream-from-database-to-file).

Other links:

* [Book of Doobie](https://tpolecat.github.io/doobie/docs/index.html)
* [Streaming all the way with ZIO, Doobie, Quill, http4s and fs2](https://juliano-alves.com/2020/06/15/streaming-all-the-way-zio-doobie-quill-http4s-fs2/)
* [Sample film database](https://www.postgresqltutorial.com/postgresql-sample-database/) - not used, but may be useful in the future.