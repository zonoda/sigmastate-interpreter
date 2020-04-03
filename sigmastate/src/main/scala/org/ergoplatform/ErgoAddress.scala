package org.ergoplatform

import java.util

import com.google.common.primitives.Ints
import org.ergoplatform.ErgoAddressEncoder.NetworkPrefix
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.util.encode.Base58
import sigmastate.Values._
import sigmastate._
import sigmastate.basics.DLogProtocol.{ProveDlog, ProveDlogProp}
import sigmastate.lang.exceptions.SigmaException
import sigmastate.serialization._
import sigmastate.utxo.{DeserializeContext, Slice}
import special.collection.Coll

import scala.util.Try

/**
  * An address is a short string corresponding to some script used to protect a box. Unlike (string-encoded) binary
  * representation of a script, an address has some useful characteristics:
  *
  * - Integrity of an address could be checked., as it is incorporating a checksum.
  * - A prefix of address is showing network and an address type.
  * - An address is using an encoding (namely, Base58) which is avoiding similarly l0Oking characters, friendly to
  * double-clicking and line-breaking in emails.
  *
  *
  *
  * An address is encoding network type, address type, checksum, and enough information to watch for a particular scripts.
  *
  * Possible network types are:
  * Mainnet - 0x00
  * Testnet - 0x10
  *
  * Address types are, semantics is described below:
  * 0x01 - Pay-to-PublicKey(P2PK) address
  * 0x02 - Pay-to-Script-Hash(P2SH)
  * 0x03 - Pay-to-Script(P2S)
  *
  * For an address type, we form content bytes as follows:
  *
  * P2PK - serialized (compressed) public key
  * P2SH - first 192 bits of the Blake2b256 hash of serialized script bytes
  * P2S  - serialized script
  *
  * Address examples for testnet:
  *
  * 3   - P2PK (3WvsT2Gm4EpsM9Pg18PdY6XyhNNMqXDsvJTbbf6ihLvAmSb7u5RN)
  * ?   - P2SH (rbcrmKEYduUvADj9Ts3dSVSG27h54pgrq5fPuwB)
  * ?   - P2S (Ms7smJwLGbUAjuWQ)
  *
  * for mainnet:
  *
  * 9  - P2PK (9fRAWhdxEsTcdb8PhGNrZfwqa65zfkuYHAMmkQLcic1gdLSV5vA)
  * ?  - P2SH (8UApt8czfFVuTgQmMwtsRBZ4nfWquNiSwCWUjMg)
  * ?  - P2S (4MQyML64GnzMxZgm, BxKBaHkvrTvLZrDcZjcsxsF7aSsrN73ijeFZXtbj4CXZHHcvBtqSxQ)
  *
  *
  * Prefix byte = network type + address type
  *
  * checksum = blake2b256(prefix byte ++ content bytes)
  *
  * address = prefix byte ++ content bytes ++ checksum
  *
  */

sealed trait ErgoAddress {
  val addressTypePrefix: Byte

  val contentBytes: Array[Byte]

  val script: ErgoTree
}

class P2PKAddress(val pubkey: ProveDlog,
                  val pubkeyBytes: Array[Byte])
                 (implicit val encoder: ErgoAddressEncoder) extends ErgoAddress {

  override val addressTypePrefix: Byte = P2PKAddress.addressTypePrefix

  override val contentBytes: Array[Byte] = pubkeyBytes

  override val script: ErgoTree = {
    // NOTE: we don't segregate constants because the whole tree is single constant
    // and we want different addreses of this type to have different `script` values
    ErgoTree(ErgoTree.DefaultHeader, ErgoTree.EmptyConstants, SigmaPropConstant(pubkey))
  }

  override def equals(obj: Any): Boolean = obj match {
    case p2pk: P2PKAddress => util.Arrays.equals(pubkeyBytes, p2pk.pubkeyBytes)
    case _ => false
  }

  override def hashCode(): Int = Ints.fromByteArray(pubkeyBytes)

  override def toString: String = encoder.toString(this)
}

