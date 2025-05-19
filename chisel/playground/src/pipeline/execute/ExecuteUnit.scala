package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.CpuConfig
import cpu.defines._
import cpu.defines.Const._
import cpu.defines.AXIParam._
import chisel3.util.experimental.BoringUtils

class ExecuteUnit extends Module {
  val io = IO(new Bundle {
    val executeStage = Input(new DecodeUnitExecuteUnit())
    val memoryStage  = Output(new ExecuteUnitMemoryUnit())
    val dcache       = new DCacheIO()
    
    val flush = Output(Bool())
    val target = Output(UInt(XLEN.W))
    
    val interrupt = Input(new ExtInterrupt())
    
    val mode = Output(UInt(2.W))
    val interrupt_out = Output(Vec(INT_WID, Bool()))
  })

  // 执行阶段完成指令的执行操作
  val fu = Module(new Fu()).io
  fu.data.pc       := io.executeStage.data.pc
  fu.data.info     := io.executeStage.data.info
  fu.data.src_info := io.executeStage.data.src_info
  fu.data.ex       := io.executeStage.data.ex
  fu.interrupt     := io.interrupt

  io.dcache.en    := fu.dataSram.en
  io.dcache.wen   := fu.dataSram.wen =/= 0.U
  io.dcache.size  := MuxLookup(fu.dataSram.wen, 0.U)(Seq(
    "b00000001".U -> AXI_SIZE_1B,
    "b00000011".U -> AXI_SIZE_2B,
    "b00001111".U -> AXI_SIZE_4B,
    "b11111111".U -> AXI_SIZE_8B
  ))
  io.dcache.addr  := fu.dataSram.addr
  io.dcache.wdata := fu.dataSram.wdata
  io.dcache.wstrb := fu.dataSram.wen
  io.dcache.finish_single_request := true.B

  fu.dataSram.rdata := io.dcache.rdata

  io.mode  := fu.mode
  io.flush := fu.flush
  io.target := fu.target
  io.interrupt_out := fu.interrupt_out

  val mem_data = Wire(new ExeMemData())
  mem_data.pc := fu.data.pc
  mem_data.info := fu.data.info
  mem_data.info.reg_wen := fu.reg_wen_out
  mem_data.rd_info := fu.data.rd_info
  mem_data.has_exception := fu.has_exception

  val src_info_mem = Wire(new SrcInfo())
  src_info_mem.src1_data := fu.data.src_info.src1_data
  src_info_mem.src2_data := io.dcache.rdata
  
  mem_data.src_info := src_info_mem
  io.memoryStage.data := mem_data
}