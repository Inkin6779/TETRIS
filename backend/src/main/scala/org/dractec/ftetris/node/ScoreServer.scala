package org.dractec
package ftetris.node

import io.scalajs.nodejs.http.{Http, RequestOptions, ServerResponse}
import io.scalajs.nodejs._
import io.scalajs.npm.express._

import scala.scalajs.js.annotation.JSExportAll

@JSExportAll
object ScoreServer extends App {
  val app = Express()
  val port = 3000

  app.use(Express.static("public"))

  println(__dirname)

  val server = app
    .get("/", (_: Request, resp: Response) => {
      resp.sendFile("/app/views/index.html")
    })
    .get("/scores/:name", (req: Request, resp: Response) => {
      println(s"Got request $req with origUrl=${req.originalUrl} and " +
        s"name=${req.params("name")}")
      resp.send(req.originalUrl.substring(req.originalUrl.indexOf("/scores") + 7))
    })

  val listener = server.listen(port)
}
