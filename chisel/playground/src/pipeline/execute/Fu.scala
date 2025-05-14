// src/pipeline/execute/Fu.scala 修改Fu模块
package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class Fu extends Module {
  val io = IO(new Bundle {
    val data = new Bundle {
      val pc       = Input(UInt(XLEN.W))
      val info     = Input(new Info())
      val src_info = Input(new SrcInfo())
      val ex       = Input(new ExceptionInfo())
      val rd_info  = Output(new RdInfo())
    }

    val dataSram = new DataSram()
    
    val branch = Output(Bool())
    val target = Output(UInt(XLEN.W))
    
    // 异常处理相关
    val has_exception = Output(Bool())
    val interrupt     = Input(new ExtInterrupt())
    
    // CSR特权模式和中断信号
    val mode = Output(UInt(2.W))
    val interrupt_out = Output(Vec(INT_WID, Bool()))
    val ex_out = Output(new ExceptionInfo())
    val reg_wen_out = Output(Bool())
  })

  val alu = Module(new Alu()).io
  val mdu = Module(new Mdu()).io
  val lsu = Module(new Lsu()).io
  val bru = Module(new Bru()).io
  val csr = Module(new Csr()).io

  io.dataSram <> lsu.dataSram

  // 传递异常信息
  lsu.ex  := io.data.ex
  bru.ex  := io.data.ex
  csr.ex  := io.data.ex
  csr.pc  := io.data.pc
  csr.interrupt := io.interrupt

  // 功能单元输入
  alu.info     := io.data.info
  alu.src_info := io.data.src_info
  mdu.info     := io.data.info
  mdu.src_info := io.data.src_info
  lsu.info     := io.data.info
  lsu.src_info := io.data.src_info
  bru.info     := io.data.info
  bru.src_info := io.data.src_info
  bru.pc       := io.data.pc
  csr.info     := io.data.info
  csr.src_info := io.data.src_info
  
  // 分支和跳转信号
  val branch = bru.branch || csr.flush
  val target = Mux(csr.flush, csr.target, bru.target)
  
  io.branch    := branch
  io.target    := target
  io.has_exception := csr.has_exception
  
  // 暴露CSR信号
  io.mode := csr.mode
  io.interrupt_out := csr.interrupt_out
  
  // 合并异常信息
  val ex_out = Wire(new ExceptionInfo())
  ex_out := csr.ex_out
  
  // 如果LSU或BRU检测到异常，合并到CSR的异常
  when(io.data.info.fusel === FuType.lsu) {
    ex_out := lsu.ex_out
  }.elsewhen(io.data.info.fusel === FuType.bru) {
    ex_out := bru.ex_out
  }
  
  io.ex_out := ex_out

  // 处理异常时禁止寄存器写使能
  val reg_wen = Mux(io.data.info.fusel === FuType.csr, csr.reg_wen_out, io.data.info.reg_wen)

  // 写回数据
  io.data.rd_info.wdata(FuType.alu) := alu.result
  io.data.rd_info.wdata(FuType.mdu) := mdu.result
  io.data.rd_info.wdata(FuType.lsu) := lsu.result
  io.data.rd_info.wdata(FuType.bru) := bru.result
  io.data.rd_info.wdata(FuType.csr) := csr.result
  
  // 更新info中的reg_wen信号
  io.reg_wen_out := reg_wen
  
}