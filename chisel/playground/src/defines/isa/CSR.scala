package cpu.defines

import chisel3._
import chisel3.util._

object CSROpType {
  def write  = "b0001".U
  def set    = "b0010".U
  def clear  = "b0011".U
  def writei = "b0101".U
  def seti   = "b0110".U
  def cleari = "b0111".U
  def ecall  = "b1000".U
  def ebreak = "b1001".U
  def mret   = "b1010".U
  def fence  = "b1011".U
  def wfi    = "b1100".U
  def isCSROp(op: UInt) = !op(3)
}

object RVCSRInstr extends HasInstrType {
  def CSRRW  = BitPat("b???????_?????_?????_001_?????_1110011")
  def CSRRS  = BitPat("b???????_?????_?????_010_?????_1110011")
  def CSRRC  = BitPat("b???????_?????_?????_011_?????_1110011")
  def CSRRWI = BitPat("b???????_?????_?????_101_?????_1110011")
  def CSRRSI = BitPat("b???????_?????_?????_110_?????_1110011")
  def CSRRCI = BitPat("b???????_?????_?????_111_?????_1110011")
  def ECALL  = BitPat("b0000000_00000_00000_000_00000_1110011")
  def EBREAK = BitPat("b0000000_00001_00000_000_00000_1110011")
  def MRET   = BitPat("b0011000_00010_00000_000_00000_1110011")
  def WFI    = BitPat("b0001000_00101_00000_000_00000_1110011")
  def FENCE  = BitPat("b???????_?????_?????_000_?????_0001111")

  val table = Array(
    CSRRW  -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.write),
    CSRRS  -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.set),
    CSRRC  -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.clear),
    CSRRWI -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.writei),
    CSRRSI -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.seti),
    CSRRCI -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.cleari),
    ECALL  -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.ecall),
    EBREAK -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.ebreak),
    MRET   -> List(InstrI, FuType.csr, 0.U(1.W) ## CSROpType.mret),
    WFI    -> List(InstrI, FuType.alu, ALUOpType.add),
    FENCE  -> List(InstrI, FuType.alu, ALUOpType.add)
  )
}

trait HasExceptionNO {
  def instAddrMisaligned = 0
  def instAccessFault = 1
  def illegalInst = 2
  def breakPoint = 3
  def loadAddrMisaligned = 4
  def loadAccessFault = 5
  def storeAddrMisaligned = 6
  def storeAccessFault = 7
  def ecallU = 8
  def ecallS = 9
  def ecallM = 11
  def instPageFault = 12
  def loadPageFault = 13
  def storePageFault = 15
  
  val ExcPriority = Seq(
    breakPoint,
    instPageFault,
    instAccessFault,
    illegalInst,
    instAddrMisaligned,
    ecallM,
    ecallS,
    ecallU,
    storeAddrMisaligned,
    loadAddrMisaligned,
    storePageFault,
    loadPageFault,
    storeAccessFault,
    loadAccessFault
  )
}