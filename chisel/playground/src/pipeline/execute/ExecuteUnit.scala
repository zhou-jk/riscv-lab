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
    
    val branch = Output(Bool())
    val target = Output(UInt(XLEN.W))
    
    // 中断输入
    val interrupt = Input(new ExtInterrupt())
    
    // 异常和中断处理相关
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

  io.dataSram <> fu.dataSram
  
  io.branch := fu.branch
  io.target := fu.target
  
  // 输出特权模式和中断信号
  io.mode := fu.mode
  io.interrupt_out := fu.interrupt_out

  val mem_data = Wire(new ExeMemData())
  mem_data.pc := fu.data.pc
  
  // 创建Info的副本而不是直接修改
  val info_with_reg_wen = Wire(new Info())
  info_with_reg_wen := fu.data.info
  info_with_reg_wen.reg_wen := fu.reg_wen_out
  
  // 使用新的Info实例
  mem_data.info := info_with_reg_wen
  mem_data.rd_info := fu.data.rd_info
  
  val src_info_mem = Wire(new SrcInfo())
  src_info_mem.src1_data := fu.data.src_info.src1_data
  src_info_mem.src2_data := io.dataSram.rdata
  
  mem_data.src_info := src_info_mem
  
  // 更新异常信息
  mem_data.ex := fu.ex_out
  mem_data.has_exception := fu.has_exception
  
  io.memoryStage.data := mem_data
}