package org.ergoplatform.dsl

import org.ergoplatform.ErgoBox.{BoxId, NonMandatoryRegisterId, TokenId}
import sigmastate.interpreter.{CostedProverResult, ProverResult}
import sigma.core.RType
import org.ergoplatform.{ErgoLikeContext, ErgoBox}
import sigma.{SigmaDslBuilder, AnyValue, SigmaProp}
import sigmastate.Values.ErgoTree
import sigmastate.eval.{IRContext, CostingSigmaDslBuilder}

import scala.util.Try
import org.ergoplatform.dsl.ContractSyntax.{ErgoScript, Proposition, Token}

import scala.language.implicitConversions

trait ContractSpec {
  val dsl: SigmaDslBuilder = CostingSigmaDslBuilder
  val Colls = dsl.Colls

  implicit def Coll[T](items: Array[T])(implicit cT: RType[T]) = Colls.fromArray(items)

  val IR: IRContext

  trait PropositionSpec {
    def name: String
    def dslSpec: Proposition
    def scriptSpec: ErgoScript
    def ergoTree: ErgoTree
  }
  object PropositionSpec {
    def apply(name: String, dslSpec: Proposition, scriptSpec: ErgoScript) = mkPropositionSpec(name, dslSpec, scriptSpec)
  }

  private[dsl] def mkPropositionSpec(name: String, dslSpec: Proposition, scriptSpec: ErgoScript): PropositionSpec


  trait ProtocolParty {
    def name: String
  }

  /** Represents a participant of blockchain scenario (protocol). Participants are identified by `pubKey`
    * and may have human readable names.
    * This type of participant can generate proof for input boxes. */
  trait ProvingParty extends ProtocolParty {
    /** Public key of this party represented as sigma protocol proposition.
      * Thus, it can be used in logical `&&`, `||` and `atLeast` propositions.
      * For example `(HEIGHT > 10 && bob.pubKey) || (HEIGHT <= 10 && alice.pubKey). */
    def pubKey: SigmaProp

    /** Generate proof for the given `inBox`. The input box has attached guarding proposition,
      * which is executed in the Context, specifically created for `inBox`.*/
    def prove(inBox: InputBox, extensions: Map[Byte, AnyValue] = Map()): Try[CostedProverResult]
  }
  object ProvingParty {
    def apply(name: String): ProvingParty = mkProvingParty(name)
  }
  protected def mkProvingParty(name: String): ProvingParty

  trait VerifyingParty extends ProtocolParty {
    /** Verifies the proof generated by the ProvingParty (using `prove` method) for the given `inBox`.*/
    def verify(inBox: InputBox, proverResult: ProverResult): Boolean
  }
  object VerifyingParty {
    def apply(name: String): VerifyingParty = mkVerifyingParty(name)
  }
  protected def mkVerifyingParty(name: String): VerifyingParty

  trait InputBox {
    def tx: TransactionCandidate
    def utxoBox: OutBox
    def runDsl(extensions: Map[Byte, AnyValue] = Map()): SigmaProp
    private [dsl] def toErgoContext: ErgoLikeContext
  }

  trait OutBox {
    def id: BoxId
    def tx: TransactionCandidate
    def boxIndex: Int
    def value: Long
    def propSpec: PropositionSpec
    def withTokens(tokens: Token*): OutBox
    def withRegs(regs: (NonMandatoryRegisterId, Any)*): OutBox
    def token(id: TokenId): Token
    private[dsl] def ergoBox: ErgoBox
  }

  trait TransactionCandidate {
    def block: BlockCandidate
    def dataInputs: Seq[InputBox]
    def inputs: Seq[InputBox]
    def outputs: Seq[OutBox]
    def inBox(utxoBox: OutBox): InputBox
    def outBox(value: Long, propSpec: PropositionSpec): OutBox
    def spending(utxos: OutBox*): TransactionCandidate
    def withDataInputs(dataBoxes: OutBox*): TransactionCandidate
  }

  trait ChainTransaction {
    def outputs: Seq[OutBox]
  }

  /** Block which is already in blockchain. */
  trait ChainBlock {
    def getTransactions(): Seq[ChainTransaction]
  }

  /** Block which serve as transaction context. */
  trait BlockCandidate {
    def height: Int
    def newTransaction(): TransactionCandidate
  }

  val MinErgValue = 1
  def error(msg: String) = sys.error(msg)

}
