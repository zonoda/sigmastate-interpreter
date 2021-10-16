package sigmastate.eval

import java.math.BigInteger

import scalan.{ExactNumeric, ExactOrderingImpl, ExactIntegral}

import scala.math.{Integral, Ordering}
import special.sigma._
import sigmastate.eval.Extensions._

object OrderingOps {
  def apply[T](implicit ord: Ordering[T]) = ord

  trait BigIntegerOrdering extends Ordering[BigInteger] {
    def compare(x: BigInteger, y: BigInteger) = x.compareTo(y)
  }
  implicit object BigIntegerOrdering extends BigIntegerOrdering

  trait BigIntOrdering extends Ordering[BigInt] {
    def compare(x: BigInt, y: BigInt) = x.compareTo(y)
  }
  implicit object BigIntOrdering extends BigIntOrdering
}

object NumericOps {

  trait BigIntegerIsIntegral extends Integral[BigInteger] {
    def quot(x: BigInteger, y: BigInteger): BigInteger = x.divide(y)
    def rem(x: BigInteger, y: BigInteger): BigInteger = x.remainder(y)
    def plus(x: BigInteger, y: BigInteger): BigInteger = x.add(y)
    def minus(x: BigInteger, y: BigInteger): BigInteger = x.subtract(y)
    def times(x: BigInteger, y: BigInteger): BigInteger = x.multiply(y)
    def negate(x: BigInteger): BigInteger = x.negate()
    def fromInt(x: Int): BigInteger = BigInteger.valueOf(x)
    def toInt(x: BigInteger): Int = x.intValueExact()
    def toLong(x: BigInteger): Long = x.longValueExact()
    def toFloat(x: BigInteger): Float = x.floatValue()
    def toDouble(x: BigInteger): Double = x.doubleValue()
  }
  implicit object BigIntegerIsIntegral extends BigIntegerIsIntegral with OrderingOps.BigIntegerOrdering

  trait BigIntIsIntegral extends Integral[BigInt] {
    def quot(x: BigInt, y: BigInt): BigInt = x.divide(y)

    /** This method is used in ErgoTreeEvaluator based interpreter, to implement
      * '%' operation of ErgoTree (i.e. `%: (T, T) => T` operation) for all
      * numeric types T including BigInt.
      *
      * In the v4.x interpreter, however, the `%` operation is implemented using
      * [[CBigInt]].mod method (see implementation in [[TestBigInt]], which
      * delegates to [[java.math.BigInteger]].mod method.
      *
      * Even though this method is called `rem`, the semantics of ErgoTree
      * language requires it to correspond to [[java.math.BigInteger]].mod
      * method.
      *
      * For this reason we define implementation of this `rem` method using
      * [[BigInt]].mod.
      */
    def rem(x: BigInt, y: BigInt): BigInt = x.mod(y)

    def plus(x: BigInt, y: BigInt): BigInt = x.add(y)
    def minus(x: BigInt, y: BigInt): BigInt = x.subtract(y)
    def times(x: BigInt, y: BigInt): BigInt = x.multiply(y)
    def negate(x: BigInt): BigInt = x.negate()
    def fromInt(x: Int): BigInt = x.toBigInt
    def toInt(x: BigInt): Int = x.toInt
    def toLong(x: BigInt): Long = x.toLong
    def toFloat(x: BigInt): Float = CostingSigmaDslBuilder.toBigInteger(x).floatValue()
    def toDouble(x: BigInt): Double = CostingSigmaDslBuilder.toBigInteger(x).doubleValue()
  }

  /** The instance of Integral for BigInt.
    *
    * Note: ExactIntegral is not defined for [[special.sigma.BigInt]].
    * This is because arithmetic BigInt operations are handled specially
    * (see `case op: ArithOp[t] if op.tpe == SBigInt =>` in RuntimeCosting.scala).
    * As result [[scalan.primitives.UnBinOps.ApplyBinOp]] nodes are not created for BigInt
    * operations, and hence operation descriptors such as
    * [[scalan.primitives.NumericOps.IntegralDivide]] and
    * [[scalan.primitives.NumericOps.IntegralMod]] are not used for BigInt.
    */
  implicit object BigIntIsIntegral extends BigIntIsIntegral with OrderingOps.BigIntOrdering

  implicit object BigIntIsExactNumeric extends ExactNumeric[BigInt] {
    val n = BigIntIsIntegral
    override def plus(x: BigInt, y: BigInt): BigInt = n.plus(x, y)
    override def minus(x: BigInt, y: BigInt): BigInt = n.minus(x, y)
    override def times(x: BigInt, y: BigInt): BigInt = n.times(x, y)
  }

  implicit object BigIntIsExactIntegral extends ExactIntegral[BigInt] {
    val n = BigIntIsIntegral
    override def plus(x: BigInt, y: BigInt): BigInt = n.plus(x, y)
    override def minus(x: BigInt, y: BigInt): BigInt = n.minus(x, y)
    override def times(x: BigInt, y: BigInt): BigInt = n.times(x, y)
  }

  implicit object BigIntIsExactOrdering extends ExactOrderingImpl[BigInt](BigIntIsIntegral)
}

