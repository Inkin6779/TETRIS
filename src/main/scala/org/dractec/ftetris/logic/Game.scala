package org.dractec
package ftetris.logic

import cats._
import cats.data.State
import cats.implicits._
import org.dractec.ftetris.logic.Tiles._

import scala.util.Random

/** Game logic and stuff based on the state monad */
object Game {

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
      - line clear animation has 5 steps, each 4 frames
  - X is left to right, Y is top to bottom
  - Local bounding box (0, 0) is upper left corner
      of boxes shown here: http://tetris.wikia.com/wiki/Nintendo_Rotation_System
  - Rotational center is odd for straight and
      center of a 3x3 for all other non-boxes
  - Scoring: http://tetris.wikia.com/wiki/Scoring

   ========================= */

  // TODO: soft drop only when button down
  // TODO:

  implicit val mapMergeSG = new Semigroup[Map[Coord, Boolean]] {
    override def combine(x: Map[Coord, Boolean], y: Map[Coord, Boolean]) =
      x.map { case (c, v) => c -> (y.getOrElse(c, false) | v) }
  }

  type FramesToGo = Int
  type Frame = Int

  sealed trait Move
  case object LeftM extends Move
  case object RightM extends Move
  case object Nothing extends Move
  case object Rotate extends Move

  val scoring = Map(
    0 -> 0,
    1 -> 40,
    2 -> 100,
    3 -> 300,
    4 -> 1200
  )

  type FramesPerDrop = Int
  case class DASDelay(hard: Int, soft: Int)
  case class Config(
      input: Input,
      random: Random = new Random(System.currentTimeMillis),
      boardDims: Coord = Coord(10, 20),
      DAS: DASDelay = DASDelay(16, 6),
      dropSpeed: FramesPerDrop = 48,
      softDropSpeed: FramesPerDrop = 2,
      rotateDelay: Int = 10, // delay between first rotation and next rotation
      validateGS: Boolean = false
  )

  case class LineClear(row: Int, clearTime: Frame, consecutiveDrops: Int)
  case class Tetromino(tile: Tile, rotation: Rotation, pos: Coord)
  case class GS private (frameCount: Frame,
                         field: GameField,
                         currTet: Option[Tetromino],
                         points: Int,
                         numConsecutiveDrops: Int,
                         lastMove: Move,
                         lastClears: List[LineClear],
                         lastMoveTimes: Map[Move, Frame],
                         turnsToSpawn: Option[FramesToGo],
                         conf: Config)
  def initGS(conf: Config) =
    GS(frameCount = 0,
       field = initField(conf),
       currTet = None,
       points = 0,
       numConsecutiveDrops = 0,
       lastMove = LeftM, // irrelevant but easier this way
       lastClears = List(),
       lastMoveTimes = Map(LeftM -> 0, RightM -> 0, Rotate -> 0),
       turnsToSpawn = Some(1),
       conf)

  type GameField = Map[Coord, Boolean]
  type GameState[T] = State[GS, T]
  def GameState[T](f: GS => (GS, T)) = State[GS, T](f)

  def nextFrame = GameState[Boolean] { gs => {
    def handleStart = handleInput(gs) |> ((gs: GS) => {
      if (gs.turnsToSpawn.contains(0))
        gs.copy(currTet = Tetromino(
          tiles(gs.conf.random.nextInt(tiles.length)),
          (nextRotation _ * gs.conf.random.nextInt(4))(initRotation),
          pos = Coord(3, -2)).some, turnsToSpawn = None)
      else if (gs.currTet.isEmpty) gs // waiting for tet, do nothing
//      else if (gs.inDrop)
      else if (gs.conf.input.softDropDown)
        if (gs.frameCount % gs.conf.softDropSpeed == 0) moveDown(gs) else gs
      else if (gs.frameCount % gs.conf.dropSpeed == 0) moveDown(gs) else gs
    }: GS) |> wrapTurn
    handleStart |> checkIfOver
  }}

  private def checkIfOver(gs: GS): (GS, Boolean) =
    gs -> (tetHasBlock(gs, _.y < 0) && hasFieldCollision(gs, mapTetYPos(_, _ + 1)))