object P2PKAddress {
  val addressTypePrefix: Byte = 1: Byte

  def apply(pubkey: ProveDlog)(implicit encoder: ErgoAddressEncoder): P2PKAddress = {
    val bs = GroupElementSerializer.toBytes(pubkey.h)
    new P2PKAddress(pubkey, bs)
  }
}

class Pay2SHAddress(val scriptHash: Array[Byte])(implicit val encoder: ErgoAddressEncoder) extends ErgoAddress {
  override val addressTypePrefix: Byte = Pay2SHAddress.addressTypePrefix

  override val contentBytes: Array[Byte] = scriptHash

  import Pay2SHAddress._

  /** The proposition which checks that `contextVar(1)` has original script,
    * which evaluates to true and also whose hash equals to this `scriptHash`.
    * Assumes the context variable accessed as getVar[Coll[Byte]](1)` to contain serialized original script bytes.
    * @see ErgoLikeInterpreterSpecification."P2SH - 160 bits" test
    * similar script checked in "P2SH - 160 bits" test in sigma repository, but here we use 192 bits
    **/
  override val script = {
    val hashEquals = EQ(
      Slice(CalcBlake2b256(GetVarByteArray(scriptId).get), IntConstant(0), IntConstant(24)),
      scriptHash
    )
    val scriptIsCorrect = DeserializeContext(scriptId, SSigmaProp)
    ErgoTree.withoutSegregation(SigmaAnd(hashEquals.toSigmaProp, scriptIsCorrect))
  }

  override def equals(obj: Any): Boolean = obj match {
    case p2sh: Pay2SHAddress => util.Arrays.equals(scriptHash, p2sh.scriptHash)
    case _ => false
  }

  override def hashCode(): Int = Ints.fromByteArray(scriptHash)

  override def toString: String = encoder.toString(this)
}

object Pay2SHAddress {
  val scriptId = 1: Byte
  val addressTypePrefix: Byte = 2: Byte

  /** Create Pay-to-script-hash address with the given underlying script (ErgoTree).
    *
    * @param  script  ErgoTree representation of guarding script
    * @param  encoder address encoder which is used to encode address bytes as String */
  def apply(script: ErgoTree)(implicit encoder: ErgoAddressEncoder): Pay2SHAddress = {
    val prop = script.toProposition(replaceConstants = script.isConstantSegregation)
    apply(prop)
  }

  /** Create Pay-to-script-hash address with the given underlying proposition (SigmaPropValue).
    *
    * @param  prop    Value representation of guarding script (aka proposition)
    * @param  encoder address encoder which is used to encode address bytes as String */
  def apply(prop: SigmaPropValue)(implicit encoder: ErgoAddressEncoder): Pay2SHAddress = {
    val sb = ValueSerializer.serialize(prop)
    val sbh = ErgoAddressEncoder.hash192(sb)
    new Pay2SHAddress(sbh)
  }
}

class Pay2SAddress(override val script: ErgoTree,
                   val scriptBytes: Array[Byte])
                  (implicit val encoder: ErgoAddressEncoder) extends ErgoAddress {
  override val addressTypePrefix: Byte = Pay2SAddress.addressTypePrefix

  override val contentBytes: Array[Byte] = scriptBytes

  override def equals(obj: Any): Boolean = obj match {
    case p2s: Pay2SAddress => util.Arrays.equals(scriptBytes, p2s.scriptBytes)
    case _ => false
  }

  override def hashCode(): Int = Ints.fromByteArray(scriptBytes)

  override def toString: String = encoder.toString(this)
}

object Pay2SAddress {
  val addressTypePrefix: Byte = 3: Byte

  def apply(script: ErgoTree)(implicit encoder: ErgoAddressEncoder): Pay2SAddress = {
    val sb = ErgoTreeSerializer.DefaultSerializer.serializeErgoTree(script)
    new Pay2SAddress(script, sb)
  }
}


