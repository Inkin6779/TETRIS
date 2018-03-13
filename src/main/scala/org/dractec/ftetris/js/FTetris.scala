package org.dractec
package ftetris.js

import cats.Functor

import scalajs.js.annotation._
import org.scalajs.dom._

import scala.scalajs.js
import scala.scalajs.js.timers._
import org.dractec.ftetris.logic.Tiles._
import org.dractec.ftetris.logic.Game._
import org.scalajs.dom
import cats.implicits._
import cats.effect._

import scala.collection.mutable

@JSExportTopLevel("FTetris")
object FTetris {

  // TODO: add callback API docs

  var canStart: Boolean = true
  var paused: Boolean = false

  /* // TODO: guideline colors
        Cyan I
        Yellow O
        Purple T
        Green S
        Red Z
        Blue J
        Orange L
  */

  val tileColors = Map[Tile, String](
    Straight -> "#4AC948",
    Box      -> "#83F52C",
    LeftL    -> "#76EE00",
    RightL   -> "#66CD00",
    Tee      -> "#49E20E",
    SnakeL   -> "#83F52C",
    SnakeR   -> "#4DBD33"
  )

  implicit class DOMListWrapper[A](val v: DOMList[A]) extends AnyVal {
    def toList = (for (i <- 0 to v.length) yield v(i)).toList
  }

