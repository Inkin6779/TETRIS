package org.dractec
package ftetris.logic

import cats._
import cats.data.State
import cats.effect.IO
import cats.implicits._
import org.dractec.ftetris.logic.Tiles._

import scala.util.Random


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

  implicit val mapMergeSG = new Semigroup[Map[Coord, Option[Tile]]] {
    override def combine(x: Map[Coord, Option[Tile]], y: Map[Coord, Option[Tile]]) =
      x.map { case (c, v) => c -> (y.getOrElse(c, None) orElse v) }
  }

  type FramesToGo = Int
  type Frame = Int

  sealed trait Move
  case object LeftM extends Move
  case object RightM extends Move
  case object Nothing extends Move
  case object Rotate extends Move
  case object Drop extends Move

  type Level = Int

  val lvl0scoring = Map(
    0 -> 0,
    1 -> 40,
    2 -> 100,
    3 -> 300,
    4 -> 1200
  )
  def scoring(lvl: Level, lines: Int) =
    lvl0scoring(lines) * (lvl + 1)

  val dropSpeed = Map(
    0 -> 48,
    1 -> 43,
    2 -> 38,
    3 -> 33,
    4 -> 28,
    5 -> 23,
    6 -> 18,
    7 -> 13,
    8 -> 10,
    9 ->  8,
    10 -> 7,
    11 -> 6,
    12 -> 5,
    13 -> 4,
    14 -> 4,
    15 -> 4,
    16 -> 3,
    17 -> 3,
    18 -> 3
    // else 2 for convenience
  ).withDefaultValue(2)

  type FramesPerDrop = Int
  case class DASDelay(hard: Int, soft: Int)
  case class Config(
      input: IO[Input],
      startLevel: Int = 0,
      random: Random = util.Random,
      boardDims: Coord = Coord(10, 20),
      DAS: DASDelay = DASDelay(16, 6),
      softDropSpeed: FramesPerDrop = 2,
      rotateDelay: Int = 10, // delay between first rotation and next rotation
      validateGS: Boolean = true
  )

  case class LineClear(row: Int, clearTime: Frame)
  case class Tetromino(tile: Tile, rotation: Rotation, pos: Coord)
  case class GS private (frameCount: Frame,
                         field: GameField,
                         currTet: Option[Tetromino],
                         nextTile: Tile,
                         nextRotation: Rotation,
                         tetSpawnBag: Set[Tile],
                         points: Int,
                         level: Level,
                         numConsecutiveDrops: Int,
                         lastMove: Move,
                         lastClears: List[LineClear],
                         lastMoveTimes: Map[Move, Frame],
                         turnsToSpawn: Option[FramesToGo],
                         conf: Config)
  def initGS(conf: Config): IO[Game.GS] = for {
    nextTile <- pickRandom(allTiles)(conf.random)
    nextRotation <- pickRandom(allRotations)(conf.random)
  } yield GS(
      frameCount = 0,
      field = initField(conf),
      currTet = None,
      nextTile = nextTile,
      nextRotation = nextRotation,
      tetSpawnBag = Set(nextTile),
      points = 0,
      level = conf.startLevel,
      numConsecutiveDrops = 0,
      lastMove = Nothing, // irrelevant but easier this way
      lastClears = List(),
      lastMoveTimes = Map(LeftM -> 0, RightM -> 0, Rotate -> 0, Drop -> 0),
      turnsToSpawn = Some(1),
      conf
  )

  type GameField = Map[Coord, Option[Tile]]

  def nextFrame(gs: GS): IO[(GS, Boolean)] = {
    handleInput(gs) flatMap ((gs: GS) => gs.conf.input flatMap { inp =>
      def noChange = IO.pure(gs)
      if (gs.turnsToSpawn.contains(0)) handleSpawn(gs)
      else if (gs.currTet.isEmpty) noChange // waiting for tet, do nothing
      else if (gs.frameCount % dropSpeed(gs.level) == 0) moveDown(gs) else noChange
    }) map wrapTurn map checkIfOver
  }

  private def handleSpawn(gs: GS): IO[GS] = for {
    nextTile <- pickRandom(allTiles diff gs.tetSpawnBag)(gs.conf.random)
    nextRotation <- IO{(nextRotation _ * gs.conf.random.nextInt(4))(initRotation)}
  } yield gs.copy(currTet = Tetromino(
    gs.nextTile,
    gs.nextRotation,
    pos = Coord(3, -2)
  ).some,
    tetSpawnBag = {
      val newBag = gs.tetSpawnBag + nextTile
      if (newBag == allTiles) Set() else newBag
    },
    turnsToSpawn = None,
    nextTile = nextTile,
    nextRotation = nextRotation
  )

  private def checkIfOver(gs: GS): (GS, Boolean) =
    gs -> (tetHasBlock(gs, _.y < 0) && hasFieldCollision(gs, mapTetYPos(_, _ + 1)))

  private def moveDown(gs: GS): IO[GS] = gs.conf.input map { inp =>
    def calcARE(gs: GS): Option[Int] = {
      //    bottom 2 rows: 10 frames
      //    then each 4 rows above 2 frames longer
      def lowestTetY = globalTetCoverage(gs).map(_.collect{case (c, t) if t.isDefined => c.y}.max)
      lowestTetY.map(mY => (((gs.conf.boardDims.y - mY) - 2) / 4) * 2 + 10)
    }

    if (tetHasBlock(gs, _.y == gs.conf.boardDims.y - 1) ||
        hasFieldCollision(gs, mapTetYPos(_, _ + 1)))
    {
      val mergedField = gs.field |+| globalTetCoverage(gs)
        .getOrElse(sys.error("Can't move a non-existing currTet downwards!"))
      val newConsDrops = if (inp.softDropDown) gs.numConsecutiveDrops + 1 else 0
      gs.copy(
        turnsToSpawn = calcARE(gs),
        currTet = None,
        numConsecutiveDrops = newConsDrops,
        points = gs.points + newConsDrops,
        field = mergedField,
        lastClears = gs.lastClears ++
          allFullLines(gs.conf, mergedField).map(y => LineClear(y, gs.frameCount))
      )
    } else mapCurrTetPos(gs, c => Coord(c.x, c.y + 1))
  }

  private def allFullLines(conf: Config, gf: GameField): List[Int] = {
    List.tabulate(conf.boardDims.y)(y => y ->
      (0 until conf.boardDims.x).forall(x =>
        gf.getOrElse(Coord(x, y), sys.error("Logic error in `allFullLines`")).isDefined))
      .collect{case (y, true) => y}
  }

  private def handleInput(gs: GS): IO[GS] = gs.conf.input.flatMap { inp =>
    // first sucessfully updated gs left, last recognized button press as right
    def tryMoveLeft(d: Move) = if (inp.leftDown) tryMoveSideways(gs, LeftM) else d.asRight
    def tryMoveRight(d: Move) = if (inp.rightDown) tryMoveSideways(gs, RightM) else d.asRight
    def trySoftDrop(d: Move) = if (inp.softDropDown) tryDrop(gs) else IO.pure(d.asRight)
    def tryRotateWhenBtn(d: Move) = if (inp.rotateDown) tryRotate(gs) else d.asRight
    trySoftDrop(Nothing) map (_ flatMap tryRotateWhenBtn flatMap tryMoveLeft flatMap tryMoveRight match {
      case Left(res) => res
      case Right(dir) => gs.copy(lastMove = dir)
    })
  }

  private def tryDrop(gs: GS): IO[Either[GS, Move]] = {
    if (gs.currTet.isDefined && gs.frameCount % gs.conf.softDropSpeed == 0)
      moveDown(gs).map(saveMove(_, Rotate)).map(_.asLeft)
    else IO.pure(Drop.asRight)
  }

  private def tryRotate(gs: GS): Either[GS, Move] = {
    if (!tetHasBlock(gs,
      // if coord taken or outside of field, fail check
      b => gs.field.get(b).forall(_.isDefined),
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

  private def overlapsWithTet(gs: GS, v: (Coord, Option[Tile]), f: Tetromino => Tetromino): Boolean = v match {
    case (c, t) => t.isDefined && globalTetCoverage(gs, f).exists(_.getOrElse(c, None).isDefined)
  }

  private def saveMove(gs: GS, dir: Move): GS = gs.copy(
    lastMove = dir, lastMoveTimes = gs.lastMoveTimes + (dir -> gs.frameCount))

  private def tetHasBlock(gs: GS, f: Coord => Boolean, tr: Tetromino => Tetromino = identity) =
    globalTetCoverage(gs, tr).exists(_.exists{case (c, t) => t.isDefined && f(c)})

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

  private def handleLineClear(gs: GS): GS = gs.lastClears.collect {
    case LineClear(row, ct) if gs.frameCount - ct == 20 => row
  }.sorted |> {l => l.foldLeft(gs){ // smallest row ind first, so we dont have to fit indices while collapsing
    case (ngs, row) => ngs.copy(field =
      List.tabulate(gs.conf.boardDims.x, gs.conf.boardDims.y) {
        case (x, 0)             => Coord(x, 0) -> None
        case (x, y) if y <= row => Coord(x, y) -> ngs.field(Coord(x, y - 1))
        case (x, y)             => Coord(x, y) -> ngs.field(Coord(x, y))
      }.flatten.toMap
    )}.copy(points = gs.points + scoring(gs.level, l.length)) |> levelUp
  }

  private def levelUp(gs: GS): GS = gs.copy(
    level = if (gs.lastClears.count(gs.frameCount - _.clearTime >= 20) |> { linesCleared =>
      // Tetris wiki specs: (confusing, unbalanced)
//      val requiredClears = (gs.conf.startLevel * 10 + 10) min (100 max (gs.conf.startLevel * 10 - 50))
//      linesCleared > (requiredClears + 10 * (gs.level - gs.conf.startLevel))
      // Observed specs:
      linesCleared >= (gs.level + 1 - gs.conf.startLevel) * 10
    }) gs.level + 1 else gs.level
  )

  private def pickRandom[T](s: Set[T])(rand: Random): IO[T] = IO {
    val n = rand.nextInt(s.size)
    s.iterator.drop(n).next
  }

  private def optValidate(gs: GS): GS = if (gs.conf.validateGS) validateRules(gs) else gs

  def globalTetCoverage(gs: GS, f: Tetromino => Tetromino = identity): Option[CoverageBox] =
    gs.currTet.map(f).map(tet => coverage(tet.tile)(tet.rotation).mapKeys(_ |+| tet.pos))

  def coverageWithClearAnimation(gs: GS, field: GameField): GameField = field.map{
    case (c, t) =>
      if (gs.lastClears.exists{ cl =>
        val diff = gs.frameCount - cl.clearTime
        val disappearing = cl.row == c.y && diff < 20
        disappearing && diff % 8 >= 4
      }) {
        c -> None
      } else {
        c -> t
      }
  }

  private def initField(conf: Config): GameField = (for {
    x <- 0 until conf.boardDims.x
    y <- 0 until conf.boardDims.y
  } yield Coord(x, y) -> None).toMap

  private def Failure = Nothing.asRight

  def validateRules(gs: GS): GS = {
    // TODO: change to Validated somehow, accumulating errors
    // current tile cant overlap with static field
//    globalTetCoverage(gs).foreach(tc =>
//      if (gs.field.exists { case (c, v) => tc.getOrElse(c, None).isDefined && v.isDefined })
//        sys.error("Current tile exists and is overlapping with static field."))
    // cant have a current tet defined and a new one scheduled for spawn at the same time
    if (gs.currTet.nonEmpty && gs.turnsToSpawn.nonEmpty)
      sys.error("currTet is defined yet a new spawn is scheduled.")
    gs
  }

  /** Checks whether the according key is currently pressed
    * Methods are called max. once every `nextFrame` */
  trait Input {
    def leftDown: Boolean
    def rightDown: Boolean
    def softDropDown: Boolean
    def rotateDown: Boolean
  }

}
