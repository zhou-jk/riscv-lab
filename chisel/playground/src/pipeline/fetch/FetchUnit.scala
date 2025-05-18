package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu.CpuConfig
import cpu.defines._

class FetchUnit extends Module with HasExceptionNO {
  val io = IO(new Bundle {
    val decodeStage = new FetchUnitDecodeUnit()
    val instSram    = new InstSram()
        
    val flush       = Input(Bool())
    val target      = Input(UInt(XLEN.W))
    val ctrl        = Input(new CtrlSignal())
  })

  val boot :: send :: receive :: Nil = Enum(3)
  val state                          = RegInit(boot)

  switch(state) {
    is(boot) {
      state := send
    }
    is(send) {
      state := receive
    }
    is(receive) {}
  }

  val pc = RegEnable(io.instSram.addr, (PC_INIT - 4.U), state =/= boot)

  val pc_next = Mux(io.flush, io.target, Mux(io.ctrl.allow_to_go, pc + 4.U, pc))  
  val pc_misaligned = pc_next(1, 0) =/= 0.U
  io.instSram.addr := pc_next

  io.decodeStage.data.valid := state === receive && !io.ctrl.do_flush
  io.decodeStage.data.pc    := pc
  io.decodeStage.data.inst := Mux(pc_misaligned, Instructions.NOP, io.instSram.rdata)

  io.instSram.en    := !reset.asBool && !pc_misaligned
  io.instSram.wen   := 0.U
  io.instSram.wdata := 0.U
}
