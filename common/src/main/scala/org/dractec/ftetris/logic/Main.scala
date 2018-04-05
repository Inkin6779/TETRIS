package org.dractec
package ftetris.logic

import cats.effect.IO
import org.dractec.ftetris.logic.Game._

/** As simple as possible  */
object Main extends App {
  // TODO: setup sbt to work with non scalajs deps in this case
  var gs = initGS(Config(IO { new Input {
    override def leftDown = false
    override def rightDown = false
    override def softDropDown = true
    override def rotateDown = true
  }}))
  var isOver = false
  do {
    val (_gs: Game.GS, _isOver: Boolean) = nextFrame(gs).unsafeRunSync()
    Main.gs = _gs
    Main.isOver = _isOver
  } while (!isOver)
}
