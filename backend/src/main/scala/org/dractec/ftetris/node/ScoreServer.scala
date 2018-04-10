package org.dractec
package ftetris.node

import io.scalajs.nodejs
import io.scalajs.nodejs.http.{Http, RequestOptions, ServerResponse}
import io.scalajs.nodejs._
import io.scalajs.npm.express._
import io.scalajs.npm.bodyparser._

//@JSExportTopLevel("Server")
object ScoreServer {

  def main(args: Array[String]): Unit = {
    val app = Express()
    val port = 3000
    val __dirname = {
      val res = path.Path.resolve()
      if (res.endsWith("/app")) res
      else res + "/app"
    }

    app.use("/public", Express.static(__dirname + "/public"))
    //app.use("/views", Express.static("views"))

    // add more parsers as necessary to parse POST bodies
    app.use(BodyParser.json())
    app.use(BodyParser.urlencoded(new UrlEncodedBodyOptions(extended = false)))

    val server = app
      .get("/", (_: Request, resp: Response) => {
        resp.sendFile(__dirname + "/views/index.html")
      })
      .get("/scores/:name", (req: Request, resp: Response) => {
        println(s"Got request $req with origUrl=${req.originalUrl} and " +
          s"name=${req.params("name")}")
        resp.send(req.originalUrl.substring(req.originalUrl.indexOf("/scores/") + 8))
        // TODO: return score as text for REST style usage
      })
      .post("/scores/:name", (req: Request, resp: Response) => {
        val (ip, name) = (req.ip, req.params("name"))
        // TODO: save score
      })

    val listener = server.listen(port, () => {
      println(s"Server listening on port $port in dir ${__dirname}")
    })
  }

}
