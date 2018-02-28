package sigmastate

import java.math.BigInteger
import java.util.Objects

import edu.biu.scapi.primitives.dlog.GroupElement
import sigmastate.SType.TypeCode
import sigmastate.utxo.SigmaStateBox



sealed trait SType {
  type WrappedType
  val typeCode: SType.TypeCode
  def isPrimitive: Boolean = SType.allPrimitiveTypes.contains(this)
}

object SType {
  type TypeCode = Byte
  implicit val typeInt = SInt
  implicit val typeBigInt = SBigInt
  implicit val typeBoolean = SBoolean
  implicit val typeByteArray = SByteArray
  implicit val typeAvlTree = SAvlTree
  implicit val typeGroupElement = SGroupElement
  implicit val typeBox = SBox
  implicit def typeCollection[V <: SType](implicit tV: V): SCollection[V] = SCollection[V]

  val allPrimitiveTypes = Seq(SInt, SBigInt, SBoolean, SByteArray, SAvlTree, SGroupElement, SBox)
  val typeCodeToType = allPrimitiveTypes.map(t => t.typeCode -> t).toMap
}

/** Primitive type recognizer to pattern match on TypeCode */
object PrimType {
  def unapply(tc: TypeCode): Option[SType] = SType.typeCodeToType.get(tc)
}

case object SInt extends SType {
  override type WrappedType = Long

  override val typeCode = 1: Byte
}

case object SBigInt extends SType {
  override type WrappedType = BigInteger

  override val typeCode = 2: Byte
}

case object SBoolean extends SType {
  override type WrappedType = Boolean

  override val typeCode = 3: Byte
}

case object SByteArray extends SType {
  override type WrappedType = Array[Byte]

  override val typeCode = 4: Byte
}

case object SAvlTree extends SType {
  override type WrappedType = AvlTreeData

  override val typeCode = 5: Byte
}

case object SGroupElement extends SType {
  override type WrappedType = GroupElement

  override val typeCode: Byte = 6: Byte
}

case object SBox extends SType {
  override type WrappedType = SigmaStateBox

  override val typeCode: Byte = 7: Byte
}

case class  SCollection[ElemType <: SType]()(implicit val elemType: ElemType) extends SType {
  override type WrappedType = IndexedSeq[Value[ElemType]]

  override val typeCode = SCollection.TypeCode

  override def equals(obj: scala.Any) = obj match {
    case that: SCollection[_] => that.elemType == elemType
    case _ => false
  }
  override def hashCode() = (31 + typeCode) * 31 + elemType.hashCode()
}

object SCollection {
  val TypeCode = 80: Byte

  //todo: SCollection[SCollection[]] is not possible!
  def collectionOf(typeCode: TypeCode) = (TypeCode + typeCode).toByte
}
