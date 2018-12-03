package org.dractec
package ftetris.js

import scalajs.js.annotation._
import org.scalajs.dom._

import scala.scalajs.js
import scala.scalajs.js.timers._
import org.dractec.ftetris.logic.Tiles._
import org.dractec.ftetris.logic.Game._
import org.scalajs.dom
import cats.implicits._
import cats.effect._
import org.scalajs.dom.raw.HTMLImageElement

import scala.collection.mutable

@JSExportTopLevel("FTetris")
object FTetris {

  @js.native
  trait GameConf extends js.Object {
    val mainCanvas: html.Canvas = js.native
    val nextTileCanvas: html.Canvas = js.native
    val onpointchange: js.Function1[Int, Unit] = js.native
    val onlevelchange: js.Function1[Int, Unit] = js.native
    val onlineclear: js.Function1[Int, Unit] = js.native
    val touchRootNode: dom.Node = js.native
    val ongameend: js.Function0[Unit] = js.native
    val onpausestart: js.Function0[Unit] = js.native
    val onpauseend: js.Function0[Unit] = js.native
    val simpleRenderingMode: js.Function0[Boolean] = js.native
    val guidelineColors: js.Function0[Boolean] = js.native
  }

  var canStart: Boolean = true
  var paused: Boolean = false

  implicit class DOMListWrapper[A](val v: DOMList[A]) extends AnyVal {
    def toList = (for (i <- 0 to v.length) yield v(i)).toList
  }

