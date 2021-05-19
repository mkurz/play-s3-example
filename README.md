# Debugging

Add a breakpoint to [this line](https://github.com/akka/akka-http/blob/v10.1.14/akka-http-core/src/main/scala/akka/http/impl/settings/ConnectionPoolSettingsImpl.scala#L72)

## Run the app

```
$ sbt -jvm-debug 9999 shell
Listening for transport dt_socket at address: 9999
[info] welcome to sbt 1.5.2 (AdoptOpenJDK Java 11.0.10)
...
[info] started sbt server
[s3-example] $ run 
```

The breakpoint will be triggered now. That is because the akka-http backend starts and [binds the address and ports](https://github.com/playframework/playframework/blob/2.8.8/transport/server/play-akka-http-server/src/main/scala/play/core/server/AkkaHttpServer.scala#L222-L229).
Actually it gets triggered by the line `Http()`, which implicitly takes `system` (which is the [dev-server's actor system](https://github.com/playframework/playframework/blob/2.8.8/transport/server/play-server/src/main/scala/play/core/server/DevServerStart.scala#L291)). What happens is that the "Http" extension gets registered in the passed actorsystem (if it hadn't been registered before), which leads to initialization of the Http extension (and therefore of the Connection Pool) with the config the (dev-server) actor system know about (and because in dev mode an applicaton was not started yet when binding the adress/ports the server does not know about application.conf yet).

## Download

```
$ curl http://127.0.0.1:9000/download/foo
```

The breakpoint will not be triggered. Meaning the config set in `application.conf` will not be used.
That is because in the action method we use `ok().chunked(source)` to return a chunked response. That chunked response **will be processed be the akka-http backend**, which, as we now know, uses the dev-server's actor system.
To be more accurate, the materializer that will be used is the one implicitly passed to [`bindAndHandleAsync(...)`](https://github.com/playframework/playframework/blob/2.8.8/transport/server/play-akka-http-server/src/main/scala/play/core/server/AkkaHttpServer.scala#L223-L229) when bootstrapping the server. There is no way to change that materializer later, it can only be set at server start.
And calling `S3.download(...)` in the action method [sets up a source](https://github.com/akka/alpakka/blob/v2.0.2/s3/src/main/scala/akka/stream/alpakka/s3/impl/S3Stream.scala#L118-L130) that will use that materializer.
From that materializer the [system is used](https://github.com/akka/alpakka/blob/v2.0.2/s3/src/main/scala/akka/stream/alpakka/s3/impl/S3Stream.scala#L332-L334) to make the actual request.
That is done by calling `Http()` in one of [these methods](https://github.com/akka/alpakka/blob/v2.0.2/s3/src/main/scala/akka/stream/alpakka/s3/impl/S3Stream.scala#L596-L600) which gets the system (retrieved from the materializer) again passed implicitly.
And now that the "Http" extension already was registered in the dev-mode actor system, it will just re-use that extension - so no need to re-initialize something (also it would not make a difference, application.conf would not be read anyway)

## Upload

```
$ echo "abc" > /tmp/foo.txt
$ curl -F "image=@/tmp/foo.txt" http://127.0.0.1:9000/upload
```

Here the breakpoint **does get** triggerd. That is because the body parser is a Play thing, not an akka-http thing. That means the body parser is set up by Play, the application. And for this it uses the application actor system.
akka-http does not know anything about body parser from Play. So that's why an upload/body parser will register the `Http` extension in the application actor system, which again initializes the connection pool, but this time it uses the config from the application actor system.
And because its the application actor system, it already know about the values in `application.conf`

# Note about the netty backend

If you use the netty backend, there will not be that problem. That is because netty does not need a materializer _at the time_ when binding ports, like akka-http does. Therefore when it processes a chunked response, [it will use the materializer from the application](https://github.com/playframework/playframework/blob/2.8.8/transport/server/play-netty-server/src/main/scala/play/core/server/netty/PlayRequestHandler.scala#L293-L296), which can make use of `application.conf`

# Solution?

You can use `PlayKeys.devSettings` in `build.sbt`.
No matter if you use the netty or the akka-http backend, you have to set the `play.server.*` configs there (in addition to application.conf) so the backend dev-mode's actor system picks them up.

If using akka-http, in `build.sbt` you also have to set any `akka.http.*` settings.
Maybe you sometimes have to set a `akka.*` key there (or `play.akka.dev-mode.akka.*`, which overrides the `akka.*` ones in dev mode) as well, if it somehow influences the behaviour of the akka-http server.

Example:

```
PlayKeys.devSettings ++= Seq(
  //"play.server.provider" -> "play.core.server.AkkaHttpServerProvider",
  //"play.server.provider" -> "play.core.server.NettyServerProvider",
  //"play.server.akka.server-header" -> "akka-http-server",
  //"play.server.netty.server-header" -> "netty-server",
  //"play.server.max-content-length" -> "30m",
  
  //"akka.http.host-connection-pool.max-open-requests" -> "256",
      
  //"play.server.netty.log.wire" -> "false", // Needs io.netty.handler set to DEBUG in logback

  // To change a config for the actor system used in dev mode:
  //"play.akka.dev-mode.akka.jvm-exit-on-fatal-error" -> "false",
  // Plus, if you want to change an akka setting _in dev-mode_ for _both_ the dev-mode _and_ application actor system you need to do that here:
  //"akka.jvm-exit-on-fatal-error" -> "true",
)
```

# Further discussion

* https://discuss.lightbend.com/t/exceeded-configured-max-open-requests-value-of-32/8217/8
* https://discuss.lightbend.com/t/questions-about-akka-http-alpakka-s3-in-play/6957