  @JSExport
  def startGame(
     canv: html.Canvas,
     onpointchange: js.Function1[Int, Unit],
     onlevelchange: js.Function1[Int, Unit],
     onlineclear: js.Function1[Int, Unit],
     ongameend: js.Function0[Unit],
     touchRootNode: dom.Node = null,
     onpausestart: js.Function0[Unit] = () => {},
     onpauseend: js.Function0[Unit] = () => {}
   ): Unit = {

    if (!canStart) return
    canStart = false

    val touchRoot: dom.Node = if (touchRootNode == null) canv else touchRootNode
    echo! s"Got touchRoot = $touchRoot"
    if (touchRootNode == null) echo! "WAAAAT"

    type Ctx2D =
      CanvasRenderingContext2D
    val ctx = canv.getContext("2d")
      .asInstanceOf[Ctx2D]

    var keysDown = Set[Int]()
    var lastTouchMove: Move = Nothing
    val validInput = Set(37, 65, 39, 68, 40, 83, 32, 27, 80)

    val gs = initGS(Config(
      input = IO { new Input{
        override def leftDown = keysDown.contains(37) || keysDown.contains(65) || lastTouchMove == LeftM

        override def rightDown = keysDown.contains(39) || keysDown.contains(68) || lastTouchMove == RightM

        override def softDropDown = keysDown.contains(40) || keysDown.contains(83) || lastTouchMove == Drop

        override def rotateDown = keysDown.contains(32) || lastTouchMove == Rotate
    }}))

    var lastState = gs
    var lastField: Option[GameField] = None

  // __________ DRAWING FUNCTIONS __________

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

    def drawTile(tile: Tile, coord: Coord): Unit = {
      //      println(s"Drawing tile $coord")
      val widthPerTile = canv.width / gs.conf.boardDims.x
      val heightPerTile = canv.height / gs.conf.boardDims.y
      // TODO print warning if not equal?
      ctx.fillStyle = tileColors(tile) //"#49fb35" // neon green
      ctx.fillRect(coord.x * widthPerTile, coord.y * heightPerTile, widthPerTile, heightPerTile)
      ctx.lineWidth = 1
      ctx.fillStyle = "black"
      ctx.rect(coord.x * widthPerTile, coord.y * heightPerTile, widthPerTile, heightPerTile)
    }

  // __________ PAUSE HANDLING __________

    def pause(): Unit = {
      drawGradient()
      val center = Coord(canv.width / 2, canv.height / 2)
      val linewidth = canv.width / 10
      val lineheight = canv.height / 5
      ctx.fillStyle = "red"
      ctx.fillRect(center.x - linewidth * 1.5, center.y - lineheight / 2, linewidth, lineheight)
      ctx.fillRect(center.x + linewidth * 0.5, center.y - lineheight / 2, linewidth, lineheight)
    } andFinally onpausestart()

    def resume(): Unit = {
      drawGradient()
      globalTetCoverage(lastState)
        .map(tc => lastState.field |+| tc)
        .getOrElse(lastState.field)
        .foreach { case (c, t) => t.foreach(drawTile(_, c)) }
    } andFinally onpauseend()

  // __________ KEYBOARD EVENTS __________

    dom.window.addEventListener("keydown", (e: dom.KeyboardEvent) => {
      if (validInput(e.keyCode)) {
        e.preventDefault()
        e.stopPropagation()
        if (e.keyCode == 27 || e.keyCode == 80) {
          paused = !paused
          if (paused) pause()
          else resume()
        }
        else keysDown += e.keyCode
      }}, useCapture = false)

    dom.window.addEventListener("keyup", (e: dom.KeyboardEvent) => {
      if (validInput(e.keyCode)) {
        e.preventDefault()
        e.stopPropagation()
        keysDown -= e.keyCode
      }}, useCapture = false)

  // __________ TOUCH EVENTS __________

    // idea: swipe to the left to move left
    // soft drop while swiping down
    // tap to rotate

    def pos(e: TouchEvent) = e.touches(0) |> { t: Touch => {
      val cr = canv.getBoundingClientRect()
      Coord(
        (t.clientX - cr.left).toInt,
        (t.clientY - cr.top).toInt
      )
    }}

    val movesSinceTouchStart = mutable.Stack[TouchEvent]()
    var moveIsDrop: Option[Boolean] = false.some

    def handleTouchEnd(e: TouchEvent): Unit = {
      e.preventDefault()
      if (moveIsDrop.isEmpty || movesSinceTouchStart.lengthCompare(1) == 0)
        lastTouchMove = Rotate
      else lastTouchMove = Nothing
      moveIsDrop = None
    }
    touchRoot.addEventListener("touchstart", (e: TouchEvent) => {
      e.preventDefault()
      movesSinceTouchStart.clear()
      movesSinceTouchStart.push(e)
    })
    touchRoot.addEventListener("touchmove", (e: TouchEvent) => {
      // TODO: rework control flow
      e.preventDefault()
      val thresh = canv.width / gs.conf.boardDims.x / 30
      val last = movesSinceTouchStart.top
      movesSinceTouchStart.push(e)
      val cp = pos(e)
      val lp = pos(last)
      if (moveIsDrop.getOrElse(true)) {
        if ((cp.y - lp.y) > (lp.x - cp.x).abs && (cp.y - lp.y) > thresh) { // soft drop
          lastTouchMove = Drop
          moveIsDrop = true.some
        }
        else if (moveIsDrop.isEmpty && (cp.x - lp.x).abs > thresh)
          moveIsDrop = false.some
        else lastTouchMove = Nothing
      }
      if (moveIsDrop.contains(false)) {
        if (cp.x - lp.x > thresh) lastTouchMove = RightM
        else if (lp.x - cp.x > thresh) lastTouchMove = LeftM
        else lastTouchMove = Nothing
      }
    })
    touchRoot.addEventListener("touchend", handleTouchEnd)
    touchRoot.addEventListener("touchcancel", handleTouchEnd)

  // __________ MAIN GAME LOOP __________

    clearAll()
    drawGradient()
    onpointchange(0)
    onlevelchange(gs.level)

    var mainLoop: Option[SetIntervalHandle] = None

    mainLoop = setInterval(1000d/60d) {
      if (!paused) {
        // have to run here, since running everything at once fails to draw
        val (newState, isOver) = nextFrame(lastState).unsafeRunSync()

        if (lastState.points != newState.points) onpointchange(newState.points)
        if (lastState.lastClears.count(_.clearTime >= 20) != newState.lastClears.count(_.clearTime >= 20))
          onlineclear(newState.lastClears.size)
        if (lastState.level != newState.level) onlevelchange(newState.level)

        if (lastTouchMove == Rotate && (newState.lastMoveTimes(Rotate) - newState.frameCount) < gs.conf.rotateDelay) {
          echo! "Resetting lastTouchMove from rotate"
          lastTouchMove = Nothing
        }
        val newField = globalTetCoverage(newState)
          .map(tc => newState.field |+| tc)
        if (newField != lastField) {
          drawGradient()
          newField.getOrElse(newState.field)
            .foreach { case (c, t) => t.foreach(drawTile(_, c)) }
          lastField = newField
        }
        lastState = newState

        if (isOver) {
          mainLoop.foreach(clearInterval)
          canStart = true
          ongameend()
        }
      }
    }.some
  }
}
