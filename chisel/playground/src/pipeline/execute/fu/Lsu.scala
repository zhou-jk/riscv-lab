package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Lsu extends Module with HasExceptionNO {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val ex       = Input(new ExceptionInfo())
    val result   = Output(UInt(XLEN.W))
    val dataSram = new DataSram()
    val ex_out   = Output(new ExceptionInfo())
  })

  val op = io.info.op(3, 0)

  val mem_addr = io.src_info.src1_data + io.info.imm
  
  val ex_out = WireInit(io.ex)
  val is_load = LSUOpType.isLoad(op)
  val is_store = LSUOpType.isStore(op)
  val addr_check = MuxLookup(op(1, 0), true.B)(Seq(
    "b00".U -> true.B,                         // lb/sb
    "b01".U -> (mem_addr(0) === 0.U),          // lh/sh
    "b10".U -> (mem_addr(1, 0) === 0.U),       // lw/sw
    "b11".U -> (mem_addr(2, 0) === 0.U)        // ld/sd
  ))
  
  val load_addr_misaligned = WireInit(false.B)
  val store_addr_misaligned = WireInit(false.B)
  val addr_misaligned_tval = WireInit(0.U(XLEN.W))
  
  when(io.info.valid && io.info.fusel === FuType.lsu) {
    when(is_load && !addr_check) {
      load_addr_misaligned := true.B
      addr_misaligned_tval := mem_addr
    }
    
    when(is_store && !addr_check) {
      store_addr_misaligned := true.B
      addr_misaligned_tval := mem_addr
    }
  }
  
  val exception_bits = ex_out.exception.asUInt
  val updated_exception_bits = exception_bits | 
    (load_addr_misaligned << loadAddrMisaligned.U) | 
    (store_addr_misaligned << storeAddrMisaligned.U)
  
  val final_ex_out = Wire(new ExceptionInfo())
  final_ex_out.exception := updated_exception_bits.asTypeOf(ex_out.exception)
  final_ex_out.interrupt := ex_out.interrupt
  
  for (i <- 0 until EXC_WID) {
    when(i.U === loadAddrMisaligned.U && load_addr_misaligned) {
      final_ex_out.tval(i) := addr_misaligned_tval
    }.elsewhen(i.U === storeAddrMisaligned.U && store_addr_misaligned) {
      final_ex_out.tval(i) := addr_misaligned_tval
    }.otherwise {
      final_ex_out.tval(i) := ex_out.tval(i)
    }
  }
  
  val do_access = addr_check && 
    !(final_ex_out.exception.asUInt.orR || final_ex_out.interrupt.asUInt.orR)
  
  io.dataSram.en := do_access && io.info.valid && (io.info.fusel === FuType.lsu)
  io.dataSram.addr := mem_addr(SRAM_ADDR_WID-1, 0)
  
  val mem_wdata = Wire(UInt(XLEN.W))
  val src2_data = io.src_info.src2_data
  
  mem_wdata := MuxLookup(op(1, 0), 0.U)(Seq(
    "b00".U -> Fill(8, src2_data(7, 0)),    // sb
    "b01".U -> Fill(4, src2_data(15, 0)),   // sh
    "b10".U -> Fill(2, src2_data(31, 0)),   // sw
    "b11".U -> src2_data                    // sd
  ))
  
  io.dataSram.wdata := mem_wdata
  
  val mem_wen_temp = Wire(UInt(8.W))
  val mem_addr_low = mem_addr(2, 0)
  
  val access_width = Wire(UInt(8.W))
  access_width := MuxLookup(op(1, 0), 0.U)(Seq(
    "b00".U -> "b00000001".U,
    "b01".U -> "b00000011".U,
    "b10".U -> "b00001111".U,
    "b11".U -> "b11111111".U
  ))
  
  mem_wen_temp := access_width << mem_addr_low
  
  io.dataSram.wen := Mux(io.info.valid && (io.info.fusel === FuType.lsu) && is_store && do_access, mem_wen_temp, 0.U)
  
  io.result := 0.U
  io.ex_out := final_ex_out
}