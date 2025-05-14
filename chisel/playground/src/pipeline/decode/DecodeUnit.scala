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
    
    // CSR相关信号
    val mode = Input(UInt(2.W))  // 当前特权模式
    val interrupt = Input(Vec(INT_WID, Bool())) // 中断信号
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

  // 异常检测
  val ex = Wire(new ExceptionInfo())
  ex.exception := VecInit(Seq.fill(EXC_WID)(false.B))
  ex.interrupt := VecInit(Seq.fill(INT_WID)(false.B))
  ex.tval := VecInit(Seq.fill(EXC_WID)(0.U(XLEN.W)))
  
  // 检测PC地址未对齐异常
  when(pc(1, 0) =/= 0.U) {
    ex.exception(instAddrMisaligned) := true.B
    ex.tval(instAddrMisaligned) := pc
  }
  
  // 非法指令异常
  val inst_illegal = !decoder.out.legal
  when(inst_illegal) {
    ex.exception(illegalInst) := true.B
    ex.tval(illegalInst) := io.decodeStage.data.inst
  }
  
  // 断点异常
  when(info.valid && info.fusel === FuType.csr && info.op(3, 0) === CSROpType.ebreak) {
    ex.exception(breakPoint) := true.B
  }
  
  // ECALL指令异常
  when(info.valid && info.fusel === FuType.csr && info.op(3, 0) === CSROpType.ecall) {
    when(io.mode === Priv.u) {
      ex.exception(ecallU) := true.B
    }.elsewhen(io.mode === Priv.m) {
      ex.exception(ecallM) := true.B
    }
  }
  
  // 中断
  ex.interrupt := io.interrupt

  // ADD PRINTF STATEMENTS HERE
  when(pc === "h800000dc".U(XLEN.W)) {
    val inst_in_decode_unit = io.decodeStage.data.inst
    val rd_extracted_in_decode_unit = inst_in_decode_unit(11,7)
    printf(p"DecodeUnit: pc=0x${Hexadecimal(pc)}, inst=0x${Hexadecimal(inst_in_decode_unit)}\n")
    printf(p"            decoder.out.legal=${decoder.out.legal}, inst_illegal=${inst_illegal}\n")
    printf(p"            ex.exception(illegalInst)=${ex.exception(illegalInst)}\n")
    printf(p"            info.valid=${info.valid}, info.reg_wen=${info.reg_wen}\n")
    printf(p"            info.fusel=${info.fusel}, info.op=${info.op}, info.reg_waddr=0x${Hexadecimal(info.reg_waddr)}\n")
    printf(p"            DEBUG_DU: rd_extracted_in_decode_unit = ${rd_extracted_in_decode_unit}\n")
  }

  io.executeStage.data.pc                 := pc
  io.executeStage.data.info               := info
  io.executeStage.data.src_info.src1_data := final_src1_data
  io.executeStage.data.src_info.src2_data := final_src2_data
  io.executeStage.data.ex                 := ex
}
