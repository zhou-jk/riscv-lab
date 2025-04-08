package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.CpuConfig
import cpu.defines._
import cpu.defines.Const._
import chisel3.util.experimental.BoringUtils

class ExecuteUnit extends Module {
  val io = IO(new Bundle {
    val executeStage = Input(new DecodeUnitExecuteUnit())
    val memoryStage  = Output(new ExecuteUnitMemoryUnit())
    val dataSram     = new DataSram()
  })

  // 执行阶段完成指令的执行操作
  val fu = Module(new Fu()).io
  fu.data.pc       := io.executeStage.data.pc
  fu.data.info     := io.executeStage.data.info
  fu.data.src_info := io.executeStage.data.src_info

  io.dataSram <> fu.dataSram

  val mem_data = Wire(new ExeMemData())
  mem_data.pc := fu.data.pc
  mem_data.info := fu.data.info
  mem_data.rd_info := fu.data.rd_info
  
  val src_info_mem = Wire(new SrcInfo())
  src_info_mem.src1_data := fu.data.src_info.src1_data
  src_info_mem.src2_data := io.dataSram.rdata
  
  mem_data.src_info := src_info_mem
  io.memoryStage.data := mem_data
}