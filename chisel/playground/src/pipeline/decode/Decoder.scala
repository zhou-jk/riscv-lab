package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Decoder extends Module with HasInstrType {
  val io = IO(new Bundle {
    // inputs
    val in = Input(new Bundle {
      val inst = UInt(XLEN.W)
    })
    // outputs
    val out = Output(new Bundle {
      val info = new Info()
    })
  })

  val inst = io.in.inst
  // 根据输入的指令inst从Instructions.DecodeTable中查找对应的指令类型、功能单元类型和功能单元操作类型
  // 如果找不到匹配的指令，则使用Instructions.DecodeDefault作为默认值
  // instrType、fuType和fuOpType分别被赋值为Instructions.DecodeTable中的对应值
  val instrType :: fuType :: fuOpType :: Nil =
    ListLookup(inst, Instructions.DecodeDefault, Instructions.DecodeTable)

  val (rs, rt, rd) = (inst(19, 15), inst(24, 20), inst(11, 7))

  // 完成Decoder模块的逻辑
  val is_valid_instr_type = instrType =/= InstrN
  io.out.info.valid      := is_valid_instr_type
  io.out.info.src1_raddr := rs
  io.out.info.src2_raddr := rt
  io.out.info.op         := fuOpType
  io.out.info.reg_wen    := isRegWen(instrType) && is_valid_instr_type
  io.out.info.reg_waddr  := rd
}