case class ErgoAddressEncoder(networkPrefix: NetworkPrefix) {

  import ErgoAddressEncoder._

  implicit private val ergoAddressEncoder: ErgoAddressEncoder = this

  val ChecksumLength = 4

  def toString(address: ErgoAddress): String = {
    val withNetworkByte = (networkPrefix + address.addressTypePrefix).toByte +: address.contentBytes

    val checksum = hash256(withNetworkByte).take(ChecksumLength)
    Base58.encode(withNetworkByte ++ checksum)
  }

  def isTestnetAddress(addrHeadByte: Byte): Boolean = addrHeadByte > TestnetNetworkPrefix

  def isMainnetAddress(addrHeadByte: Byte): Boolean = addrHeadByte < TestnetNetworkPrefix

  def fromString(addrStr: String): Try[ErgoAddress] = Base58.decode(addrStr).flatMap { bytes =>
    Try {
      val headByte = bytes.head
      networkPrefix match {
        case TestnetNetworkPrefix => require(isTestnetAddress(headByte), "Trying to decode mainnet address in testnet")
        case MainnetNetworkPrefix => require(isMainnetAddress(headByte), "Trying to decode testnet address in mainnet")
      }
      val addressType = (headByte - networkPrefix).toByte
      val (withoutChecksum, checksum) = bytes.splitAt(bytes.length - ChecksumLength)

      if (!util.Arrays.equals(hash256(withoutChecksum).take(ChecksumLength), checksum)) {
        throw new Exception(s"Checksum check fails for $addrStr")
      }

      val contentBytes = withoutChecksum.tail

      addressType match {
        case P2PKAddress.addressTypePrefix =>
          val r = SigmaSerializer.startReader(contentBytes)
          val p = GroupElementSerializer.parse(r)
          new P2PKAddress(ProveDlog(p), contentBytes)
        case Pay2SHAddress.addressTypePrefix =>
          if (contentBytes.length != 24) { //192-bits hash used
            throw new Exception(s"Improper content in P2SH script: $addrStr")
          }
          new Pay2SHAddress(contentBytes)
        case Pay2SAddress.addressTypePrefix =>
          val tree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(contentBytes)
          new Pay2SAddress(tree, contentBytes)
        case _ =>
          throw new Exception(s"Unsupported address type: $addressType")
      }
    }
  }

  object IsPay2SHAddress {
    def unapply(exp: SigmaPropValue): Option[Coll[Byte]] = exp match {
      case SigmaAnd(Seq(
            BoolToSigmaProp(
              EQ(
                Slice(_: CalcHash, ConstantNode(0, SInt), ConstantNode(24, SInt)),
                ByteArrayConstant(scriptHash))),
            DeserializeContext(Pay2SHAddress.scriptId, SSigmaProp))) => Some(scriptHash)
      case _ => None
    }
  }

  def fromProposition(proposition: ErgoTree): Try[ErgoAddress] = Try {
    proposition.root match {
      case Right(SigmaPropConstant(ProveDlogProp(d))) => P2PKAddress(d)
      case Right(IsPay2SHAddress(scriptHash)) => new Pay2SHAddress(scriptHash.toArray)
      case Right(b: Value[SSigmaProp.type]@unchecked) if b.tpe == SSigmaProp => Pay2SAddress(proposition)
      case Left(unparsedErgoTree) =>
        throw new SigmaException(s"Cannot create ErgoAddress form unparsed ergo tree: $unparsedErgoTree")
      case _ =>
        throw new RuntimeException(s"Cannot create ErgoAddress form proposition: $proposition")
    }
  }
}

object ErgoAddressEncoder {
  type NetworkPrefix = Byte
  val MainnetNetworkPrefix: NetworkPrefix = 0.toByte
  val TestnetNetworkPrefix: NetworkPrefix = 16.toByte

  def hash256(input: Array[Byte]): Digest32 = Blake2b256(input)

  def hash192(input: Array[Byte]): Array[Byte] = hash256(input).take(24)
}