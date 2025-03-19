package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class WriteBackUnit extends Module {
  val io = IO(new Bundle {
    val writeBackStage = Input(new MemoryUnitWriteBackUnit())
    val regfile        = Output(new RegWrite())
    val debug          = new DEBUG()
  })

  // 写回阶段完成数据的写回操作
  // 同时该阶段还负责差分测试的比对工作
  // TODO: 完成WriteBackUnit模块的逻辑
}
