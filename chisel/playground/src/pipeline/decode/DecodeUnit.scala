package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class DecodeUnit extends Module {
  val io = IO(new Bundle {
    // 输入
    val decodeStage = Flipped(new FetchUnitDecodeUnit())
    val regfile     = new Src12Read()
    // 输出
    val executeStage = Output(new DecodeUnitExecuteUnit())
  })

  // 译码阶段完成指令的译码操作以及源操作数的准备

  val decoder = Module(new Decoder()).io
  decoder.in.inst := io.decodeStage.data.inst

  val pc   = io.decodeStage.data.pc
  val pipeline_valid = io.decodeStage.data.valid

  val info = Wire(new Info())

  info       := decoder.out.info
  info.valid := pipeline_valid && io.decodeStage.data.valid

  val inst_opcode = io.decodeStage.data.inst(6, 0)
  val is_lui = inst_opcode === RV32I_ALUInstr.LUI_OPCODE
  val is_auipc = inst_opcode === RV32I_ALUInstr.AUIPC_OPCODE

  val src1_non_reg = Mux(is_lui, 0.U(XLEN.W), Mux(is_auipc, pc, 0.U(XLEN.W)))

  val final_src1_data = Mux(info.src1_ren, io.regfile.src1.rdata, src1_non_reg)
  val final_src2_data = Mux(info.src2_ren, io.regfile.src2.rdata, info.imm)

  // 完成寄存器堆的读取
  io.regfile.src1.raddr := Mux(info.src1_ren, info.src1_raddr, 0.U)
  io.regfile.src2.raddr := Mux(info.src2_ren, info.src2_raddr, 0.U)

  // 完成DecodeUnit模块的逻辑
  io.executeStage.data.pc                 := pc
  io.executeStage.data.info               := info
  io.executeStage.data.src_info.src1_data := final_src1_data
  io.executeStage.data.src_info.src2_data := final_src2_data
}