  private def moveDown(gs: GS): GS = {
    // try to move down
    // if not possible,
    //   schedule new spawn
    //   and book points
    //   and reset inDrop
    //   and set currTet to None
    def calcARE(gs: GS): Option[Int] = {
      //    bottom 2 rows: 10 frames
      //    then each 4 rows above 2 frames longer
      def lowestTetY = globalTetCoverage(gs).map(_.collect{case (c, occ) if occ => c.y}.max)
      lowestTetY.map(mY => (((gs.conf.boardDims.y - mY) - 2) / 4) * 2 + 10)
    }

    if (tetHasBlock(gs, _.y == gs.conf.boardDims.y - 1) ||
        hasFieldCollision(gs, mapTetYPos(_, _ + 1)))
    {
      // TODO: cant move down, end turn
      val mergedField = gs.field |+| globalTetCoverage(gs)
        .getOrElse(sys.error("Can't move a non-existing currTet downwards!"))
      gs.copy(
        turnsToSpawn = calcARE(gs),
        currTet = None,
        numConsecutiveDrops = if (gs.conf.input.softDropDown) gs.numConsecutiveDrops + 1 else 0,
        field = mergedField,
        lastClears = gs.lastClears ++
          allFullLines(gs.conf, mergedField).map(y => LineClear(y, gs.frameCount, gs.numConsecutiveDrops))
      )
    } else mapCurrTetPos(gs, c => Coord(c.x, c.y + 1))
  }

  private def allFullLines(conf: Config, gf: GameField): List[Int] = {
    List.tabulate(conf.boardDims.y)(y => y ->
      (0 until conf.boardDims.x).forall(x =>
        gf.getOrElse(Coord(x, y), sys.error("Logic error in `allFullLines`"))))
      .collect{case (y, true) => y}
  }

  private def handleInput(gs: GS): GS = {
    def inp = gs.conf.input
    def tryMoveLeft(d: Move) = if (inp.leftDown) tryMoveSideways(gs, LeftM) else d.asRight
    def tryMoveRight(d: Move) = if (inp.rightDown) tryMoveSideways(gs, RightM) else d.asRight
    def tryRotateWhenBtn = if (inp.rotateDown) tryRotate(gs) else Failure
    tryRotateWhenBtn flatMap tryMoveLeft flatMap tryMoveRight match {
      case Left(res) => res
      case Right(dir) => gs.copy(lastMove = dir)
    } //|> {_.copy(inDrop = gs.inDrop || gs.currTet.isDefined && inp.softDropDown)}
  }

  // TODO: if hitting a wall, rotate and push inside instead?
  private def tryRotate(gs: GS): Either[GS, Move] = {
    if (!tetHasBlock(gs,
      // if coord taken or outside of field, fail check
      b => gs.field.getOrElse(b, true),
      tet => tet.copy(rotation = nextRotation(tet.rotation))
    ) && gs.frameCount - gs.lastMoveTimes(Rotate) >= gs.conf.rotateDelay) {
      saveMove(gs.copy(currTet = gs.currTet.map(ct =>
        ct.copy(rotation = nextRotation(ct.rotation)))), Rotate).asLeft
    } else Failure
  }

  private def tryMoveSideways(gs: GS, dir: Move): Either[GS, Move] = {
    // DAS: if lm > 16 always move. if lm > 6 only move if last move equals this move
    def gsWithTetXPos(f: Int => Int) = mapCurrTetPos(gs, c => Coord(f(c.x), c.y))
    def checkDAS(dir: Move) = gs.frameCount - gs.lastMoveTimes(dir) >= gs.conf.DAS.hard ||
      (gs.lastMove == dir && gs.frameCount - gs.lastMoveTimes(dir) >= gs.conf.DAS.soft)
    def ifDASmapTetX(dir: Move, f: Int => Int) =
      if (checkDAS(dir)) saveMove(gsWithTetXPos(f), dir).asLeft else dir.asRight
    dir match {
      case LeftM =>
        if (!tetHasBlock(gs, _.x == 0) && !hasFieldCollision(gs, mapTetXPos(_, _ - 1)))
          ifDASmapTetX(LeftM, _ - 1)
        else Failure
      case RightM =>
        if (!tetHasBlock(gs, _.x == (gs.conf.boardDims.x - 1)) && !hasFieldCollision(gs, mapTetXPos(_, _ + 1)))
          ifDASmapTetX(RightM, _ + 1)
        else Failure
      case d => sys.error(s"Called tryMoveSideways with $d")
    }
  }

