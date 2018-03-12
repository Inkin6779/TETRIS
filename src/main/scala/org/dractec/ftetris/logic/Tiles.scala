package org.dractec
package ftetris.logic

import cats.{Monoid, Show}
import cats.implicits._

object Tiles {

  /* ===== CONVENTIONS =====

  - Tetris NES/Nintendo version:
      - 10x20 tiles
      - no hold piece
      - no hard drop (debatable)
      - 60 fps
      - Delayed Auto Shift:
          initial 16 frames, then every 6 frames
      - Soft drop speed 1/2 G (1G = 1 cell per frame)
      - ARE (appearance delay) of 10-18 frames
          based on how high last tile locked
          bottom 2 rows: 10 frames
          then each 4 rows above 2 frames longer
      - drop speed at lvl0: 1/48 G
      - other drop speeds and leveling here:
          http://tetris.wikia.com/wiki/Tetris_(NES,_Nintendo)
  - X is left to right, Y is top to bottom
  - Local bounding box (0, 0) is upper left corner
      of boxes shown here: http://tetris.wikia.com/wiki/Nintendo_Rotation_System
  - Rotational center is odd for straight and
      center of a 3x3 for all other non-boxes
  - Scoring: http://tetris.wikia.com/wiki/Scoring

   ========================= */

  case class Coord(x: Int, y: Int)
  implicit val coordMonoid = new Monoid[Coord] {
    override def empty: Coord = Coord(0, 0)
    override def combine(a: Coord, b: Coord): Coord = Coord(a.x + b.x, a.y + b.y)
  }

  type CoverageBox = Map[Coord, Option[Tile]]
  implicit val cbShow = new Show[CoverageBox] {
    def maxC(t: CoverageBox) = t.map{case (k, _) => k.x max k.y}.max + 1
    override def show(t: CoverageBox) = List.tabulate(maxC(t), maxC(t))((a, b) =>
      if (t(Coord(a, b)).isDefined) "X" else "0").map(_.mkString).mkString("\n")
  }

  sealed abstract class Tile
  case object Straight extends Tile
  case object Box extends Tile
  case object LeftL extends Tile
  case object RightL extends Tile
  case object Tee extends Tile
  case object SnakeR extends Tile
  case object SnakeL extends Tile
  val allTiles = Set[Tile](Straight, Box, LeftL, RightL, Tee, SnakeR, SnakeL)

  sealed trait Rotation
  case object HorizDown extends Rotation
  case object VertLeft extends Rotation
  case object HorizUp extends Rotation
  case object VertRight extends Rotation
  def initRotation: Rotation = HorizDown

  def nextRotation(rotation: Rotation): Rotation = rotation match {
    case HorizDown => VertLeft
    case VertLeft => HorizUp
    case HorizUp => VertRight
    case VertRight => HorizDown
  }

  def coverage(tile: Tile)(rotation: Rotation): CoverageBox = tile match {
    case Box => baseCoverage(Box)
    case Straight if (numRotations(rotation) & 1) == 0 => baseCoverage(Straight)
    case Straight => string2CB(
      """00X0
        |00X0
        |00X0
        |00X0
      """.stripMargin, Straight)
    case _ => rotate3x3BoxTimes(baseCoverage(tile), numRotations(rotation))
  }

//  def boxWidth(tile: Tile): Int = {
//    case Straight | Box => 4
//    case _ => 3
//  }

  def baseCoverage(tile: Tile): CoverageBox = string2CB(initBox(tile), tile)

  def rotate3x3CoverageBox(box: CoverageBox): CoverageBox = {
    List.tabulate(3, 3){
      case (0, 0) => Coord(0, 0) -> box(Coord(0, 2))
      case (0, 1) => Coord(0, 1) -> box(Coord(1, 2))
      case (0, 2) => Coord(0, 2) -> box(Coord(2, 2))
      case (1, 0) => Coord(1, 0) -> box(Coord(0, 1))
      case (1, 1) => Coord(1, 1) -> box(Coord(1, 1)) // -> true, always
      case (1, 2) => Coord(1, 2) -> box(Coord(2, 1))
      case (2, 0) => Coord(2, 0) -> box(Coord(0, 0))
      case (2, 1) => Coord(2, 1) -> box(Coord(1, 0))
      case (2, 2) => Coord(2, 2) -> box(Coord(2, 0))
      case _ => sys.error("Apparently List.tabulate is wrong ?")
    }.flatten.toMap
  }

  def rotate3x3BoxTimes(box: CoverageBox, n: Int): CoverageBox =
    (rotate3x3CoverageBox _ * n)(box)

  private def string2CB(str: String, tileType: Tile): CoverageBox = str.lines.zipWithIndex.flatMap {
    case (s, y) => s.zipWithIndex.map {
      case (c, x) => Coord(x, y) -> (if (c == 'X') Some(tileType) else None)
  }}.toMap


  private[Tiles] val numRotations = Map[Rotation, Int](
    HorizDown -> 0,
    VertLeft -> 1,
    HorizUp -> 2,
    VertRight -> 3
  )

  private[Tiles] val initBox = Map[Tile, String](

    Straight ->
    """0000
      |0000
      |XXXX
      |0000""".stripMargin,

    Box ->
    """0000
      |0XX0
      |0XX0
      |0000""".stripMargin,

    LeftL ->
    """000
      |XXX
      |00X""".stripMargin,

    RightL ->
    """000
      |XXX
      |X00""".stripMargin,

    Tee ->
    """000
      |XXX
      |0X0""".stripMargin,

    SnakeR ->
    """000
      |0XX
      |XX0""".stripMargin,

    SnakeL ->
    """000
      |XX0
      |0XX""".stripMargin
  )

}
