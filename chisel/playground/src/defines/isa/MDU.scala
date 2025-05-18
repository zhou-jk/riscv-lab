package cpu.defines

import chisel3._
import chisel3.util._

object MDUOpType {
  def mul    = "b0000".U
  def mulh   = "b0001".U
  def mulhsu = "b0010".U
  def mulhu  = "b0011".U
  def mulw   = "b1000".U

  def div    = "b0100".U
  def divu   = "b0101".U
  def rem    = "b0110".U
  def remu   = "b0111".U
  def divw   = "b1100".U
  def divuw  = "b1101".U
  def remw   = "b1110".U
  def remuw  = "b1111".U

  def isDiv(op: UInt) = op(2)
  def isDivSign(op: UInt) = isDiv(op) && !op(0)
  def isWordOp(op: UInt) = op(3)
  def isMul(op: UInt) = !op(2)
}


object RV64MInstr extends HasInstrType {
  def MUL    = BitPat("b0000001_?????_?????_000_?????_0110011")
  def MULH   = BitPat("b0000001_?????_?????_001_?????_0110011")
  def MULHSU = BitPat("b0000001_?????_?????_010_?????_0110011")
  def MULHU  = BitPat("b0000001_?????_?????_011_?????_0110011")
  def MULW   = BitPat("b0000001_?????_?????_000_?????_0111011")

  def DIV    = BitPat("b0000001_?????_?????_100_?????_0110011")
  def DIVU   = BitPat("b0000001_?????_?????_101_?????_0110011")
  def REM    = BitPat("b0000001_?????_?????_110_?????_0110011")
  def REMU   = BitPat("b0000001_?????_?????_111_?????_0110011")
  def DIVW   = BitPat("b0000001_?????_?????_100_?????_0111011")
  def DIVUW  = BitPat("b0000001_?????_?????_101_?????_0111011")
  def REMW   = BitPat("b0000001_?????_?????_110_?????_0111011")
  def REMUW  = BitPat("b0000001_?????_?????_111_?????_0111011")

  val table = Array(
    MUL    -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.mul),
    MULH   -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.mulh),
    MULHSU -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.mulhsu),
    MULHU  -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.mulhu),
    MULW   -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.mulw),

    DIV    -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.div),
    DIVU   -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.divu),
    REM    -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.rem),
    REMU   -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.remu),
    DIVW   -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.divw),
    DIVUW  -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.divuw),
    REMW   -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.remw),
    REMUW  -> List(InstrR, FuType.mdu, 0.U(1.W) ## MDUOpType.remuw)
  )
}