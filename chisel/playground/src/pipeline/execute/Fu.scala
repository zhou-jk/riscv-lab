package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class Fu extends Module with HasExceptionNO {
  val io = IO(new Bundle {
    val data = new Bundle {
      val pc       = Input(UInt(XLEN.W))
      val info     = Input(new Info())
      val src_info = Input(new SrcInfo())
      val ex       = Input(new ExceptionInfo())
      val rd_info  = Output(new RdInfo())
    }

    val dataSram = new DataSram()
    
    val flush = Output(Bool())
    val target = Output(UInt(XLEN.W))
    
    val has_exception = Output(Bool())
    val interrupt     = Input(new ExtInterrupt())
    
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

  lsu.ex  := io.data.ex
  bru.ex  := io.data.ex
  
  alu.info     := io.data.info
  alu.src_info := io.data.src_info
  mdu.info     := io.data.info
  mdu.src_info := io.data.src_info
  lsu.info     := io.data.info
  lsu.src_info := io.data.src_info
  bru.info     := io.data.info
  bru.src_info := io.data.src_info
  bru.pc       := io.data.pc
  
  val fu_ex_out = Wire(new ExceptionInfo())
  
  fu_ex_out := io.data.ex
  
  when(io.data.info.valid) {
    when(io.data.info.fusel === FuType.bru && bru.ex_out.exception(instAddrMisaligned)) {
      fu_ex_out.exception(instAddrMisaligned) := true.B
      fu_ex_out.tval(instAddrMisaligned) := bru.ex_out.tval(instAddrMisaligned)
    }
    
    when(io.data.info.fusel === FuType.lsu) {
      when(lsu.ex_out.exception(loadAddrMisaligned)) {
        fu_ex_out.exception(loadAddrMisaligned) := true.B
        fu_ex_out.tval(loadAddrMisaligned) := lsu.ex_out.tval(loadAddrMisaligned)
      }
      when(lsu.ex_out.exception(storeAddrMisaligned)) {
        fu_ex_out.exception(storeAddrMisaligned) := true.B
        fu_ex_out.tval(storeAddrMisaligned) := lsu.ex_out.tval(storeAddrMisaligned)
      }
    }
  }
  
  csr.info     := io.data.info
  csr.src_info := io.data.src_info
  csr.pc       := io.data.pc
  csr.ex       := fu_ex_out
  csr.interrupt := io.interrupt
  
  val flush = bru.branch || csr.flush
  val target = Mux(csr.flush, csr.target, bru.target)
  
  io.flush     := flush
  io.target    := target
  io.has_exception := csr.has_exception
  
  io.mode := csr.mode
  io.interrupt_out := csr.interrupt_out
  
  io.ex_out := csr.ex_out
  
  val reg_wen = Mux(io.data.info.fusel === FuType.csr, csr.reg_wen_out, io.data.info.reg_wen) && !csr.has_exception

  io.data.rd_info.wdata(FuType.alu) := alu.result
  io.data.rd_info.wdata(FuType.mdu) := mdu.result
  io.data.rd_info.wdata(FuType.lsu) := lsu.result
  io.data.rd_info.wdata(FuType.bru) := bru.result
  io.data.rd_info.wdata(FuType.csr) := csr.result
  
  io.reg_wen_out := reg_wen
}