  @JSExport
  def startGame(gc: GameConf): Unit = {

    if (!canStart) return
    canStart = false

    var lastRenderingMode = false
    def simpleRenderingMode: Boolean = {
      val res = if (js.isUndefined(gc.simpleRenderingMode)) false else gc.simpleRenderingMode()
      lastRenderingMode = res
      res
    }

    def useGuidelineColors: Boolean = if (js.isUndefined(gc.guidelineColors)) false else gc.guidelineColors()

    def tileColors = if (useGuidelineColors) {
      Map[Option[Tile], String](
        Straight.some -> "cyan",
        Box.some -> "yellow",
        LeftL.some -> "blue",
        RightL.some -> "orange",
        Tee.some -> "purple",
        SnakeL.some -> "red",
        SnakeR.some -> "green",
        // for simple rendering mode
        None -> "#303030"
      )
    } else {
      Map[Option[Tile], String](
        Straight.some -> "#4AC948",
        Box.some -> "#83F52C",
        LeftL.some -> "#76EE00",
        RightL.some -> "#66CD00",
        Tee.some -> "#49E20E",
        SnakeL.some -> "#83F52C",
        SnakeR.some -> "#4DBD33",
        // for simple rendering mode
        None -> "#303030"
      )
    }

    val touchRoot: dom.Node = if (js.isUndefined(gc.touchRootNode)) gc.mainCanvas else gc.touchRootNode

    type Ctx2D =
      CanvasRenderingContext2D
    val contextMap = Map(
      gc.mainCanvas -> gc.mainCanvas.getContext("2d").asInstanceOf[Ctx2D],
      gc.nextTileCanvas -> gc.nextTileCanvas.getContext("2d").asInstanceOf[Ctx2D]
    )
    def ctx(implicit canv: html.Canvas) = contextMap(canv)

    def callIfDef(f: js.Function0[Unit]): Unit = if (!js.isUndefined(f)) f()

    var keysDown = Set[Int]()
    var lastTouchMove: Move = Nothing
    val validInput = Set(37, 65, 39, 68, 40, 83, 32, 27, 80, 38)

    val initialGs = initGS(Config(
      input = IO { new Input{
        override def leftDown = keysDown.contains(37) || keysDown.contains(65) || lastTouchMove == LeftM

        override def rightDown = keysDown.contains(39) || keysDown.contains(68) || lastTouchMove == RightM

        override def softDropDown = keysDown.contains(40) || keysDown.contains(83) || lastTouchMove == Drop

        override def rotateDown = keysDown.contains(38) || keysDown.contains(32) || lastTouchMove == Rotate
    }})).unsafeRunSync()

    var lastState = initialGs
    var lastField: Option[GameField] = None
    var lastNextTile: Tile = initialGs.nextTile

  // __________ DRAWING FUNCTIONS __________

    def redraw(oldField: Option[GameField], newField: GameField): Unit = {
      implicit val canv: html.Canvas = gc.mainCanvas
      val lastMode = lastRenderingMode
      if (simpleRenderingMode) {
        if (oldField.isEmpty || !lastMode) {
          // draw all first time
          newField.foreach{case (c, t) => drawTile(t, c, lastRenderingMode)}
        } else {
          // draw only the difference
          val diff = newField.collect{
            case (c, t) if t != oldField.get.apply(c) => c -> t}
          diff.foreach{case (c, t) => drawTile(t, c, lastRenderingMode)}
        }
      } else {
        drawGradient()
        newField.foreach{case (c, t) => if (t.isDefined) drawTile(t, c, lastRenderingMode) }
      }
    }

    def gradientStyle(implicit canv: html.Canvas): CanvasGradient = {
      val grd = ctx.createLinearGradient(0, 0, 0, gc.mainCanvas.height)
//      if (useGuidelineColors) {
//        grd.addColorStop(0, "grey")
//        grd.addColorStop(1, "white")
//      } else {
        grd.addColorStop(0, "black")
        grd.addColorStop(1, "grey")
//      }
      grd
    }

    def drawGradient()(implicit canv: html.Canvas): Unit = {
      ctx.fillStyle = gradientStyle
      ctx.fillRect(0, 0, canv.width, canv.height)
      drawSneakyGrid()
    }

    def clearAll()(implicit canv: html.Canvas): Unit = {
      ctx.clearRect(0, 0, canv.width, canv.height)
    }

    def drawSneakyGrid()(implicit canv: html.Canvas): Unit = {
      val widthPerTile = canv.width / initialGs.conf.boardDims.x
      val heightPerTile = canv.height / initialGs.conf.boardDims.y
      ctx.strokeStyle = "black"
      ctx.lineWidth = 1
      ctx.globalAlpha = 0.1
      for (x <- 0 until initialGs.conf.boardDims.x) {
        ctx.strokeRect(x * widthPerTile, 0, 1, canv.height)
      }
      for (y <- 0 until initialGs.conf.boardDims.y) {
        ctx.strokeRect(0, y * heightPerTile, canv.width, 1)
      }
      ctx.globalAlpha = 1
    }

    def drawPreview(tile: Tile, rotation: Rotation, renderingMode: Boolean): Unit = {
      implicit val canv = gc.nextTileCanvas
      val dims = Coord(4, 4)
      val tiles = coverage(tile)(rotation)
      val border = ((0 until 4).map(Coord(3, _) -> None) ++ (0 until 4).map(Coord(_, 3) -> None)).toMap[Coord, Option[Tile]]
      drawGradient()
      (border ++ tiles).foreach{case (c, t) => drawTile(t, c, renderingMode, dims)}
    }

    def drawTile(tile: Option[Tile], coord: Coord, renderingMode: Boolean,
                 dimensions: Coord = initialGs.conf.boardDims)(implicit canv: html.Canvas): Unit =
    {
      val widthPerTile = canv.width / dimensions.x
      val heightPerTile = canv.height / dimensions.y
      ctx.fillStyle = tileColors(tile)
      ctx.fillRect(coord.x * widthPerTile, coord.y * heightPerTile, widthPerTile, heightPerTile)
      if (renderingMode) {
        ctx.globalAlpha = 1
        ctx.strokeStyle = "#696969"
        ctx.lineWidth = 1
      } else {
        ctx.globalAlpha = if (useGuidelineColors) 1 else 0.1
        ctx.strokeStyle = if (useGuidelineColors) "#696969" else gradientStyle
        ctx.lineWidth = 2
      }
      ctx.strokeRect(coord.x * widthPerTile, coord.y * heightPerTile, widthPerTile, heightPerTile)
      ctx.globalAlpha = 1
    }

  // __________ PAUSE HANDLING __________

    def pause(): Unit = {
      implicit val canv = gc.mainCanvas
      drawGradient()
      val center = Coord(gc.mainCanvas.width / 2, gc.mainCanvas.height / 2)
      val linewidth = gc.mainCanvas.width / 10
      val lineheight = gc.mainCanvas.height / 5
      ctx.fillStyle = "red"
      ctx.fillRect(center.x - linewidth * 1.5, center.y - lineheight / 2, linewidth, lineheight)
      ctx.fillRect(center.x + linewidth * 0.5, center.y - lineheight / 2, linewidth, lineheight)
    } andFinally callIfDef(gc.onpausestart)

    def resume(): Unit = {
      implicit val canv = gc.mainCanvas
      drawGradient()
      val field = globalTetCoverage(lastState)
        .map(tc => lastState.field |+| tc)
        .getOrElse(lastState.field)
      redraw(None, field)
    } andFinally callIfDef(gc.onpauseend)

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

    def pos(e: TouchEvent, i: Int = 0) =
      if (i >= e.touches.length) None
      else Some(e.touches(i)) map { t: Touch => {
        val cr = gc.mainCanvas.getBoundingClientRect()
        Coord(
          (t.clientX - cr.left).toInt,
          (t.clientY - cr.top).toInt
        )
      }}

    val movesSinceTouchStart = mutable.Stack[TouchEvent]()
    var moveIsDrop: Option[Boolean] = false.some
    var lastPause = 0l

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
      // TODO: Rework control flow. Right now it's a mess.
      e.preventDefault()
      def currTime = System.currentTimeMillis()
      val thresh = gc.mainCanvas.width / initialGs.conf.boardDims.x / 30
      val last = movesSinceTouchStart.top
      movesSinceTouchStart.push(e)
      val cp = pos(e).get
      val lp = pos(last).get
      val cp1opt = pos(e, 1)
      val lp1opt = pos(last, 1)
      //noinspection UnitInMap
      cp1opt.flatMap(cp1 => lp1opt.map(lp1 =>
        if ((cp.y - lp.y) > thresh && (cp1.y - lp1.y) > thresh && (currTime - lastPause) > 500) {
          lastPause = currTime
          paused = !paused
          if (paused) pause()
          else resume()
        }
      )).getOrElse{
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
      }
    })
    touchRoot.addEventListener("touchend", handleTouchEnd)
    touchRoot.addEventListener("touchcancel", handleTouchEnd)

  // __________ GAME END STUFF __________

    var mainLoop: Option[SetIntervalHandle] = None

    val img = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
    img.src = "img/gameover.png"

    def endGame(): Unit = {
      mainLoop.foreach(clearInterval)
      canStart = true
      ctx(gc.mainCanvas).drawImage(img, 0, gc.mainCanvas.height/2 - img.height/2)
    }

    // __________ MAIN GAME LOOP __________

    clearAll()(gc.mainCanvas)
    drawGradient()(gc.mainCanvas)
    drawPreview(initialGs.nextTile, initialGs.nextRotation, simpleRenderingMode)
    gc.onpointchange(0)
    gc.onlevelchange(initialGs.level)

    callIfDef(gc.ongameend)

    var frameIsRunning = false

    mainLoop = setInterval(1000d/60d) {
      if (frameIsRunning) {
        frameIsRunning = false
        sys.error("Calculating a frame took too long.")
      }
      frameIsRunning = true
      if (!paused) {
        // have to run here, since running everything at once fails to draw
        val (newState, isOver) = nextFrame(lastState).unsafeRunSync()

        if (lastState.points != newState.points) gc.onpointchange(newState.points)
        if (lastState.lastClears.count(_.clearTime >= 20) != newState.lastClears.count(_.clearTime >= 20))
          gc.onlineclear(newState.lastClears.size)
        if (lastState.level != newState.level) gc.onlevelchange(newState.level)

        if (lastNextTile != newState.nextTile) {
          lastNextTile = newState.nextTile
          drawPreview(newState.nextTile, newState.nextRotation, simpleRenderingMode)
        }

        if (lastTouchMove == Rotate && (newState.lastMoveTimes(Rotate) - newState.frameCount) < initialGs.conf.rotateDelay)
          lastTouchMove = Nothing

        val rawNewField = globalTetCoverage(newState)
          .map(tc => newState.field |+| tc)
        val newField = coverageWithClearAnimation(newState, rawNewField.getOrElse(newState.field))
        if (lastField.isEmpty || newField != lastField.get) {
          redraw(lastField, newField)
          lastField = newField.some
        }

        lastState = newState
        if (isOver) endGame()
      }
      frameIsRunning = false
    }.some
  }
}
