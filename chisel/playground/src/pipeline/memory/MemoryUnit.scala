package cpu.pipeline

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class MemoryUnit extends Module {
  val io = IO(new Bundle {
    val memoryStage    = Input(new ExecuteUnitMemoryUnit())
    val rdata          = Input(UInt(XLEN.W))
    val writeBackStage = Output(new MemoryUnitWriteBackUnit())
  })

  // 访存阶段完成指令的访存操作
  val lsu_mem = Module(new LsuMem()).io

  lsu_mem.info := io.memoryStage.data.info
  lsu_mem.src_info := io.memoryStage.data.src_info
  lsu_mem.rdata := io.rdata

  io.writeBackStage.data.pc := io.memoryStage.data.pc
  io.writeBackStage.data.info := io.memoryStage.data.info
  io.writeBackStage.data.rd_info := io.memoryStage.data.rd_info
  io.writeBackStage.data.has_exception := io.memoryStage.data.has_exception

  io.writeBackStage.data.rd_info.wdata(FuType.lsu) := lsu_mem.result
}