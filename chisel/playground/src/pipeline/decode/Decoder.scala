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

  // 为不同指令格式准备立即数
  val imm_i = inst(31, 20)
  val imm_s = Cat(inst(31, 25), inst(11, 7))
  val imm_u = inst(31, 12)
  val imm_b = Cat(inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
  val imm_j = Cat(inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))

  val imm_i_sext = Cat(Fill(XLEN - 12, imm_i(11)), imm_i).asUInt
  val imm_s_sext = Cat(Fill(XLEN - 12, imm_s(11)), imm_s).asUInt
  val imm_u_sext = Cat(Fill(XLEN - 32, imm_u(19)), imm_u, 0.U(12.W)).asUInt
  val imm_b_sext = Cat(Fill(XLEN - 13, imm_b(11)), imm_b).asUInt
  val imm_j_sext = Cat(Fill(XLEN - 21, imm_j(19)), imm_j).asUInt

  val final_imm = MuxLookup(instrType, 0.U(XLEN.W))(Seq(
    InstrI -> imm_i_sext,
    InstrS -> imm_s_sext,
    InstrU -> imm_u_sext,
    InstrB -> imm_b_sext,
    InstrJ -> imm_j_sext
  ))

  val src1_ren = MuxLookup(instrType, false.B)(Seq(
    InstrR -> true.B,
    InstrI -> true.B,
    InstrS -> true.B,
    InstrB -> true.B,
    InstrJ -> false.B
  ))

  val src2_ren = MuxLookup(instrType, false.B)(Seq(
    InstrR -> true.B,
    InstrI -> false.B,
    InstrS -> true.B,
    InstrB -> true.B,
    InstrJ -> false.B
  ))

  // 完成Decoder模块的逻辑
  val is_valid_instr_type = instrType =/= InstrN
  io.out.info.valid      := is_valid_instr_type
  io.out.info.src1_raddr := rs
  io.out.info.src2_raddr := rt
  io.out.info.op         := fuOpType
  io.out.info.fusel      := fuType
  io.out.info.reg_wen    := isRegWen(instrType) && is_valid_instr_type
  io.out.info.reg_waddr  := rd

  io.out.info.imm        := final_imm
  io.out.info.src1_ren   := src1_ren
  io.out.info.src2_ren   := src2_ren
  io.out.info.inst       := inst
}
