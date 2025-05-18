package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Bru extends Module with HasExceptionNO {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val pc       = Input(UInt(XLEN.W))
    val ex       = Input(new ExceptionInfo())
    val result   = Output(UInt(XLEN.W))
    val branch   = Output(Bool())
    val target   = Output(UInt(XLEN.W))
    val ex_out   = Output(new ExceptionInfo())
  })

  val op = io.info.op(3, 0)
  val src1 = io.src_info.src1_data
  val src2 = io.src_info.src2_data
  val pc = io.pc
  val imm = io.info.imm

  val eq  = src1 === src2
  val ne  = !eq
  val lt  = src1.asSInt < src2.asSInt
  val ge  = !lt
  val ltu = src1 < src2
  val geu = !ltu

  val take_branch = MuxLookup(op, false.B)(Seq(
    BRUOpType.beq  -> eq,
    BRUOpType.bne  -> ne,
    BRUOpType.blt  -> lt,
    BRUOpType.bge  -> ge,
    BRUOpType.bltu -> ltu,
    BRUOpType.bgeu -> geu
  ))

  val is_branch = BRUOpType.isBranch(op)
  val is_jump = BRUOpType.isJump(op)
  val is_jalr = op === BRUOpType.jalr

  val branch_target = pc + imm
  val jalr_target = (src1 + imm) & (~1.U(XLEN.W))
  
  val target_out = Mux(is_jalr, jalr_target, branch_target)
  
  val branch_out = (is_branch & take_branch) | is_jump
  
  val result_out = pc + 4.U

  val ex_out = Wire(new ExceptionInfo())
  ex_out := io.ex
  when(io.info.valid && branch_out && target_out(1, 0) =/= 0.U) {
    ex_out.exception(instAddrMisaligned) := true.B
    ex_out.tval(instAddrMisaligned) := target_out
  }

  io.result := result_out
  io.branch := io.info.valid && (io.info.fusel === FuType.bru) && branch_out
  io.target := target_out
  io.ex_out := ex_out
}