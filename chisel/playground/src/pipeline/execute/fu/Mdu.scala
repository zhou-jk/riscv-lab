package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class Multiplier extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })

  val op     = io.info.op(3, 0)
  val src1   = io.src_info.src1_data
  val src2   = io.src_info.src2_data

  val src1_sext = Wire(SInt((XLEN + 1).W))
  val src2_sext = Wire(SInt((XLEN + 1).W))
  val src1_zext = Wire(UInt((XLEN + 1).W))
  val src2_zext = Wire(UInt((XLEN + 1).W))

  src1_sext := Cat(src1(XLEN - 1), src1).asSInt
  src2_sext := Cat(src2(XLEN - 1), src2).asSInt
  src1_zext := Cat(0.U(1.W), src1)
  src2_zext := Cat(0.U(1.W), src2)

  val mul_ss_130 = (src1_sext * src2_sext).asUInt
  def mulSU(a: SInt, b: UInt): UInt = {
    val a_is_neg = a(a.getWidth - 1)
    val abs_a = Mux(a_is_neg, -a, a).asUInt
    val product = abs_a * b
    Mux(a_is_neg, (~product + 1.U), product)
  }

  val mul_su_130 = mulSU(src1_sext, src2_zext)
  val mul_uu_130 = (src1_zext * src2_zext)

  val mul_ss_h = mul_ss_130(XLEN * 2 - 1, XLEN)
  val mul_su_h = mul_su_130(XLEN * 2 - 1, XLEN)
  val mul_uu_h = mul_uu_130(XLEN * 2 - 1, XLEN)
  val mul_l    = mul_ss_130(XLEN - 1, 0)

  val src1_32 = src1(31, 0)
  val src2_32 = src2(31, 0)

  val src1_33_sext = Wire(SInt(33.W))
  val src2_33_sext = Wire(SInt(33.W))
  src1_33_sext := Cat(src1_32(31), src1_32).asSInt
  src2_33_sext := Cat(src2_32(31), src2_32).asSInt

  val mul_w_66 = (src1_33_sext * src2_33_sext).asUInt

  val mulw_result = Cat(Fill(XLEN - 32, mul_w_66(31)), mul_w_66(31, 0))

  val mul_result = MuxLookup(op, mul_l)(Seq(
    MDUOpType.mul    -> mul_l,
    MDUOpType.mulh   -> mul_ss_h,
    MDUOpType.mulhsu -> mul_su_h,
    MDUOpType.mulhu  -> mul_uu_h,
    MDUOpType.mulw   -> mulw_result
  ))

  io.result := mul_result
}

class Divider extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })

  val op     = io.info.op(3, 0)
  val src1   = io.src_info.src1_data
  val src2   = io.src_info.src2_data

  val src1_s = src1.asSInt
  val src2_s = src2.asSInt
  val src1_u = src1
  val src2_u = src2

  val src1_32 = src1(31, 0)
  val src2_32 = src2(31, 0)
  val src1_32_s = src1_32.asSInt
  val src2_32_s = src2_32.asSInt
  val src1_32_u = src1_32
  val src2_32_u = src2_32

  val MIN_SINT64 = (BigInt(1) << (XLEN - 1)).S(XLEN.W)
  val MAX_UINT64 = Fill(XLEN, 1.B)
  val NEG_ONE64 = (-1).S(XLEN.W)

  val MIN_SINT32 = (BigInt(1) << 31).S(32.W)
  val MAX_UINT32 = Fill(32, 1.B)
  val NEG_ONE32 = (-1).S(32.W)

  val divisor_is_zero64 = (src2_u === 0.U)
  val divisor_is_zero32 = (src2_32_u === 0.U)
  val overflow64 = (src1_s === MIN_SINT64) && (src2_s === NEG_ONE64)
  val overflow32 = (src1_32_s === MIN_SINT32) && (src2_32_s === NEG_ONE32)

  val quotient64_s = Wire(SInt(XLEN.W))
  val quotient32_s = Wire(SInt(32.W))
  quotient64_s := Mux(divisor_is_zero64, NEG_ONE64, Mux(overflow64, MIN_SINT64, src1_s / src2_s))
  quotient32_s := Mux(divisor_is_zero32, NEG_ONE32, Mux(overflow32, MIN_SINT32, src1_32_s / src2_32_s)) 

  val remainder64_s = Wire(SInt(XLEN.W))
  val remainder32_s = Wire(SInt(32.W))
  remainder64_s := Mux(divisor_is_zero64, src1_s, Mux(overflow64, 0.S(XLEN.W), src1_s - quotient64_s * src2_s))
  remainder32_s := Mux(divisor_is_zero32, src1_32_s, Mux(overflow32, 0.S(32.W), src1_32_s - quotient32_s * src2_32_s))

  val rem64 = remainder64_s.asUInt
  val rem32 = remainder32_s.asUInt

  val divu64  = Mux(divisor_is_zero64, MAX_UINT64, src1_u / src2_u)
  val remu64  = Mux(divisor_is_zero64, src1_u, src1_u % src2_u)
  val div64   = quotient64_s.asUInt

  val divu32  = Mux(divisor_is_zero32, MAX_UINT32, src1_32_u / src2_32_u)
  val remu32 = Mux(divisor_is_zero32, src1_32_u, src1_32_u % src2_32_u)
  val div32   = quotient32_s.asUInt

  val divw_result  = Cat(Fill(XLEN - 32, div32(31)), div32)
  val remw_result  = Cat(Fill(XLEN - 32, rem32(31)), rem32)
  val divuw_result = Cat(Fill(XLEN - 32, divu32(31)), divu32)
  val remuw_result = Cat(Fill(XLEN - 32, remu32(31)), remu32)

  val div_rem_result = MuxLookup(op, 0.U(XLEN.W))(Seq(
    MDUOpType.div   -> div64,
    MDUOpType.divu  -> divu64,
    MDUOpType.rem   -> rem64,
    MDUOpType.remu  -> remu64,
    MDUOpType.divw  -> divw_result,
    MDUOpType.divuw -> divuw_result,
    MDUOpType.remw  -> remw_result,
    MDUOpType.remuw -> remuw_result
  ))

  io.result := div_rem_result
}

class Mdu extends Module {
  val io = IO(new Bundle {
    val info     = Input(new Info())
    val src_info = Input(new SrcInfo())
    val result   = Output(UInt(XLEN.W))
  })

  val multiplier = Module(new Multiplier()).io
  val divider    = Module(new Divider()).io

  multiplier.info     := io.info
  multiplier.src_info := io.src_info
  divider.info        := io.info
  divider.src_info    := io.src_info

  val mdu_op = io.info.op(3, 0)

  val is_mul_operation = MDUOpType.isMul(mdu_op)

  io.result := Mux(is_mul_operation, multiplier.result, divider.result)
  val mdu_chosen_result = Mux(is_mul_operation, multiplier.result, divider.result)
}