  private def hasFieldCollision(gs: GS, f: Tetromino => Tetromino) =
    gs.field.exists(overlapsWithTet(gs, _, f))

  private def overlapsWithTet(gs: GS, v: (Coord, Boolean), f: Tetromino => Tetromino): Boolean = v match {
    case (c, occ) => occ && globalTetCoverage(gs, f).exists(_.getOrElse(c, false))
  }

  private def saveMove(gs: GS, dir: Move): GS = gs.copy(
    lastMove = dir, lastMoveTimes = gs.lastMoveTimes + (dir -> gs.frameCount))

  private def tetHasBlock(gs: GS, f: Coord => Boolean, tr: Tetromino => Tetromino = identity) =
    globalTetCoverage(gs, tr).exists(_.exists{case (c, occ) => occ && f(c)})

  private def mapCurrTetPos(gs: GS, f: Coord => Coord): GS =
    gs.copy(currTet = gs.currTet.map(mapTetPos(_, f)))
  private def mapTetPos(tet: Tetromino, f: Coord => Coord): Tetromino =
    tet.copy(pos = f(tet.pos))
  private def mapTetXPos(tet: Tetromino, f: Int => Int): Tetromino =
    mapTetPos(tet, c => c.copy(x = f(c.x)))
  private def mapTetYPos(tet: Tetromino, f: Int => Int): Tetromino =
    mapTetPos(tet, c => c.copy(y = f(c.y)))

  private def wrapTurn(gs: GS): GS = gs.copy(
    frameCount = gs.frameCount + 1,
    turnsToSpawn = gs.turnsToSpawn.map(_ - 1)
  ) |> handleLineClear |> optValidate

  // TODO: handle line clear somehow
  private def handleLineClear(gs: GS): GS = gs.lastClears.collect {
    case LineClear(row, ct, drops) if gs.frameCount - ct == 20 => (row, drops)
  }.sortBy(_._1) |> {l => l.foldLeft(gs){ // smallest row ind first, so we dont have to fit indices while collapsing
    case (ngs, (row, drops)) => ngs.copy(field =
      List.tabulate(gs.conf.boardDims.x, gs.conf.boardDims.y) {
        case (x, 0)             => Coord(x, 0) -> false
        case (x, y) if y <= row => Coord(x, y) -> ngs.field(Coord(x, y - 1))
        case (x, y)             => Coord(x, y) -> ngs.field(Coord(x, y))
      }.flatten.toMap,
    )}.copy(points = gs.points + scoring(l.length) + l.headOption.map{case (_, drops) => drops}.getOrElse(0))
  }


  private def optValidate(gs: GS): GS = if (gs.conf.validateGS) validateRules(gs) else gs

  def globalTetCoverage(implicit gs: GS, f: Tetromino => Tetromino = identity): Option[CoverageBox] =
    gs.currTet.map(f).map(tet => coverage(tet.tile)(tet.rotation).mapKeys(_ |+| tet.pos))

  private def initField(conf: Config): GameField = (for {
    x <- 0 until conf.boardDims.x
    y <- 0 until conf.boardDims.y
  } yield Coord(x, y) -> false).toMap

  private def Failure = Nothing.asRight

  def validateRules(gs: GS): GS = {
    // TODO: validation rules aka 'unit test'
    // TODO: change to Validated somehow, accumulating errors
    // current tile cant overlap with static field
    globalTetCoverage(gs).foreach(tc =>
      if (gs.field.exists { case (c, v) => tc(c) && v })
        sys.error("Current tile exists and is overlapping with static field."))
    // cant have a current tet defined and a new one scheduled for spawn at the same time
    if (gs.currTet.nonEmpty && gs.turnsToSpawn.nonEmpty)
      sys.error("currTet is defined and a new spawn is scheduled.")
    // cant have a tet not defined and be in drop
//    if (gs.currTet.isEmpty && gs.inDrop)
//      sys.error("currTet is not defined but is in drop.")


    gs
  }

  // TODO: replace with reader monad aka kleisli?
  /** Checks whether the according key is currently pressed
    * Methods are called max. once every `nextFrame` */
  trait Input {
    def leftDown: Boolean
    def rightDown: Boolean
    def softDropDown: Boolean
    def rotateDown: Boolean
  }

}
