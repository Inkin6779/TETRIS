package org.dractec
package ftetris.js

import scalajs.js.annotation._
import org.scalajs.dom._

import scala.scalajs.js
import scala.scalajs.js.timers._
import org.dractec.ftetris.logic.Tiles._
import org.dractec.ftetris.logic.Game._
import org.scalajs.dom
import cats._
import cats.data.{State, _}
import cats.implicits._
import cats.effect._

@JSExportTopLevel("FTetris")
object FTetris {

  // TODO: add callback API

  @JSExport
  def startGame(
     canv: html.Canvas,
     onpointchange: js.Function1[Int, Unit],
     ongameend: js.Function0[Unit]
   ): Unit = {
    type Ctx2D =
      CanvasRenderingContext2D
    val ctx = canv.getContext("2d")
      .asInstanceOf[Ctx2D]

    val gs = initGS(Config(new Input{
      var keysDown = Set[Int]()
      val validInput = Set(37, 65, 39, 68, 40, 83, 32)

      dom.window.addEventListener("keydown", (e: dom.KeyboardEvent) => {
        if (validInput(e.keyCode)) {
          e.preventDefault()
          e.stopPropagation()
          keysDown += e.keyCode
      }}, useCapture = false)

      dom.window.addEventListener("keyup", (e: dom.KeyboardEvent) => {
        if (validInput(e.keyCode)) {
          e.preventDefault()
          e.stopPropagation()
          keysDown -= e.keyCode
      }}, useCapture = false)

      override def leftDown = keysDown.contains(37) || keysDown.contains(65)

      override def rightDown = keysDown.contains(39) || keysDown.contains(68)

      override def softDropDown = keysDown.contains(40) || keysDown.contains(83)

      override def rotateDown = keysDown.contains(32)
    }))

    def drawGradient(): Unit = {
      ctx.fillStyle = {
        val grd = ctx.createLinearGradient(0, 0, 0, canv.height)
        grd.addColorStop(0, "black")
        grd.addColorStop(1, "grey")
        grd
      }
      ctx.fillRect(0, 0, canv.width, canv.height)
    }

    def clearAll(): Unit = {
      ctx.clearRect(0, 0, canv.width, canv.height)
    }

    def drawTile(coord: Coord): Unit = {
//      println(s"Drawing tile $coord")
      val widthPerTile = canv.width / gs.conf.boardDims.x
      val heightPerTile = canv.height / gs.conf.boardDims.y
      // TODO print warning if not equal?
      ctx.fillStyle = "#49fb35" // neon green
      ctx.fillRect(coord.x * widthPerTile, coord.y * heightPerTile, widthPerTile, heightPerTile)
    }

    clearAll()
    drawGradient()
    onpointchange(0)

    var mainLoop: SetIntervalHandle = null
    var lastState = gs
    var lastField: Option[GameField] = None

    mainLoop = setInterval(1000d/60d) {
      val s = for {
        isOver <- nextFrame
      } yield isOver
      val (newState, isOver) = s.run(lastState).value
      if (lastState.points != newState.points) onpointchange(newState.points)
      lastState = newState
      val newField = globalTetCoverage(lastState)
        .map(tc => lastState.field |+| tc)
      if (newField != lastField) {
        drawGradient()
        newField.getOrElse(lastState.field)
          .foreach { case (c, occ) => if (occ) drawTile(c) }
        lastField = newField
      }
      if (isOver) {
        clearInterval(mainLoop)
        ongameend()
      }
      gs -> ()
    }
  }
}
