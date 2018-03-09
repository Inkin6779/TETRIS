package org

import scala.util.{Random, Try}

package object dractec {

//  object Demo {
//    def main(args: Array[String]): Unit = {
//      implicit val rand: Random = new Random(0xDEADBEEF)
//      val x = ~5
//      val f = (x: Int) => x * x
//      f(x)
//      x |> f
//      ~(1,2) == ~(2,1)
//    }
//  }

  implicit class RandomInverter[T: Numeric](f: T)(implicit rand: Random = new Random(System.currentTimeMillis())) {
    import Numeric.Implicits._
    /** Randomly negates the appended number based on an implicit `scala.util.Random` object.
      * If no implicit Random is found, a new one based on currentTimeMillis is created. */
    def unary_~ : T = if(rand.nextBoolean()) -f else f
  }

  implicit class Finally[T](val res: T) extends AnyVal {
    /** Call on return expression to cause some necessary side effects (ew) afterwards,
      * in order to skip the `val res = ...; todo; res` syntactic construct. */
    def andFinally(todo: => Unit): T = {todo; res}
  }

  implicit class KTApply[T](val that: T) extends AnyVal {
    /** Kotlin's `apply` - equal to `Some(that).map(f).get`.
      * Applies a function to a value with less nesting.
      * Note that this destroys the entire idea of monadic types.
      * Might be stolen directly from F# syntax...
      **/
    def |>[A](f: T => A ): A = f(that)
  }

  implicit class F1Wrapper[A](val v: A => A) extends AnyVal {
    def * (i: Int) = (1 to i).foldLeft(v){case(last, _) => last andThen v}
  }

  /** For more readable syntax after e.g. a multiline `for`. Does nothing. */
  def compute[T](code: => T): T = code

  /** Closes all passed resources after executing the passed code block, almost Java style.
    * Requires all resources to be defined caller-side. For alternatives see [[cleanly]]. */
  def TryWithResources[T](resources: AutoCloseable*)(block: => T): Try[T] = {
    Try(block) andFinally resources.foreach(_.close())
  }

  /** Takes a resource object (could be an n-tuple of resources), a cleanup procedure
    * (eg. `_.close()`) and a function mapping from the resource to your dependent code.
    * You can pass a partial function literal for `doWork` to use extractors properly if
    * passing a more complex resource object. */
  def cleanly[A, B](resource: A)(cleanup: A => Unit)(doWork: A => B): Try[B] = {
    Try(doWork(resource)) andFinally cleanup(resource)
  }

  implicit class UnorderedPairConverter[A](val t: (A, A)) extends AnyVal {
    /** Converts the appended tuple into an UnorderedPair. */
    def unary_~ : UnorderedPair[A] = UnorderedPair(t)
  }

  /** Tuple without order, e.g. for use as keys in a map or an undirected edge in a graph.
    * Order of the elements does not matter. You can prefix a tuple with `~` for convenience. */
  case class UnorderedPair[A](t: (A, A)) {
    override def equals(o: Any): Boolean = o match {
      case that: UnorderedPair[A] => t match {
        case (a,b) => that.t._1 == a && that.t._2 == b || that.t._1 == b && that.t._2 == a }
      case _ => false
    }
    override def hashCode: Int = t._1.hashCode * t._2.hashCode // commutative and unique
  }

  implicit class RichSeq[A, B <: Seq[A]](val v: B) extends AnyVal {
    /** (!) Inefficient (!) Equal to `v.reverse.dropWhile(pred).reverse` */
    def dropRightWhile(pred: A => Boolean): Seq[A] = v.reverse.dropWhile(pred).reverse
  }

  implicit class RichMap[A, B](val map: Map[A, B]) extends AnyVal {
    def mapKeys[A1](f: A => A1): Map[A1, B] = map.map({ case (a, b) => (f(a), b) })
  }

  object D {
    def unapply(arg: Any): Option[Double] =
      arg match {
        case d: Double => Some(d)
        case d: String => Try(d.toDouble).toOption
        case _ => None
      }}
  object F {
    def unapply(arg: Any): Option[Float] =
      arg match {
        case d: Float => Some(d)
        case d: String => Try(d.toFloat).toOption
        case _ => None
      }}
  object I {
    def unapply(arg: Any): Option[Int] =
      arg match {
        case d: Int => Some(d)
        case d: String => Try(d.toInt).toOption
        case _ => None
      }}
  object L {
    def unapply(arg: Any): Option[Long] =
      arg match {
        case d: Long => Some(d)
        case d: String => Try(d.toLong).toOption
        case _ => None
      }}
  object S {
    def unapply(arg: Any): Option[Short] =
      arg match {
        case d: Short => Some(d)
        case d: String => Try(d.toShort).toOption
        case _ => None
      }}
  object B {
    def unapply(arg: Any): Option[Byte] =
      arg match {
        case d: Byte => Some(d)
        case d: String => Try(d.toByte).toOption
        case _ => None
      }}
  object C {
    def unapply(arg: Any): Option[Char] =
      arg match {
        case d: Char => Some(d)
        case d: String if d.length == 1 => Try(d.head).toOption
        case _ => None
      }}

}
