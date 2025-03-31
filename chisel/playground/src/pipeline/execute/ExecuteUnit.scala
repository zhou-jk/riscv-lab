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

  // 完成ExecuteUnit模块的逻辑

  io.memoryStage.data.pc       := fu.data.pc
  io.memoryStage.data.info     := fu.data.info
  io.memoryStage.data.src_info := fu.data.src_info
  io.memoryStage.data.rd_info  := fu.data.rd_info
}
