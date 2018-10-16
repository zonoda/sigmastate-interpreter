package sigmastate.eval

import sigmastate.SType
import sigmastate.Values.Constant
import special.collection.{ConcreteCostedBuilder, Col, Types}
import special.sigma._

import scala.reflect.ClassTag
import scalan.meta.RType

class CostingBox(
    val IR: Evaluation,
    id: Col[Byte],
    value: Long,
    bytes: Col[Byte],
    bytesWithoutRef: Col[Byte],
    propositionBytes: Col[Byte],
    registers: Col[AnyValue],
    var isCost: Boolean) extends TestBox(id, value, bytes, bytesWithoutRef, propositionBytes, registers) {
  override val builder = new CostingSigmaDslBuilder(IR)

  override def getReg[T](i: Int)(implicit cT: RType[T]): Option[T] =
    if (isCost) {
      val optV =
        if (i < 0 || i >= registers.length) None
        else {
          val value = registers(i)
          if (value != null ) {
            // once the value is not null it should be of the right type
            value match {
              case value: TestValue[_] if value.value != null =>
                Some(value.value.asInstanceOf[T])
              case _ =>
                None
            }
          } else None
        }

      optV.orElse {
        val tpe = IR.elemToSType(cT.asInstanceOf[IR.Elem[_]])
        val default = builder.Costing.defaultValue(cT).asInstanceOf[SType#WrappedType]
        Some(Constant[SType](default, tpe).asInstanceOf[T])
      }
    } else
      super.getReg(i)
}

class CostingSigmaDslBuilder(val IR: Evaluation) extends TestSigmaDslBuilder { dsl =>
  override val Costing = new ConcreteCostedBuilder {
    import RType._
    override def defaultValue[T](valueType: RType[T]): T = (valueType match {
      case ByteType | IR.ByteElement  => 0.toByte
      case ShortType | IR.ShortElement=> 0.toShort
      case IntType | IR.IntElement  => 0
      case LongType | IR.LongElement => 0L
      case StringType | IR.StringElement => ""
      case p: PairRType[a, b] => (defaultValue(p.tA), defaultValue(p.tB))
      case col: Types.ColRType[a] => dsl.Cols.fromArray(Array[a]()(col.tA.classTag))
      case p: IR.PairElem[a, b] => (defaultValue(p.eFst), defaultValue(p.eSnd))
      case col: IR.Col.ColElem[a,_] => dsl.Cols.fromArray(Array[a]()(col.eItem.classTag))
      case _ => sys.error(s"Cannot create defaultValue($valueType)")
    }).asInstanceOf[T]
  }
}

class CostingDataContext(
    val IR: Evaluation,
    inputs: Array[Box],
    outputs: Array[Box],
    height: Long,
    selfBox: Box,
    lastBlockUtxoRootHash: AvlTree,
    vars: Array[AnyValue],
    var isCost: Boolean)
    extends TestContext(inputs, outputs, height, selfBox, lastBlockUtxoRootHash, vars)
{
  override val builder = new CostingSigmaDslBuilder(IR)

  override def getVar[T](id: Byte)(implicit cT: RType[T]) =
    if (isCost) {
      implicit val tag: ClassTag[T] = cT.classTag
      val optV =
        if (id < 0 || id >= vars.length) None
        else {
          val value = vars(id)
          if (value != null ) {
            // once the value is not null it should be of the right type
            value match {
              case value: TestValue[_] if value.value != null =>
                Some(value.value.asInstanceOf[T])
              case _ => None
            }
          } else None
        }
      optV.orElse {
        val tpe = IR.elemToSType(cT.asInstanceOf[IR.Elem[_]])
        val default = builder.Costing.defaultValue(cT).asInstanceOf[SType#WrappedType]
        Some(Constant[SType](default, tpe).asInstanceOf[T])
      }
    } else
      super.getVar(id)(cT)
}
