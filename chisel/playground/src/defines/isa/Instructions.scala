package cpu.defines

import chisel3._
import chisel3.util._

// 指令类型
trait HasInstrType {
  def InstrN = "b000".U
  def InstrI = "b100".U
  def InstrR = "b101".U
  def InstrS = "b010".U
  def InstrB = "b001".U
  def InstrU = "b110".U
  def InstrJ = "b111".U

  // I、R、U、J类型的指令都需要写寄存器
  def isRegWen(instrType: UInt): Bool = instrType(2)
}

// 功能单元类型 Function Unit Type
object FuType {
  def num     = 2
  def alu     = 0.U // arithmetic logic unit
  def mdu     = 1.U // multiplication-division unit
  def apply() = UInt(log2Up(num).W)
}

// 功能单元操作类型 Function Unit Operation Type
object FuOpType {
  def apply() = UInt(5.W) // 宽度与最大的功能单元操作类型宽度一致
}

// 算术逻辑单元操作类型 Arithmetic Logic Unit Operation Type
object ALUOpType {
  def add  = "b00000".U
  def sub  = "b01000".U
  def sll  = "b00001".U
  def slt  = "b00010".U
  def sltu = "b00011".U
  def xor  = "b00100".U
  def srl  = "b00101".U
  def sra  = "b01101".U
  def or   = "b00110".U
  def and  = "b00111".U
  def addw = "b10000".U
  def subw = "b11000".U
  def sllw = "b10001".U
  def srlw = "b10101".U
  def sraw = "b11101".U

  def isWordOp(func: UInt) = func(4)
}
