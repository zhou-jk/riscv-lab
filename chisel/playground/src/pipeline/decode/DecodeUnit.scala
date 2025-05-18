package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class DecodeUnit extends Module with HasExceptionNO {
  val io = IO(new Bundle {
    // 输入
    val decodeStage = Flipped(new FetchUnitDecodeUnit())
    val regfile     = new Src12Read()
    
    // 前递数据接口
    val exeForwardInfo = Input(new ForwardInfo())
    val memForwardInfo = Input(new ForwardInfo())
    val wbForwardInfo  = Input(new ForwardInfo())
    
    val exeForwardData = Input(new ForwardData())
    val memForwardData = Input(new ForwardData())
    val wbForwardData  = Input(new ForwardData())
    
    // 输出
    val executeStage = Output(new DecodeUnitExecuteUnit())
    
    val mode = Input(UInt(2.W))
    val interrupt = Input(Vec(INT_WID, Bool()))
  })

  // 译码阶段完成指令的译码操作以及源操作数的准备

  val decoder = Module(new Decoder()).io
  decoder.in.inst := io.decodeStage.data.inst

  val pc = io.decodeStage.data.pc
  val pipeline_valid = io.decodeStage.data.valid

  val info = Wire(new Info())

  info       := decoder.out.info
  info.valid := pipeline_valid && io.decodeStage.data.valid

  val inst_opcode = io.decodeStage.data.inst(6, 0)
  val is_lui = inst_opcode === RV32I_ALUInstr.LUI_OPCODE
  val is_auipc = inst_opcode === RV32I_ALUInstr.AUIPC_OPCODE

  // 完成寄存器堆的读取
  io.regfile.src1.raddr := Mux(info.src1_ren, info.src1_raddr, 0.U)
  io.regfile.src2.raddr := Mux(info.src2_ren, info.src2_raddr, 0.U)

  val forwardCtrl = Module(new ForwardCtrl()).io

  forwardCtrl.src1.src_ren := info.src1_ren
  forwardCtrl.src1.src_raddr := info.src1_raddr
  forwardCtrl.src2.src_ren := info.src2_ren
  forwardCtrl.src2.src_raddr := info.src2_raddr

  forwardCtrl.exe_info := io.exeForwardInfo
  forwardCtrl.mem_info := io.memForwardInfo
  forwardCtrl.wb_info := io.wbForwardInfo

  forwardCtrl.exe_data := io.exeForwardData
  forwardCtrl.mem_data := io.memForwardData
  forwardCtrl.wb_data := io.wbForwardData

  forwardCtrl.reg_src1_data := io.regfile.src1.rdata
  forwardCtrl.reg_src2_data := io.regfile.src2.rdata

  val src1_non_reg = Mux(is_lui, 0.U(XLEN.W), Mux(is_auipc, pc, 0.U(XLEN.W)))
  
  val final_src1_data = Mux(info.src1_ren, forwardCtrl.final_src1_data, src1_non_reg)
  val final_src2_data = Mux(info.src2_ren, forwardCtrl.final_src2_data, info.imm)

  val ex = Wire(new ExceptionInfo())
  ex.exception := VecInit(Seq.fill(EXC_WID)(false.B))
  ex.interrupt := VecInit(Seq.fill(INT_WID)(false.B))
  ex.tval := VecInit(Seq.fill(EXC_WID)(0.U(XLEN.W)))
  
  when(pc(1, 0) =/= 0.U) {
    ex.exception(instAddrMisaligned) := true.B
    ex.tval(instAddrMisaligned) := pc
  }
  
  val inst_illegal = !decoder.out.legal
  when(inst_illegal) {
    ex.exception(illegalInst) := true.B
    ex.tval(illegalInst) := io.decodeStage.data.inst
  }
  
  when(info.valid && info.fusel === FuType.csr && info.op(3, 0) === CSROpType.ebreak) {
    ex.exception(breakPoint) := true.B
  }
  
  when(info.valid && info.fusel === FuType.csr && info.op(3, 0) === CSROpType.ecall) {
    when(io.mode === Priv.u) {
      ex.exception(ecallU) := true.B
    }.elsewhen(io.mode === Priv.m) {
      ex.exception(ecallM) := true.B
    }
  }
  
  ex.interrupt := io.interrupt


  io.executeStage.data.pc                 := pc
  io.executeStage.data.info               := info
  io.executeStage.data.src_info.src1_data := final_src1_data
  io.executeStage.data.src_info.src2_data := final_src2_data
  io.executeStage.data.ex                 := ex
}
