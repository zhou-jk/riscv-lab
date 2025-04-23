package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu.CpuConfig
import cpu.defines._

class FetchUnit extends Module {
  val io = IO(new Bundle {
    val decodeStage = new FetchUnitDecodeUnit()
    val instSram    = new InstSram()
    
    val branch      = Input(Bool())
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

  io.instSram.addr := Mux(io.branch, io.target, Mux(io.ctrl.allow_to_go, pc + 4.U, pc))

  io.decodeStage.data.valid := state === receive && !io.ctrl.do_flush
  io.decodeStage.data.pc    := pc
  io.decodeStage.data.inst  := io.instSram.rdata

  io.instSram.en    := !reset.asBool
  io.instSram.wen   := 0.U
  io.instSram.wdata := 0.U
}